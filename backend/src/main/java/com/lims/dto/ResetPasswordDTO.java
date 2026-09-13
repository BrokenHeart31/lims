package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求（api-spec 第 13 章，T-107）。
 *
 * <p>独立于 {@link SysUserSaveDTO}：密码是单向写入的敏感字段，混在通用保存 DTO 里
 * 会让「编辑资料时误覆盖密码」成为可能。单独入口 + 单独权限意识 = 更小的失误面。</p>
 */
@Data
public class ResetPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;
}
