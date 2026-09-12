package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核/签发动作结果（api-spec 7.4 / 7.5 / 7.6，T-701）。
 */
@Data
public class AuditActionVO {

    private Long sampleId;

    private String sampleNo;

    /** 动作后样品状态 code */
    private Integer status;

    private String statusLabel;

    /** 动作：1=审核通过 2=审核退回 3=签发 */
    private Integer action;

    private String actionLabel;

    private String opinion;

    /** 是否已确认异常项清单 */
    private Integer abnormalConfirmed;

    private String operatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;
}
