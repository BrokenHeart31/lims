package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色新增/编辑请求（api-spec 第 13 章，T-107）。
 *
 * <p>菜单权限为**全量覆盖式**绑定（roleIds → menuIds），与 T-401 分解覆盖式同一决策逻辑：
 * 前端提交的一定是该角色当前完整的权限集合，增量合并会让「取消勾选的项」残留。</p>
 */
@Data
public class SysRoleSaveDTO {

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 32, message = "角色编码不能超过 32 字符")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "角色编码只能包含字母、数字、下划线")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过 50 字符")
    private String roleName;

    @Size(max = 255, message = "描述不能超过 255 字符")
    private String description;

    /** 菜单/权限 id 集合（全量覆盖式） */
    private List<Long> menuIds;
}
