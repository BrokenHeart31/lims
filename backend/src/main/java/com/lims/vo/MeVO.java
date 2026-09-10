package com.lims.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * /me 响应（api-spec 1.3）：用户 + 权限标识集合 + 菜单树。
 */
@Data
public class MeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private UserInfoVO user;

    /** 权限标识全集（resource:action），供 v-permission 使用 */
    private List<String> permissions;

    /** 菜单树（动态路由数据源） */
    private List<MenuVO> menus;
}
