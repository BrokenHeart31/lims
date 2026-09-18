package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回退记录行（api-spec B5，feature B）。
 */
@Data
public class RollbackHistoryVO {

    private Long id;

    private Long sampleId;

    private String sampleNo;

    private Integer fromStatus;

    private String fromStatusLabel;

    private Integer toStatus;

    private String toStatusLabel;

    private Integer edgeGroup;

    private String edgeGroupLabel;

    private String reason;

    private Integer secondConfirmed;

    private Integer affectedItemCount;

    private Integer affectedResultCount;

    private Integer canRecover;

    private Integer recovered;

    private String operatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;

    private String recoverBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recoverAt;
}
