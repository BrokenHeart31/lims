package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 恢复某次回退请求（api-spec B4）。
 */
@Data
public class RollbackRecoverDTO {

    @NotNull(message = "回退记录ID不能为空")
    private Long rollbackId;

    /** 恢复原因（可空；建议填写便于追溯） */
    @Size(max = 500, message = "恢复原因不能超过 500 字")
    private String reason;
}
