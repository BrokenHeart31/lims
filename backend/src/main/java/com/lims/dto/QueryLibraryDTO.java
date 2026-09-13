package com.lims.dto;

import lombok.Data;

/**
 * 项目库（产品标准库）查询条件（T-801 A3）。
 *
 * <p>对应 {@code product_lib} 的分页检索：产品名称模糊、食品大类精确。分页参数由 Controller 单独接收。</p>
 */
@Data
public class QueryLibraryDTO {

    /** 产品名称（模糊匹配） */
    private String productName;

    /** 食品大类（精确匹配，对应 product_lib.category） */
    private String category;
}
