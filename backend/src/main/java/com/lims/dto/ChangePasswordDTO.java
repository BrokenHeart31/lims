package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前登录用户修改自己的密码（自服务，无需 sys:user:edit 权限）。
 *
 * <p>与 {@link ResetPasswordDTO} 的区别：本接口要求先校验旧密码，
 * 适用于用户在个人中心自助改密；管理员重置密码走 PUT /sys/user/{id}/password。</p>
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String newPassword;
}
