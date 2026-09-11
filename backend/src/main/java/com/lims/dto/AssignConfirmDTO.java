package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 安排确认请求（api-spec 5.6，S30 → S40）。
 */
@Data
public class AssignConfirmDTO {

    /** 样品ID（sample_info.id） */
    @NotNull(message = "样品ID不能为空")
    private Long sampleId;
}
