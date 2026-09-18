package com.lims.service.ai.companion;

import com.lims.dto.AiChatContextDTO;
import com.lims.dto.AiCompanionFeedbackDTO;
import com.lims.vo.AiCompanionHintVO;

import java.util.List;

/**
 * 标准伴随查询服务（feature 增量 ai_flow_assistant，T02，设计 §2.5 / §5.1）。
 *
 * <p><b>只读</b>：不写任何业务表、不写结论字段、不调 {@code JudgeEngine}；
 * 唯一写入是 {@code ai_hint_log} 留痕（非业务数据）。</p>
 */
public interface StandardCompanionService {

    /**
     * 依上下文给出建议条（无触发 / 无命中返回空列表，**不是错误**，fail-soft）。
     *
     * @param ctx   对话上下文（{@code itemName} 是触发的必要条件）
     * @param perms 当前用户权限标识清单（用于数值卡片跳转权限收敛）
     */
    List<AiCompanionHintVO> hints(AiChatContextDTO ctx, List<String> perms);

    /**
     * 建议条留痕（是否被点开）→ 写 {@code ai_hint_log}。
     *
     * @return 恒定 true（留痕成功/幂等）
     */
    boolean feedback(AiCompanionFeedbackDTO dto);
}
