package com.lims.service;

import com.lims.vo.MenuVO;

import java.util.List;

/**
 * 权限装配服务：用户 → 角色 → 权限标识/菜单树（AGENTS 8.3）。
 * R100 综合管理角色直接拥有全部权限与菜单（SysRole.ADMIN_ROLE_CODE）。
 */
public interface SysPermissionService {

    /** 用户的角色编码集合 */
    List<String> getRoleCodes(Long userId);

    /** 用户的权限标识全集（resource:action） */
    List<String> getPermissions(Long userId);

    /** 用户可见的菜单树（目录/菜单，按 sort_order 组树） */
    List<MenuVO> getMenuTree(Long userId);
}
