package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 回退前预览请求（api-spec B2）。
 */
@Data
public class RollbackPreviewDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    /** 目标状态 code（必须为当前状态的「上一级」） */
    @NotNull(message = "目标状态不能为空")
    private Integer targetStatus;
}
