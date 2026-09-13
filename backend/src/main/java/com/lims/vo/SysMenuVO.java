package com.lims.vo;

import com.lims.entity.SysMenu;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单管理视图（api-spec 第 13 章，T-107）。
 *
 * <p>与 {@link MenuVO}（登录后的导航树，仅含目录/菜单）不同：本 VO 用于**系统管理页**，
 * 必须包含按钮权限行，因为管理员需要在同一棵树里看到并维护 {@code resource:action} 权限点。</p>
 */
@Data
public class SysMenuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    private String title;

    private String path;

    private String icon;

    /** 1=目录 2=菜单 3=按钮 */
    private Integer menuType;

    /** 权限标识（按钮必填） */
    private String permission;

    private Integer sortOrder;

    /** 1=显示 0=隐藏 */
    private Integer visible;

    private List<SysMenuVO> children = new ArrayList<>();

    public static SysMenuVO from(SysMenu menu) {
        SysMenuVO vo = new SysMenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setTitle(menu.getTitle());
        vo.setPath(menu.getPath());
        vo.setIcon(menu.getIcon());
        vo.setMenuType(menu.getMenuType());
        vo.setPermission(menu.getPermission());
        vo.setSortOrder(menu.getSortOrder());
        vo.setVisible(menu.getVisible());
        return vo;
    }
}
