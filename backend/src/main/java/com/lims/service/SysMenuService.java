package com.lims.service;

import com.lims.dto.SysMenuSaveDTO;
import com.lims.vo.SysMenuVO;

import java.util.List;

/**
 * 菜单/权限管理服务（api-spec 第 13 章，T-107）。
 *
 * <p>本域是全系统**权限标识的唯一权威来源**（AGENTS 8.2）：后端
 * {@code @PreAuthorize("hasAuthority('...')")} 里的字符串，必须能在
 * {@code sys_menu.permission} 找到对应行，否则任何角色都无法被授予该权限。
 * 新增权限点的正确姿势是「先在本域建按钮行，再去角色页勾选」。</p>
 */
public interface SysMenuService {

    /** 完整菜单树（含按钮权限行，供管理页展示与角色授权树复选） */
    List<SysMenuVO> tree(String title, Integer menuType);

    SysMenuVO detail(Long id);

    Long create(SysMenuSaveDTO dto);

    void update(SysMenuSaveDTO dto);

    /**
     * 逻辑删除菜单。
     *
     * <p>fail-loud 保护：存在子菜单时拒绝删除（先删子树再删父节点）；
     * 同时级联清理 {@code sys_role_menu}，避免已删除权限残留在角色授权中。</p>
     */
    void remove(Long id);
}
