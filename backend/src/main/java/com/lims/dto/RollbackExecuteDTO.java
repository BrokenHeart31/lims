package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 执行回退请求（api-spec B3）。
 */
@Data
public class RollbackExecuteDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    @NotNull(message = "目标状态不能为空")
    private Integer targetStatus;

    /** 回退原因（必填，业务校验） */
    @Size(max = 500, message = "回退原因不能超过 500 字")
    private String reason;

    /** 敏感边（S70→S60）需二次确认 */
    private Boolean secondConfirmed;
}
