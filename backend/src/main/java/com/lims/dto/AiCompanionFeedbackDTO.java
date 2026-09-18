package com.lims.dto;

import lombok.Data;

/**
 * 建议条留痕请求（feature 增量 ai_flow_assistant，T02，设计 §4.3）。
 *
 * <p>前端点开建议条（或关闭）时回传，写 {@code ai_hint_log}（AI 域唯一写入，属留痕非业务写）。</p>
 */
@Data
public class AiCompanionFeedbackDTO {

    /** 去重键（sampleNo|itemName|stdNo） */
    private String hintKey;

    private String sampleNo;

    private String itemName;

    private String stdNo;

    /** 来源页面标识 */
    private String pageKey;

    /** 是否被点开 true=点开（翻桌） / false=关闭（静默） */
    private Boolean clicked;
}
