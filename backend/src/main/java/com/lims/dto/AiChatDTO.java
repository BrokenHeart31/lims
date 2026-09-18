package com.lims.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求（feature A，T03 / api-spec A2、A3）。
 *
 * <p>{@code conversationId} 为空表示**新会话**（后端建 {@code ai_conversation} 并回填）；
 * 非空表示续聊（后端按 {@code lims.ai.history-limit} 取最近若干条作上下文）。</p>
 */
@Data
public class AiChatDTO {

    /** 会话ID；null=新会话 */
    private Long conversationId;

    /** 用户问题（必填） */
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题过长（上限 2000 字）")
    private String question;

    /** 可选业务上下文 */
    @Valid
    private AiChatContextDTO context;
}
