package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.SysRoleSaveDTO;
import com.lims.vo.SysRoleVO;

import java.util.List;

/**
 * 角色管理服务（api-spec 第 13 章，T-107）。
 *
 * <p>角色是权限的载体：{@code sys_role_menu} 决定该角色能看到哪些菜单、拥有哪些
 * {@code resource:action} 权限点。因此本域的写入直接改变系统行为，权限为 {@code sys:role:*}
 * 的角色（R100）本身应受最小授权原则约束。</p>
 */
public interface SysRoleService {

    PageResult<SysRoleVO> pageQuery(long current, long size, String roleCode, String roleName);

    /** 全部角色（下拉选项用，不分页） */
    List<SysRoleVO> listAll();

    /** 详情（含 menuIds，供权限树回显） */
    SysRoleVO detail(Long id);

    Long create(SysRoleSaveDTO dto);

    void update(SysRoleSaveDTO dto);

    /**
     * 逻辑删除角色。
     *
     * <p>fail-loud 保护：R100 综合管理不可删除（删掉即全系统失权）；
     * 角色下仍有用户时拒绝删除，避免用户静默失去全部权限。</p>
     */
    void remove(Long id);
}
