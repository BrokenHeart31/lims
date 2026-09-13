package com.lims.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/编辑请求（api-spec 第 13 章，T-107）。
 *
 * <p>安全约束（AGENTS 4.2）：password 只在新增/重置时传入，**任何 VO 不返回密码**。
 * 编辑时 password 为空表示「不改密码」，由服务层区分「传了空串」与「传了 null」。</p>
 */
@Data
public class SysUserSaveDTO {

    /** 编辑时必填；新增时忽略 */
    private Long id;

    /** 登录名/工号，如 nj001。不可修改（编辑时忽略）。 */
    @NotBlank(message = "登录名不能为空")
    @Size(max = 32, message = "登录名不能超过 32 字符")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "登录名只能包含字母、数字、下划线")
    private String username;

    /** 明文密码（仅新增/重置密码时传）；服务层 BCrypt 加密 */
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过 50 字符")
    private String nickname;

    private Long deptId;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过 100 字符")
    private String email;

    @Size(max = 20, message = "手机号不能超过 20 字符")
    private String phone;

    /** 电子签名图片地址（T-702 报告调用）；为空时报告渲染虚线占位框 */
    @Size(max = 255, message = "签名地址不能超过 255 字符")
    private String signatureUrl;

    /** 1=启用 0=停用；null 按启用处理 */
    @Min(value = 0, message = "状态取值只能是 0 或 1")
    @Max(value = 1, message = "状态取值只能是 0 或 1")
    private Integer status;

    @Size(max = 255, message = "备注不能超过 255 字符")
    private String remark;

    /** 角色 id 集合（全量覆盖式绑定） */
    private List<Long> roleIds;
}
