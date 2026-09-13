package com.lims.vo;

import lombok.Data;

/**
 * 检验报告「检验结果」明细行（第 2 页 7 列表，T-702）。
 *
 * <p>字段直接对应业务说明书的 7 列：检验项目 / 检验数据 / 检测依据 / 标准值 /
 * 单位 / 最低检出限 / 单项结论。数据源为 {@code sample_item}（快照）与
 * {@code sample_result}（结果），按 {@code item_order} 升序排列。</p>
 */
@Data
public class ReportItemVO {

    /** 项次（sample_item.item_order） */
    private Integer itemOrder;

    /** 检验项目名称；参考性限量项（is_reference=1）前缀 "*" */
    private String itemName;

    /** 检验数据（sample_result.test_value）；无结果行时为 null */
    private String testValue;

    /** 检测依据标准号 */
    private String basisCode;

    /** 标准值（限量值文本） */
    private String stdValue;

    /** 单位 */
    private String unit;

    /** 最低检出限 */
    private String lowerLimit;

    /** 单项结论中文（取自 ResultConclusion.getLabel()）；无结论时为 null */
    private String conclusionText;

    /** 单项结论码：1=合格 2=不合格 3=待判定；无结论时为 null */
    private Integer conclusionCode;

    /** 是否参考性限量项：0=否 1=是 */
    private Integer isReference;
}
