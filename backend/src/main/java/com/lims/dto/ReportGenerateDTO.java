package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成检验报告请求体（T-702：POST /api/report/generate）。
 *
 * <p>{@code reportType} 为<b>数字 code</b>（1=CMA / 2=CMA-CATL），与 api-spec 8.3
 * 及报告 VO 出网口径完全一致；为 {@code null} 时按 CMA(1) 处理。非法 code 由
 * {@code ReportType.of} 校验并抛 400。</p>
 */
@Data
public class ReportGenerateDTO {

    /** 样品编号（sample_info.sample_no） */
    @NotBlank(message = "样品编号不能为空")
    private String sampleNo;

    /** 报告类型 code：1=CMA（缺省） / 2=CMA-CATL */
    private Integer reportType;
}
