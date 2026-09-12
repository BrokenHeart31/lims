package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核退回请求（api-spec 7.5，T-701）。
 *
 * <p>退回是「否定」动作：样品由 S60（检验完成）退至 S50（检验中），打回检验员重录
 * （AGENTS 7.2 表格末行）。<b>退回原因必填</b>——检验员需要知道要改什么，
 * 且流水表要留痕「谁因何退回」。</p>
 */
@Data
public class AuditReturnDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    @NotBlank(message = "退回原因不能为空")
    @Size(max = 500, message = "退回原因不能超过 500 字")
    private String reason;
}
