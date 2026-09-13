package com.lims.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单/权限新增/编辑请求（api-spec 第 13 章，T-107）。
 *
 * <p>menu_type：1=目录 2=菜单 3=按钮。按钮行的 permission 即 AGENTS 8.2 权限标识
 * {@code resource:action}，是后端 {@code @PreAuthorize} 的唯一来源，因此必须校验格式。</p>
 */
@Data
public class SysMenuSaveDTO {

    private Long id;

    /** 父菜单 id，0=根 */
    @NotNull(message = "上级菜单不能为空")
    private Long parentId;

    @NotBlank(message = "菜单标题不能为空")
    @Size(max = 50, message = "菜单标题不能超过 50 字符")
    private String title;

    /** 前端路由路径（目录/菜单必填，按钮为空） */
    @Size(max = 200, message = "路由路径不能超过 200 字符")
    private String path;

    @Size(max = 50, message = "图标名不能超过 50 字符")
    private String icon;

    /** 1=目录 2=菜单 3=按钮 */
    @NotNull(message = "菜单类型不能为空")
    @Min(value = 1, message = "菜单类型只能是 1/2/3")
    @Max(value = 3, message = "菜单类型只能是 1/2/3")
    private Integer menuType;

    /**
     * 权限标识 resource:action，按钮必填、目录/菜单可空。
     *
     * <p>DB 上 permission 有唯一约束（`sys_menu.permission` UNI），因此服务层必须做
     * 「非空且重复」校验——否则会直接抛 SQLIntegrityConstraintViolationException，
     * 前端拿到 500 而非可读的业务错误。</p>
     */
    @Size(max = 100, message = "权限标识不能超过 100 字符")
    @Pattern(regexp = "^$|^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$",
            message = "权限标识需形如 resource:action（小写字母/数字/中划线，可多级冒号分隔）")
    private String permission;

    private Integer sortOrder;

    /** 1=显示 0=隐藏 */
    @Min(value = 0, message = "显示状态只能是 0 或 1")
    @Max(value = 1, message = "显示状态只能是 0 或 1")
    private Integer visible;
}
