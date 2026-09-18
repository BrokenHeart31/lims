package com.lims.config;

import com.lims.entity.SysOperationLog;
import com.lims.mapper.SysOperationLogMapper;
import com.lims.security.LoginUser;
import com.lims.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 操作日志拦截器（MVC 层审计，零业务侵入）。
 * =============================================================================
 * 设计要点
 * -----------------------------------------------------------------------------
 * <b>1. 为什么不是 AOP 切面？</b>
 * 本机离线 Maven 仓库没有 `spring-boot-starter-aop` / `aspectjweaver`，
 * 无法在不联网的前提下引入依赖。而 {@link HandlerInterceptor} 属于 spring-webmvc
 * （已在依赖内），能达成完全相同的「集中记录、不改业务代码」的目标。
 * 代价是记录粒度到「接口」而非「Service 方法」——对本系统（接口与业务动作近乎一一对应）
 * 这一粒度差异不构成信息损失。
 *
 * <b>2. 只记录写请求</b>
 * POST / PUT / DELETE 入表；GET 不入表。查询行为由 Web 容器访问日志承担，
 * 若把 GET 也写入，日志表会被翻页查询瞬间淹没，真正有价值的写操作反而被埋掉。
 *
 * <b>3. 绝不记录请求体</b>
 * 请求体可能包含密码（登录、改密、重置密码）。本拦截器只记录「方法 + 路径 + 结果 + 耗时」，
 * 从设计上杜绝凭据落库。这条是硬约束，后续维护不得为「看得更详细」而添加 body 记录。
 *
 * <b>4. 失败也要留痕</b>
 * 校验失败（400）、越权（403）、服务异常（500）同样入库并标记 `result=0`。
 * 审计日志的价值恰恰在于记录「尝试」——只记录成功等于放弃了对异常行为的追溯。
 *
 * <b>5. 记录失败不得影响业务</b>
 * 落库整体 try/catch，仅打 WARN。审计写入失败绝不能让一次成功的业务操作对用户变成失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor {

    private final SysOperationLogMapper operationLogMapper;

    /** 只记录这些 HTTP 方法（写操作） */
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE");

    /**
     * 不记录的路径（去掉 context-path 后比较）。
     * 登录/刷新位于认证之前（此时尚无登录人，记录了也是匿名噪音）；
     * `/error` 是容器转发的中转路径，记录会产生重复条目。
     */
    private static final Set<String> SKIP_PATHS = Set.of("/auth/login", "/auth/refresh", "/error");

    /** 请求属性名：保存本次请求的审计上下文，供 afterCompletion 消费 */
    private static final String ATTR_CONTEXT = "lims.oplog.context";

    /**
     * 模块识别表（**有序**，先匹配到前缀的胜出）。
     * 顺序敏感：`/report/audit` 与 `/report/generate` 同属报告域但动作不同，
     * 更长的前缀必须排在更短的前面。
     */
    private static final Map<String, String> MODULE_PREFIXES = new LinkedHashMap<>();

    static {
        MODULE_PREFIXES.put("/sys/user", "用户管理");
        MODULE_PREFIXES.put("/sys/role", "角色管理");
        MODULE_PREFIXES.put("/sys/menu", "菜单管理");
        MODULE_PREFIXES.put("/sys/dept", "部门管理");
        MODULE_PREFIXES.put("/sys/log", "操作日志");
        MODULE_PREFIXES.put("/base/lib", "项目标准库");
        MODULE_PREFIXES.put("/base/tester-method", "方法资质");
        MODULE_PREFIXES.put("/report/audit", "报告审核");
        MODULE_PREFIXES.put("/report/sign", "报告签发");
        MODULE_PREFIXES.put("/report/generate", "报告生成");
        MODULE_PREFIXES.put("/report/void", "报告作废");
        MODULE_PREFIXES.put("/sample", "样品登记");
        MODULE_PREFIXES.put("/item", "项目分解");
        MODULE_PREFIXES.put("/assign", "任务安排");
        MODULE_PREFIXES.put("/result", "结果录入");
        MODULE_PREFIXES.put("/task", "监抽任务");
        MODULE_PREFIXES.put("/export", "数据导出");
        // 2026-09-17 增量（feature A/B）——沿用「更长的前缀必须排在更短的前面」：
        //   /ai/kb 必须排在 /ai 之前，否则知识库动作会被记成「AI 助手」。
        MODULE_PREFIXES.put("/rollback", "流程回溯");
        MODULE_PREFIXES.put("/ai/kb", "AI 知识库");
        MODULE_PREFIXES.put("/ai", "AI 助手");
        MODULE_PREFIXES.put("/auth", "认证");
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }
        String uri = stripContextPath(request);
        if (SKIP_PATHS.contains(uri)) {
            return true;
        }
        // 幂等：ASYNC 派发（SSE 完成时）会让本方法**再执行一次**。
        // 若此处覆盖上下文，会把「起始时间」重置为 ASYNC 派发时刻——耗时变成 0ms，
        // 真实耗时（如流式对话 1.6s）永久丢失；且 ANONYMOUS 阶段会把操作人写坏。
        // 故已有上下文时直接复用首次（REQUEST 派发）建立的这一份。
        if (request.getAttribute(ATTR_CONTEXT) != null) {
            return true;
        }
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("uri", uri);
        ctx.put("method", request.getMethod());
        ctx.put("start", System.currentTimeMillis());
        ctx.put("ip", resolveIp(request));
        // 在认证过滤器已执行完毕的 preHandle 阶段取上下文，最可靠
        SecurityUtils.getLoginUser().ifPresent(loginUser -> {
            ctx.put("operator", loginUser.getUsername());
            ctx.put("operatorName", loginUser.getNickname());
        });
        if (!ctx.containsKey("operator")) {
            ctx.put("operator", SecurityUtils.getUsername().orElse("anonymous"));
        }
        request.setAttribute(ATTR_CONTEXT, ctx);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        Object raw = request.getAttribute(ATTR_CONTEXT);
        if (!(raw instanceof Map<?, ?> ctx)) {
            return;
        }
        try {
            String uri = String.valueOf(ctx.get("uri"));
            String method = String.valueOf(ctx.get("method"));
            String module = resolveModule(uri);
            int status = response.getStatus();
            boolean success = status < 400 && ex == null;

            SysOperationLog entity = new SysOperationLog();
            entity.setModule(module);
            entity.setHttpMethod(method);
            entity.setUri(uri);
            entity.setSummary(module + " · " + resolveAction(method, uri) + " " + uri);
            entity.setOperator(String.valueOf(ctx.get("operator")));
            Object name = ctx.get("operatorName");
            entity.setOperatorName(name == null ? null : String.valueOf(name));
            Object ip = ctx.get("ip");
            entity.setIp(ip == null ? null : String.valueOf(ip));
            entity.setResult(success ? 1 : 0);
            entity.setStatusCode(status);
            entity.setDurationMs(System.currentTimeMillis() - ((Number) ctx.get("start")).longValue());
            // 显式写入审计字段：afterCompletion 阶段安全上下文可能已被清理，
            // 不依赖 MetaObjectHandler 的兜底（否则会落成 "system"）
            entity.setCreatedBy(entity.getOperator());
            entity.setUpdatedBy(entity.getOperator());
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计写入失败绝不能影响业务响应
            log.warn("[操作日志] 写入失败（不影响本次业务结果）：{}", e.getMessage());
        }
    }

    /** 去掉 context-path（/api），得到与 api-spec 一致的相对路径 */
    private String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    /** 路径 → 模块名；未识别的路径归入「其他」而不是硬造一个模块名 */
    static String resolveModule(String uri) {
        for (Map.Entry<String, String> entry : MODULE_PREFIXES.entrySet()) {
            if (uri.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "其他";
    }

    /**
     * 路径 + 方法 → 动作词。
     *
     * <p>这是**派生标签**，用于让日志列表可读（`结果录入 · 提交 /result/submit` 远好于
     * `结果录入 · POST /result/submit`）。匹配不到时退回 HTTP 方法的通用说法，
     * 不猜测语义。</p>
     */
    static String resolveAction(String method, String uri) {
        // 2026-09-17：/kb/import 必须排在 /import 之前，否则知识库导入会被记为通用「导入」
        if (uri.contains("/kb/import")) {
            return "导入标准";
        }
        if (uri.contains("/import")) {
            return "导入";
        }
        if (uri.contains("/confirm")) {
            return "确认";
        }
        if (uri.contains("/generate")) {
            return "生成";
        }
        if (uri.contains("/approve")) {
            return "审核通过";
        }
        if (uri.contains("/return")) {
            return "审核退回";
        }
        if (uri.contains("/sign")) {
            return "签发";
        }
        if (uri.contains("/submit")) {
            return "提交";
        }
        if (uri.contains("/judge")) {
            return "判定预览";
        }
        if (uri.contains("/reassign")) {
            return "人工改派";
        }
        if (uri.contains("/auto")) {
            return "自动分配";
        }
        // 2026-09-17 增量（feature B）：回退 / 恢复 / 作废
        //   /rollback/execute 与 /rollback/recover 分属两个不同动作，故分别匹配；
        //   /report/void 的作废动作亦在此登记（/void 不与既有任何关键词冲突）。
        if (uri.contains("/rollback/execute")) {
            return "回退";
        }
        if (uri.contains("/recover")) {
            return "恢复";
        }
        if (uri.contains("/void")) {
            return "作废";
        }
        // 2026-09-17 增量（feature A）：AI 对话
        //   /chat/stream 必须排在 /chat 之前，否则流式对话会被记成普通「对话」。
        if (uri.contains("/chat/stream")) {
            return "流式对话";
        }
        if (uri.contains("/chat")) {
            return "对话";
        }
        // 注意顺序：/change-password 必须排在 /password 之前，否则会被后者抢先匹配
        if (uri.contains("/change-password")) {
            return "修改密码";
        }
        if (uri.contains("/password")) {
            return "重置密码";
        }
        if (uri.contains("/login")) {
            return "登录";
        }
        if (uri.contains("/logout")) {
            return "退出登录";
        }
        return switch (method) {
            case "DELETE" -> "删除";
            case "PUT" -> "修改";
            default -> "新增";
        };
    }

    /** 客户端 IP：优先取反向代理首跳，回退 RemoteAddr */
    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
