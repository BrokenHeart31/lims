package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 签发请求（api-spec 7.6，T-701）。
 *
 * <p>签发是最后一个把关动作：样品由 S70（已审核）→ S80（已签发），之后即可生成报告。
 * 审核环节已完成异常项确认，故本请求不再重复要求 {@code abnormalConfirmed}。</p>
 */
@Data
public class ReportSignDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    @Size(max = 500, message = "签发意见不能超过 500 字")
    private String opinion;
}
