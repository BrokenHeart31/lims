package com.lims.vo;

import lombok.Data;

/**
 * 实时判定预览结果（api-spec 6.3，T-601）。
 *
 * <p>返回引擎对「单个检测单项 + 一个检验值」的判定输出，含依据说明，供录入页实时展示。</p>
 */
@Data
public class ResultJudgeVO {

    private Long itemId;

    private String itemName;

    private String unit;

    /** 判定依据参数回显（来自 sample_item 快照，便于前端展示「限量值 / 检出限」） */
    private String stdValue;

    private String lowerLimit;

    private Integer judgeType;

    /** 回显的检验值（原样，便于前端校对） */
    private String testValue;

    /** 结论 code：1=合格 2=不合格 3=待判定 */
    private Integer conclusion;

    private String conclusionLabel;

    /** 结论来源 code：1=自动判定 2=人工判定 */
    private Integer conclusionSource;

    private String conclusionSourceLabel;

    /** 判定依据说明 */
    private String judgeBasis;
}
