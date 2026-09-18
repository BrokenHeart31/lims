package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报告作废 / 召回结果（api-spec B6，feature B）。
 */
@Data
public class ReportVoidResultVO {

    private Long sampleId;

    private String sampleNo;

    private Integer voidType;

    /** 类型中文：作废 / 召回 */
    private String voidTypeLabel;

    private Integer statusAtVoid;

    private String statusAtVoidLabel;

    private String reason;

    private Integer secondConfirmed;

    private String operatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;
}
