package com.lims.vo;

import lombok.Data;

/**
 * 项目库（产品）查询行（T-801 A3，GET /api/query/library/page）。
 *
 * <p>对应 {@code product_lib} 一行一个产品；{@code itemCount} 为该产品下 {@code product_lib_item} 的条数。</p>
 */
@Data
public class LibraryQueryVO {

    /** product_lib.id（后续查询该产品检测单项列表用） */
    private Long id;

    private String productName;

    /** 食品大类 */
    private String category;

    /** 检测单项数（该产品 product_lib_item 有效行数） */
    private Integer itemCount;
}
