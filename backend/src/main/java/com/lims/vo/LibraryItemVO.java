package com.lims.vo;

import lombok.Data;

/**
 * 项目库检测单项（T-801 A3，GET /api/query/library/{productLibId}/items）。
 *
 * <p>取自 {@code product_lib_item}，按 {@code item_order} 升序；{@code judgeTypeLabel} 由
 * {@code ResultEntryPolicy.judgeTypeLabel} 统一取值（与结果录入页同一来源，禁止前端另维护字典）。</p>
 */
@Data
public class LibraryItemVO {

    /** product_lib_item.id */
    private Long id;

    private Integer itemOrder;

    private String itemName;

    /** 判定依据标准号 */
    private String basisCode;

    /** 检验方法（多个以 # 分隔） */
    private String methods;

    /** 标准值（限量值文本） */
    private String stdValue;

    /** 判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
    private Integer judgeType;

    /** 判定类型中文名 */
    private String judgeTypeLabel;

    /** 是否参考性限量 0=否 1=是 */
    private Integer isReference;

    /** 最低检出限 */
    private String lowerLimit;

    private String unit;
}
