package com.lims.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 采样单导入行（T-301），字段与采样单 Excel 列头一一对应。
 *
 * <p>列头取自业务说明书「采样单 Excel 格式」（22 列，数据自第 3 行起，A1 为文件标记），
 * 全部以 String 承接原始文本，由 {@code SampleImportListener} 统一做去空格/解析/校验，
 * 避免 Excel 单元格类型不定（数值/文本/日期）导致的转换异常中断整批导入。</p>
 */
@Data
public class SampleImportDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "样品编号", index = 0)
    private String sampleNo;

    @ExcelProperty(value = "样品名称", index = 1)
    private String sampleName;

    @ExcelProperty(value = "受检单位", index = 2)
    private String clientName;

    @ExcelProperty(value = "抽样地址", index = 3)
    private String samplingAddress;

    @ExcelProperty(value = "收款人", index = 4)
    private String payee;

    @ExcelProperty(value = "费用", index = 5)
    private String fee;

    @ExcelProperty(value = "样品数量", index = 6)
    private String sampleQuantity;

    @ExcelProperty(value = "项目名称", index = 7)
    private String projectName;

    @ExcelProperty(value = "日期", index = 8)
    private String samplingDate;

    @ExcelProperty(value = "备注", index = 9)
    private String remark;

    @ExcelProperty(value = "采样者", index = 10)
    private String sampler;

    @ExcelProperty(value = "生产单位", index = 11)
    private String manufacturer;

    @ExcelProperty(value = "抽样基数", index = 12)
    private String samplingBase;

    @ExcelProperty(value = "样品状态", index = 13)
    private String sampleState;

    @ExcelProperty(value = "规格型号", index = 14)
    private String spec;

    @ExcelProperty(value = "商标", index = 15)
    private String brand;

    @ExcelProperty(value = "样品等级", index = 16)
    private String grade;

    @ExcelProperty(value = "原编号或生产日期", index = 17)
    private String originalNo;

    @ExcelProperty(value = "检验类别", index = 18)
    private String inspectType;

    @ExcelProperty(value = "要求完成日期", index = 19)
    private String requireCompleteDate;

    @ExcelProperty(value = "任务编号", index = 20)
    private String taskNo;

    @ExcelProperty(value = "任务批号", index = 21)
    private String taskBatchNo;
}
