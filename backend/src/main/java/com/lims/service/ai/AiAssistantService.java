package com.lims.service.ai;

import com.lims.common.PageResult;
import com.lims.dto.AiChatDTO;
import com.lims.vo.AiAnswerVO;
import com.lims.vo.AiConversationVO;
import com.lims.vo.AiMessageVO;
import com.lims.vo.AiStatusVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 助手编排服务（feature A，T03 / api-spec A1–A5）。
 *
 * <p><b>AI 是旁路</b>：本服务只写 {@code ai_*} / {@code gb_*} 表，业务只读统一走
 * {@link BusinessContextReader}；Ollama 不可用时返回明确降级态（4201），业务接口零影响。</p>
 */
public interface AiAssistantService {

    /** A1 健康检查（fail-soft，离线也 code=0）。 */
    AiStatusVO status();

    /** A2 非流式问答（自检/降级/单测）。 */
    AiAnswerVO chat(AiChatDTO dto);

    /** A3 流式问答（SSE 主通道）。 */
    SseEmitter chatStream(AiChatDTO dto);

    /** A4 分页查询会话（审计）。 */
    PageResult<AiConversationVO> pageConversations(long current, long size);

    /** A5 会话消息明细（含引用/拒答留痕）。 */
    List<AiMessageVO> messages(Long conversationId);
}
