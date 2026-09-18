package com.lims.common;

import lombok.Getter;

/**
 * 统一响应业务码（AGENTS.md 4.1）
 *
 * <p>0 成功；400 参数错误；401 未认证；403 无权限；500 系统异常。
 * 业务自定义错误码建议从 1000 起按模块分段，在 api-spec.md 登记。</p>
 *
 * <p><b>2026-09-17 增量登记</b>：回退域 4100–4199、AI 域 4200–4299
 * （api-spec 0.2 已登记；拒答不算错误——退避与离线走 HTTP 200 + body.code）。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    ERROR(500, "系统异常，请稍后重试"),

    // =====================================================================
    // 回退域 4100–4199（feature B，T02）
    // =====================================================================
    /** 边不在 ROLLBACK 白名单 / 跨级（动态消息由 RollbackEdgePolicy 补全 from→to） */
    ROLLBACK_ILLEGAL(4101, "样品状态不允许回退（回退仅支持逐级）"),
    /** S80→S70：已签发不支持普通回退 */
    ROLLBACK_SIGNED(4102, "已签发样品不支持普通回退；报告已对外生效，请使用「作废 / 召回」"),
    /** S90→S80：已出报告不支持回退 */
    ROLLBACK_REPORTED(4103, "已出报告不支持回退；数据已上报省平台，只能新增更正 / 作废记录"),
    /** 敏感边权限不足 */
    ROLLBACK_SENSITIVE_FORBIDDEN(4104, "敏感回退需要「业务管理员 / 系统管理员」权限"),
    /** 敏感边未二次确认 */
    ROLLBACK_SECOND_CONFIRM_REQUIRED(4105, "敏感回退需二次确认"),
    /** 回退原因缺失 */
    ROLLBACK_REASON_REQUIRED(4106, "回退原因不能为空"),
    /** 该回退不可再撤销 */
    ROLLBACK_NOT_RECOVERABLE(4107, "该回退已产生新的下游数据，无法原路恢复；请重新前进"),
    /** 并发状态已变更 */
    ROLLBACK_CONFLICT(4108, "样品状态已变更，请刷新后重试"),

    // =====================================================================
    // AI 域 4200–4299（feature A，T03；T01 先登记常量）
    // =====================================================================
    /** 本地模型服务未启动/不可达（HTTP 200，前端静默降级） */
    AI_OFFLINE(4201, "AI 助手暂不可用（本地模型服务未启动），业务功能不受影响。"),
    /** 模型未就绪 */
    AI_MODEL_MISSING(4202, "本地模型未就绪，请先运行 ai/scripts/deploy-ollama.ps1 拉取模型"),
    /** 推理超时 */
    AI_TIMEOUT(4203, "本地模型响应超时，请缩短问题后重试"),
    /** OCR 任务不存在（feature 增量 ai_flow_assistant） */
    AI_OCR_JOB_NOT_FOUND(4204, "未找到该扫描件任务（请先在 ai/standards/inbox 投放 PDF 并运行预处理）"),
    /** OCR 任务进行中，不可复位（feature 增量 ai_flow_assistant） */
    AI_OCR_JOB_BUSY(4205, "该 OCR 任务正在进行中，请待其结束后再重试失败页"),
    /** 上传**文本版** PDF（有文字层）——需先经 prepare-standards.py 转文本（无需 OCR） */
    AI_PDF_NOT_SUPPORTED(4211, "这是文本版 PDF（有文字层），请先转文本：ai/scripts/prepare-standards.py（无需 OCR）"),
    /** 上传**扫描版** PDF（无文字层）——需先跑 OCR 通道（feature 增量 ai_flow_assistant） */
    AI_PDF_SCANNED(4212, "这是扫描版 PDF（无文字层），请先跑 OCR 通道：ai/scripts/prepare-standards.py --ocr（约 8~10 秒/页）");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
