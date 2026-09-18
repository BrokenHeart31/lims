package com.lims.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 操作日志拦截器的「路径 → 模块 / 动作」派生逻辑单元测试（T-918）。
 *
 * <p>为什么单独测这两个纯函数：这是本域**唯一有分支的判定逻辑**——
 * 模块识别表是**有序**的（先匹配到的前缀胜出），
 * 一旦有人调整顺序（例如把 `/report` 泛化前缀插到 `/report/audit` 之前），
 * 日志会把「报告审核」错记成「报告生成」，而这类错误在界面上极难被发现
 * （显示的是一个看起来完全合理的模块名）。用单测把顺序语义固化下来。</p>
 *
 * <p>写入链路本身（拦截器 → Mapper）为 I/O 代码，由运行期端到端实测覆盖
 * （见 HANDOFF：nj001 写操作后 `sys_operation_log` 出现对应行）。</p>
 */
class OperationLogInterceptorTest {

    @Test
    @DisplayName("模块识别：常规业务路径")
    void resolveModuleForBusinessPaths() {
        assertAll(
                () -> assertEquals("结果录入", OperationLogInterceptor.resolveModule("/result/submit")),
                () -> assertEquals("结果录入", OperationLogInterceptor.resolveModule("/result/save")),
                () -> assertEquals("样品登记", OperationLogInterceptor.resolveModule("/sample/import")),
                () -> assertEquals("项目分解", OperationLogInterceptor.resolveModule("/item/confirm")),
                () -> assertEquals("任务安排", OperationLogInterceptor.resolveModule("/assign/auto")),
                () -> assertEquals("监抽任务", OperationLogInterceptor.resolveModule("/task")),
                () -> assertEquals("数据导出", OperationLogInterceptor.resolveModule("/export/province")),
                () -> assertEquals("认证", OperationLogInterceptor.resolveModule("/auth/change-password"))
        );
    }

    @Test
    @DisplayName("模块识别：前缀顺序敏感（报告域三拆 + sys/base 子域）")
    void resolveModuleRespectsPrefixOrder() {
        assertAll(
                // 报告域必须按 audit / sign / generate 精确区分，不能被更短的前缀吞掉
                () -> assertEquals("报告审核", OperationLogInterceptor.resolveModule("/report/audit/approve")),
                () -> assertEquals("报告签发", OperationLogInterceptor.resolveModule("/report/sign")),
                () -> assertEquals("报告生成", OperationLogInterceptor.resolveModule("/report/generate")),
                // /base/tester-method 必须排在 /base 之前（否则会被「项目标准库」吞掉）
                () -> assertEquals("方法资质",
                        OperationLogInterceptor.resolveModule("/base/tester-method/import")),
                () -> assertEquals("项目标准库", OperationLogInterceptor.resolveModule("/base/lib/item")),
                // 系统管理四个子域互不串味
                () -> assertEquals("用户管理", OperationLogInterceptor.resolveModule("/sys/user/3/password")),
                () -> assertEquals("角色管理", OperationLogInterceptor.resolveModule("/sys/role")),
                () -> assertEquals("菜单管理", OperationLogInterceptor.resolveModule("/sys/menu")),
                () -> assertEquals("部门管理", OperationLogInterceptor.resolveModule("/sys/dept")),
                () -> assertEquals("操作日志", OperationLogInterceptor.resolveModule("/sys/log"))
        );
    }

    @Test
    @DisplayName("模块识别：未登记路径归入「其他」，不硬造模块名")
    void resolveModuleFallsBackToOther() {
        assertAll(
                () -> assertEquals("其他", OperationLogInterceptor.resolveModule("/whatever")),
                () -> assertEquals("其他", OperationLogInterceptor.resolveModule("/"))
        );
    }

    @Test
    @DisplayName("动作派生：业务关键词优先于 HTTP 方法")
    void resolveActionUsesKeywords() {
        assertAll(
                () -> assertEquals("导入", OperationLogInterceptor.resolveAction("POST", "/sample/import")),
                () -> assertEquals("确认", OperationLogInterceptor.resolveAction("POST", "/item/confirm")),
                () -> assertEquals("生成", OperationLogInterceptor.resolveAction("POST", "/report/generate")),
                () -> assertEquals("审核通过", OperationLogInterceptor.resolveAction("POST", "/report/audit/approve")),
                () -> assertEquals("审核退回", OperationLogInterceptor.resolveAction("POST", "/report/audit/return")),
                () -> assertEquals("签发", OperationLogInterceptor.resolveAction("POST", "/report/sign")),
                () -> assertEquals("提交", OperationLogInterceptor.resolveAction("POST", "/result/submit")),
                () -> assertEquals("判定预览", OperationLogInterceptor.resolveAction("POST", "/result/judge")),
                () -> assertEquals("人工改派", OperationLogInterceptor.resolveAction("POST", "/assign/reassign")),
                () -> assertEquals("自动分配", OperationLogInterceptor.resolveAction("POST", "/assign/auto")),
                () -> assertEquals("登录", OperationLogInterceptor.resolveAction("POST", "/auth/login")),
                () -> assertEquals("退出登录", OperationLogInterceptor.resolveAction("POST", "/auth/logout"))
        );
    }

    @Test
    @DisplayName("动作派生：/change-password 不得被 /password 抢先匹配")
    void resolveActionDistinguishesChangeAndResetPassword() {
        assertAll(
                () -> assertEquals("修改密码",
                        OperationLogInterceptor.resolveAction("POST", "/auth/change-password")),
                () -> assertEquals("重置密码",
                        OperationLogInterceptor.resolveAction("PUT", "/sys/user/3/password"))
        );
    }

    @Test
    @DisplayName("动作派生：无关键词时退回 HTTP 方法通用说法")
    void resolveActionFallsBackToHttpMethod() {
        assertAll(
                () -> assertEquals("删除", OperationLogInterceptor.resolveAction("DELETE", "/task/1")),
                () -> assertEquals("修改", OperationLogInterceptor.resolveAction("PUT", "/task")),
                () -> assertEquals("新增", OperationLogInterceptor.resolveAction("POST", "/task"))
        );
    }

    // =========================================================================
    // 2026-09-17 增量（feature A/B）：流程回溯 / AI / 报告作废
    // =========================================================================

    @Test
    @DisplayName("模块识别：增量路径（回溯 / AI / 报告作废）；/ai/kb 先于 /ai（顺序敏感）")
    void resolveModuleForRollbackAndAiPaths() {
        assertAll(
                () -> assertEquals("流程回溯", OperationLogInterceptor.resolveModule("/rollback/execute")),
                () -> assertEquals("流程回溯", OperationLogInterceptor.resolveModule("/rollback/recover")),
                () -> assertEquals("流程回溯", OperationLogInterceptor.resolveModule("/rollback/history")),
                () -> assertEquals("报告作废", OperationLogInterceptor.resolveModule("/report/void")),
                // /ai/kb 必须排在 /ai 之前，否则知识库动作会被记成「AI 助手」
                () -> assertEquals("AI 知识库", OperationLogInterceptor.resolveModule("/ai/kb/import/scan")),
                () -> assertEquals("AI 助手", OperationLogInterceptor.resolveModule("/ai/chat")),
                () -> assertEquals("AI 助手", OperationLogInterceptor.resolveModule("/ai/chat/stream")),
                // 既有报告域三拆不被新前缀影响（回归护栏）
                () -> assertEquals("报告审核", OperationLogInterceptor.resolveModule("/report/audit/approve")),
                () -> assertEquals("报告生成", OperationLogInterceptor.resolveModule("/report/generate"))
        );
    }

    @Test
    @DisplayName("动作派生：回退 / 恢复 / 作废；/kb/import 优先于 /import（顺序回归护栏）")
    void resolveActionForRollbackAndAi() {
        assertAll(
                () -> assertEquals("回退", OperationLogInterceptor.resolveAction("POST", "/rollback/execute")),
                () -> assertEquals("恢复", OperationLogInterceptor.resolveAction("POST", "/rollback/recover")),
                () -> assertEquals("作废", OperationLogInterceptor.resolveAction("POST", "/report/void")),
                () -> assertEquals("导入标准", OperationLogInterceptor.resolveAction("POST", "/ai/kb/import/scan")),
                // 既有 /sample/import 仍是通用「导入」（证明新关键词未误伤旧路径）
                () -> assertEquals("导入", OperationLogInterceptor.resolveAction("POST", "/sample/import"))
        );
    }
}
