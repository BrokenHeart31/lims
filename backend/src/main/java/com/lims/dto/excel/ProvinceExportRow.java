package com.lims.dto.excel;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 省平台上报导出行（T-802，一行 = 一个「样品 × 检测单项」）。
 *
 * <p><b>严格 10 列、无双层表头、不插空列</b>（已拍板）。字段顺序即列顺序，EasyExcel 按 {@link ExcelProperty}
 * 声明顺序写出：样品编号 / 样品名称 / 抽样日期 / 检验依据 / 检验项目 / 单位 / 技术要求 / 检验结果 / 单项评价 / 任务编号。</p>
 *
 * <p><b>参考项星号</b>：{@code is_reference=1} 的项在「检验项目」名前<b>不加</b> {@code *}
 * （导出为结构化数据，星号仅用于纸质报告视觉区分）。</p>
 *
 * <p>{@code conclusion} 为源 {@code sample_result.conclusion} 码值，仅作中转（{@link ExcelIgnore} 不输出），
 * 由 Service 经 {@code ResultConclusion.getLabel()} 填充 {@code conclusionLabel}，禁止硬编码中文。</p>
 */
@Data
public class ProvinceExportRow {

    @ExcelProperty("样品编号")
    @ColumnWidth(22)
    private String sampleNo;

    @ExcelProperty("样品名称")
    @ColumnWidth(16)
    private String sampleName;

    @ExcelProperty("抽样日期")
    @ColumnWidth(14)
    private String samplingDate;

    @ExcelProperty("检验依据")
    @ColumnWidth(22)
    private String basisCode;

    @ExcelProperty("检验项目")
    @ColumnWidth(20)
    private String itemName;

    @ExcelProperty("单位")
    @ColumnWidth(10)
    private String unit;

    @ExcelProperty("技术要求")
    @ColumnWidth(16)
    private String stdValue;

    @ExcelProperty("检验结果")
    @ColumnWidth(16)
    private String testValue;

    @ExcelProperty("单项评价")
    @ColumnWidth(12)
    private String conclusionLabel;

    @ExcelProperty("任务编号")
    @ColumnWidth(20)
    private String taskNo;

    /** 单项结论码（源 sample_result.conclusion），仅中转，不输出到 Excel */
    @ExcelIgnore
    private Integer conclusion;
}
