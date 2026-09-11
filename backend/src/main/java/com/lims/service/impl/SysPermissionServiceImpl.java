package com.lims.service.impl;

import com.lims.entity.SysMenu;
import com.lims.entity.SysRole;
import com.lims.mapper.SysMenuMapper;
import com.lims.mapper.SysRoleMapper;
import com.lims.service.SysPermissionService;
import com.lims.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<String> getRoleCodes(Long userId) {
        return sysRoleMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public List<String> getPermissions(Long userId) {
        if (isAdmin(userId)) {
            return sysMenuMapper.selectAllPermissions();
        }
        return sysMenuMapper.selectPermissionsByUserId(userId);
    }

    @Override
    public List<MenuVO> getMenuTree(Long userId) {
        List<SysMenu> menus = isAdmin(userId)
                ? sysMenuMapper.selectAllVisibleMenus()
                : sysMenuMapper.selectVisibleMenusByUserId(userId);
        return buildTree(menus);
    }

    /** 是否 R100 综合管理（拥有全部权限/菜单，AGENTS 8.1） */
    private boolean isAdmin(Long userId) {
        return getRoleCodes(userId).contains(SysRole.ADMIN_ROLE_CODE);
    }

    /** 平铺列表（已按 sort_order 排序）→ 树；parent_id=0 为根 */
    private List<MenuVO> buildTree(List<SysMenu> menus) {
        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodeMap.put(menu.getId(), MenuVO.from(menu));
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : nodeMap.values()) {
            MenuVO parent = nodeMap.get(node.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
