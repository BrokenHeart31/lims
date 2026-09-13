package com.lims.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目标准库-检测单项（api-spec 第 12 章，T-106）。
 *
 * <p>字段与 `sample_item` 的快照下沉字段一一对应（见 DECISIONS 2026-09-11）：
 * 维护页编辑的就是「将来会被复制进 sample_item 的那几个值」，两边字段名保持一致，
 * 便于人工核对「标准库 → 分解结果」的映射是否走样。</p>
 */
@Data
public class ProductLibItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productLibId;
    private Integer itemOrder;
    private String itemName;
    private String unit;
    private String basisCode;
    private String methods;
    private String stdValue;
    private Integer judgeType;

    /** 判定类型中文标签：限量比较 / 不得检出或不得使用 / 文本感官人工 */
    private String judgeTypeLabel;

    private Integer isReference;
    private String lowerLimit;
    private String methodNote;
}
