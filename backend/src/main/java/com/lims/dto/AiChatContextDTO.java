package com.lims.dto;

import lombok.Data;

/**
 * AI 对话上下文（可选，feature A，T03 / api-spec A2）。
 *
 * <p>前端悬浮窗在业务页打开时可能带上「当前样品 / 当前标准」上下文，让「解释这个状态」
 * 「这个样品下一步」等问题无需用户手打样品号。全部字段可选。</p>
 */
@Data
public class AiChatContextDTO {

    /** 当前样品编号（用于业务域「解释状态 / 下一步」） */
    private String sampleNo;

    /** 当前样品状态 code（前端已知时直传，避免再查库） */
    private Integer status;

    /** 当前标准号（限定检索范围） */
    private String stdNo;

    /** 当前正在看的检测项目名（feature 增量：**标准伴随查询的触发条件**，为空则不主动提示） */
    private String itemName;

    /** 当前项目的判定依据标准号（feature 增量：优先用于解析伴随查询的标准号） */
    private String basisCode;

    /** 来源页面标识（result-entry / item-decompose / assign-index / report-audit / sample-register） */
    private String pageKey;

    /** 当前角色编码（R1/R2/R3/R100，供引导文案与复盘） */
    private String roleCode;
}
