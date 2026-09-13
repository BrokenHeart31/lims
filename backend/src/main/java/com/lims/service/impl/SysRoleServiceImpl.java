package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.exception.BizException;
import com.lims.dto.SysRoleSaveDTO;
import com.lims.entity.SysMenu;
import com.lims.entity.SysRole;
import com.lims.entity.SysRoleMenu;
import com.lims.entity.SysUserRole;
import com.lims.mapper.SysMenuMapper;
import com.lims.mapper.SysRoleMapper;
import com.lims.mapper.SysRoleMenuMapper;
import com.lims.mapper.SysUserRoleMapper;
import com.lims.service.SysRoleService;
import com.lims.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色管理实现（T-107）。
 *
 * <p>权限绑定采用**全量覆盖式**：{@code sys_role_menu} 先按 roleId 全删再按提交的 menuIds 重建。
 * 这样「取消勾选」才能真正生效——增量 upsert 只能加不能减，是权限系统最常见的隐蔽漏洞。</p>
 *
 * <p>R100 综合管理是硬编码的特权角色（{@link SysRole#ADMIN_ROLE_CODE}）：它不依赖 sys_role_menu，
 * 在 {@code SysPermissionServiceImpl} 中直接返回全部权限。因此**禁止删除 R100**，
 * 也**禁止编辑其 roleCode**（改用列表接口忽略 roleCode 变更），否则全系统失权。</p>
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysMenuMapper sysMenuMapper;

    @Override
    public PageResult<SysRoleVO> pageQuery(long current, long size, String roleCode, String roleName) {
        Page<SysRole> page = sysRoleMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<SysRole>()
                        .like(StringUtils.hasText(roleCode), SysRole::getRoleCode, roleCode)
                        .like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                        .orderByAsc(SysRole::getId));
        return PageResult.of(page, r -> toVO(r, null));
    }

    @Override
    public List<SysRoleVO> listAll() {
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
        return roles.stream().map(r -> toVO(r, null)).toList();
    }

    @Override
    public SysRoleVO detail(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BizException(404, "角色不存在或已删除");
        }
        List<SysRoleMenu> links = sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        List<Long> menuIds = links.stream().map(SysRoleMenu::getMenuId).toList();
        return toVO(role, menuIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysRoleSaveDTO dto) {
        String code = dto.getRoleCode().trim();
        Long dup = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code));
        if (dup != null && dup > 0) {
            throw new BizException(409, "角色编码「" + code + "」已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(dto.getRoleName().trim());
        role.setDescription(dto.getDescription());
        sysRoleMapper.insert(role);

        rebindMenus(role.getId(), dto.getMenuIds());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRoleSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "角色 id 不能为空");
        }
        SysRole exist = sysRoleMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BizException(404, "角色不存在或已删除");
        }
        boolean isAdmin = SysRole.ADMIN_ROLE_CODE.equals(exist.getRoleCode());

        String code = dto.getRoleCode().trim();
        // R100 是硬编码特权角色，改编码会让 isAdmin 判定失效 → 全系统失权
        if (isAdmin && !SysRole.ADMIN_ROLE_CODE.equals(code)) {
            throw new BizException(409, "综合管理（R100）是系统特权角色，不允许修改角色编码");
        }
        if (!isAdmin) {
            Long dup = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, code)
                    .ne(SysRole::getId, dto.getId()));
            if (dup != null && dup > 0) {
                throw new BizException(409, "角色编码「" + code + "」已存在");
            }
        }

        SysRole role = new SysRole();
        role.setId(dto.getId());
        role.setRoleCode(code);
        role.setRoleName(dto.getRoleName().trim());
        role.setDescription(dto.getDescription());
        sysRoleMapper.updateById(role);

        // R100 不依赖 sys_role_menu，其权限绑定无意义；跳过以免制造「看似受限其实全权」的误导数据
        if (!isAdmin && dto.getMenuIds() != null) {
            rebindMenus(dto.getId(), dto.getMenuIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysRole exist = sysRoleMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "角色不存在或已删除");
        }
        if (SysRole.ADMIN_ROLE_CODE.equals(exist.getRoleCode())) {
            throw new BizException(409, "综合管理（R100）是系统特权角色，不允许删除");
        }
        Long userCount = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(409, "该角色下还有 " + userCount + " 个用户，请先解除绑定再删除");
        }
        sysRoleMapper.deleteById(id);
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    }

    // ==================== 内部方法 ====================

    private void rebindMenus(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(menuIds);
        // 校验菜单存在，避免悬空 menu_id（权限树回显时会出现幽灵节点）
        List<SysMenu> menus = sysMenuMapper.selectBatchIds(distinct);
        if (menus.size() != distinct.size()) {
            throw new BizException(400, "存在无效的菜单/权限 id");
        }
        for (Long menuId : distinct) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            sysRoleMenuMapper.insert(rm);
        }
    }

    private SysRoleVO toVO(SysRole role, List<Long> menuIds) {
        SysRoleVO vo = new SysRoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setMenuIds(menuIds);
        vo.setCreatedAt(role.getCreatedAt());
        Long count = sysUserRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, role.getId()));
        vo.setUserCount(count == null ? 0 : count.intValue());
        return vo;
    }

    /** 批量预热用（当前未使用，保留给后续角色下拉批量查询优化） */
    @SuppressWarnings("unused")
    private Map<Long, Integer> countUsersByRoleIds(List<Long> roleIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (roleIds.isEmpty()) {
            return map;
        }
        List<SysUserRole> links = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
        for (SysUserRole link : links) {
            map.merge(link.getRoleId(), 1, Integer::sum);
        }
        return map;
    }

    /** 批量预热用（当前未使用，保留给列表页 N+1 优化） */
    @SuppressWarnings("unused")
    private List<Long> menuIdsOf(Long roleId) {
        return new ArrayList<>(sysRoleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList());
    }
}
