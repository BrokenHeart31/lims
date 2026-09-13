package com.lims.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 检验员任务导出行（T-603，一行 = 一个「检验任务（样品 × 已指派检测单项）」）。
 *
 * <p>列（12 列）：样品编号 / 样品名称 / 受检单位 / 任务编号 / 项次 / 检验项目 / 检验方法 /
 * 检测依据 / 标准值 / 单位 / 最低检出限 / 样品状态。</p>
 *
 * <p>「样品状态」取 {@code sample_info.sample_state}（如「鲜活」），<b>不是</b>样品状态机 code——
 * 检验员关心的是实物状态，而非流程阶段。</p>
 */
@Data
public class MyTaskExportRow {

    @ExcelProperty("样品编号")
    @ColumnWidth(22)
    private String sampleNo;

    @ExcelProperty("样品名称")
    @ColumnWidth(16)
    private String sampleName;

    @ExcelProperty("受检单位")
    @ColumnWidth(20)
    private String clientName;

    @ExcelProperty("任务编号")
    @ColumnWidth(20)
    private String taskNo;

    @ExcelProperty("项次")
    @ColumnWidth(8)
    private Integer itemOrder;

    @ExcelProperty("检验项目")
    @ColumnWidth(20)
    private String itemName;

    @ExcelProperty("检验方法")
    @ColumnWidth(24)
    private String methods;

    @ExcelProperty("检测依据")
    @ColumnWidth(22)
    private String basisCode;

    @ExcelProperty("标准值")
    @ColumnWidth(16)
    private String stdValue;

    @ExcelProperty("单位")
    @ColumnWidth(10)
    private String unit;

    @ExcelProperty("最低检出限")
    @ColumnWidth(14)
    private String lowerLimit;

    @ExcelProperty("样品状态")
    @ColumnWidth(12)
    private String sampleState;
}
