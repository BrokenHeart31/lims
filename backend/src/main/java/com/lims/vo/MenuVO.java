package com.lims.vo;

import com.lims.entity.SysMenu;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（api-spec 1.3 menus 节点）。仅含目录/菜单（menu_type 1/2），按钮权限不出树。
 */
@Data
public class MenuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 父菜单 id，0=根 */
    private Long parentId;

    private String title;

    private String path;

    private String icon;

    private List<MenuVO> children = new ArrayList<>();

    public static MenuVO from(SysMenu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setTitle(menu.getTitle());
        vo.setPath(menu.getPath());
        vo.setIcon(menu.getIcon());
        return vo;
    }
}
