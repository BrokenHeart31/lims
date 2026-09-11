package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品标准库明细（product_lib_item，T-401 套库来源之二）。
 *
 * <p>每行是某产品的一个应检项目，含判定依据、检验方法、限量值、判定类型等。
 * T-401 套库时按 {@code item_order} 升序取出，并把下列字段**快照复制**进 {@code sample_item}。</p>
 *
 * <p>`std_value` 白名单 5 形态与 `judge_type` 语义见
 * docs/knowledge/2026-09-11-judge-engine-whitelist.md（Copilot 裁决定稿）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_lib_item")
public class ProductLibItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属产品库ID（product_lib.id） */
    private Long productLibId;

    /** 项次（产品内排序，从 1 起） */
    private Integer itemOrder;

    /** 检验项目/检测单项名称 */
    private String itemName;

    private String unit;

    /** 判定依据标准号 */
    private String basisCode;

    /** 检验方法（多个以 # 分隔） */
    private String methods;

    /** 标准值（限量值文本） */
    private String stdValue;

    /** 判定类型：1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
    private Integer judgeType;

    /** 是否参考性限量：0=否 1=是 */
    private Integer isReference;

    /** 最低检出限 */
    private String lowerLimit;

    /** 方法备注 */
    private String methodNote;
}
