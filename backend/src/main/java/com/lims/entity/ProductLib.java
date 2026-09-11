package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品标准库（product_lib，T-401 套库来源之一）。
 *
 * <p>每行代表一个受检产品（如「蚕豆」「大米」），`product_code` 与旧系统 `product.id` 一致；
 * `product_name` / `category` 由 V2 迁移脚本从旧 `product` 表（`libName` / `prd_category`）补齐（T-903）。</p>
 *
 * <p>套库匹配键：{@code sample_info.sample_name} = {@code product_lib.product_name}（精确匹配）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_lib")
public class ProductLib extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品编码（与旧系统 product.id 一致，如 nagw001） */
    private String productCode;

    /** 产品名称（如 蚕豆）；T-903 补齐 */
    private String productName;

    /** 食品大类（如 谷物/蔬菜/水果）；T-903 补齐 */
    private String category;

    private String remark;
}
