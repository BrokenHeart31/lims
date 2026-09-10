package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单/权限（sys_menu）：菜单树与按钮权限标识一体。
 * menu_type：1=目录 2=菜单 3=按钮；按钮行的 permission 即 AGENTS 8.2 权限标识。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 目录 */
    public static final int TYPE_DIR = 1;
    /** 菜单（页面） */
    public static final int TYPE_MENU = 2;
    /** 按钮（权限点） */
    public static final int TYPE_BUTTON = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父菜单 id，0=根 */
    private Long parentId;

    private String title;

    /** 前端路由路径（目录/菜单） */
    private String path;

    private String icon;

    /** 1=目录 2=菜单 3=按钮 */
    private Integer menuType;

    /** 权限标识 resource:action（按钮必填） */
    private String permission;

    private Integer sortOrder;

    /** 1=显示 0=隐藏 */
    private Integer visible;
}
