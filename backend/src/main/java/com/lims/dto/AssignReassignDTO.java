package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工改派请求（api-spec 5.5）。
 */
@Data
public class AssignReassignDTO {

    /** 检测单项ID（sample_item.id） */
    @NotNull(message = "检测单项ID不能为空")
    private Long itemId;

    /** 目标检验员工号（sys_user.username）；仅允许有资质者，见 api-spec 5.5 */
    @NotBlank(message = "检验员工号不能为空")
    @Size(max = 32, message = "检验员工号长度不能超过 32")
    private String testerNo;
}
