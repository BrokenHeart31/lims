package com.lims.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目标准库导入行（T-106，EasyExcel 读模型）。
 *
 * <p>列按索引绑定（业务方常改表头文字，按名绑定易整列读空）。
 * 索引与 `public/templates/product_lib_import_template.xlsx` 严格一致。</p>
 */
@Data
public class ProductLibImportRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A 产品编号（必填，如 SA0001） */
    @ExcelProperty(index = 0)
    private String productCode;

    /** B 产品名称 */
    @ExcelProperty(index = 1)
    private String productName;

    /** C 食品大类 */
    @ExcelProperty(index = 2)
    private String category;

    /** D 顺序号 */
    @ExcelProperty(index = 3)
    private String itemOrder;

    /** E 检测项目名称（必填） */
    @ExcelProperty(index = 4)
    private String itemName;

    /** F 单位 */
    @ExcelProperty(index = 5)
    private String unit;

    /** G 判定依据标准号 */
    @ExcelProperty(index = 6)
    private String basisCode;

    /** H 检验方法（多个用 # 分隔） */
    @ExcelProperty(index = 7)
    private String methods;

    /** I 限量值 */
    @ExcelProperty(index = 8)
    private String stdValue;

    /** J 判定类型（限量比较/不得检出/文本 或 1/2/3） */
    @ExcelProperty(index = 9)
    private String judgeType;

    /** K 是否参考项（是/否 或 1/0） */
    @ExcelProperty(index = 10)
    private String isReference;

    /** L 最低检出限 */
    @ExcelProperty(index = 11)
    private String lowerLimit;

    /** M 方法备注 */
    @ExcelProperty(index = 12)
    private String methodNote;
}
