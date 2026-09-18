package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 报告作废 / 召回请求（api-spec B6，S80/S90 专用）。
 */
@Data
public class ReportVoidDTO {

    @NotBlank(message = "样品编号不能为空")
    private String sampleNo;

    /** 类型 1=作废 2=召回 */
    @NotNull(message = "类型不能为空")
    private Integer voidType;

    @NotBlank(message = "作废/召回原因不能为空")
    @Size(max = 500, message = "原因不能超过 500 字")
    private String reason;

    /** 需二次确认（报告已对外生效，强约束） */
    private Boolean secondConfirmed;
}
