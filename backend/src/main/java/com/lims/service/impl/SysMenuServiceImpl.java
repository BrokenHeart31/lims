package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.exception.BizException;
import com.lims.dto.SysMenuSaveDTO;
import com.lims.entity.SysMenu;
import com.lims.entity.SysRoleMenu;
import com.lims.mapper.SysMenuMapper;
import com.lims.mapper.SysRoleMenuMapper;
import com.lims.service.SysMenuService;
import com.lims.vo.SysMenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单/权限管理实现（T-107）。
 *
 * <p>本域是权限标识的权威来源。三条硬约束：
 * <ol>
 *   <li><b>permission 唯一</b>：DB 上有 UNIQUE 约束，服务层先查再插，避免前端拿到 500；
 *       但**同一次保存中把 A 的 permission 改成 B 已占用的值**也要拦（否则唯一约束直接抛异常）；</li>
 *   <li><b>成环禁止</b>：parent_id 不能指向自己的子孙，否则菜单树构建无限递归；</li>
 *   <li><b>删除级联清理</b>：删菜单时一并删 {@code sys_role_menu}，否则角色授权树里残留幽灵节点，
 *       前端渲染会拿到 null 标题。</li>
 * </ol></p>
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysMenuVO> tree(String title, Integer menuType) {
        List<SysMenu> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .like(StringUtils.hasText(title), SysMenu::getTitle, title)
                .eq(menuType != null, SysMenu::getMenuType, menuType)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
        return buildTree(menus);
    }

    @Override
    public SysMenuVO detail(Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(404, "菜单不存在或已删除");
        }
        return SysMenuVO.from(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysMenuSaveDTO dto) {
        validateParent(dto.getParentId(), null);
        validatePermission(dto);
        validateTypeShape(dto);

        SysMenu menu = new SysMenu();
        copy(menu, dto);
        sysMenuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysMenuSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "菜单 id 不能为空");
        }
        SysMenu exist = sysMenuMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BizException(404, "菜单不存在或已删除");
        }
        validateParent(dto.getParentId(), dto.getId());
        validatePermission(dto);
        validateTypeShape(dto);

        SysMenu menu = new SysMenu();
        menu.setId(dto.getId());
        copy(menu, dto);
        sysMenuMapper.updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysMenu exist = sysMenuMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "菜单不存在或已删除");
        }
        Long childCount = sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(409, "该菜单下还有 " + childCount + " 个子节点，请先删除子节点");
        }
        sysMenuMapper.deleteById(id);
        // 级联清理授权关系：否则角色授权树会残留幽灵节点
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    // ==================== 内部方法 ====================

    private void copy(SysMenu menu, SysMenuSaveDTO dto) {
        menu.setParentId(dto.getParentId());
        menu.setTitle(dto.getTitle().trim());
        menu.setPath(StringUtils.hasText(dto.getPath()) ? dto.getPath().trim() : null);
        menu.setIcon(dto.getIcon());
        menu.setMenuType(dto.getMenuType());
        menu.setPermission(StringUtils.hasText(dto.getPermission()) ? dto.getPermission().trim() : null);
        menu.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        menu.setVisible(dto.getVisible() == null ? 1 : dto.getVisible());
    }

    /**
     * 父节点校验：存在 + 不成环。
     *
     * <p>成环判定用「沿 parent_id 向根走，若途经自身则成环」——比全树遍历廉价，
     * 且能覆盖「把 A 挂到自己子孙下」这种多层环。</p>
     */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            throw new BizException(400, "上级菜单不能为空（根节点请传 0）");
        }
        if (parentId == 0L) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(400, "上级菜单不能是自己");
        }
        SysMenu parent = sysMenuMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(400, "上级菜单不存在");
        }
        if (selfId == null) {
            return;
        }
        // 向上溯源，途经 selfId 即成环
        Long cursor = parentId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 64) {
            if (cursor.equals(selfId)) {
                throw new BizException(400, "上级菜单不能是自己的子孙节点（会形成环路）");
            }
            SysMenu ancestor = sysMenuMapper.selectById(cursor);
            cursor = ancestor == null ? null : ancestor.getParentId();
        }
    }

    /** permission 唯一性（DB UNIQUE 的前置拦截，把 500 变成可读业务错误） */
    private void validatePermission(SysMenuSaveDTO dto) {
        if (!StringUtils.hasText(dto.getPermission())) {
            return;
        }
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPermission, dto.getPermission().trim());
        if (dto.getId() != null) {
            wrapper.ne(SysMenu::getId, dto.getId());
        }
        Long dup = sysMenuMapper.selectCount(wrapper);
        if (dup != null && dup > 0) {
            throw new BizException(409, "权限标识「" + dto.getPermission().trim() + "」已被其他菜单占用");
        }
    }

    /**
     * 类型与字段形态一致性（fail-loud，而非静默接受无意义数据）。
     *
     * <p>按钮必须带 permission（否则这是一个「永远无法被授权」的死权限点）；
     * 目录/菜单不应带 permission（它们是被授权的容器，不是权限点本身）；
     * 菜单必须有 path（否则前端无法路由）。</p>
     */
    private void validateTypeShape(SysMenuSaveDTO dto) {
        int type = dto.getMenuType();
        String permission = dto.getPermission() == null ? "" : dto.getPermission().trim();
        String path = dto.getPath() == null ? "" : dto.getPath().trim();
        if (type == SysMenu.TYPE_BUTTON && permission.isEmpty()) {
            throw new BizException(400, "按钮类型必须填写权限标识（resource:action）");
        }
        if (type != SysMenu.TYPE_BUTTON && !permission.isEmpty()) {
            throw new BizException(400, "目录/菜单类型不应填写权限标识，请改为按钮类型");
        }
        if (type == SysMenu.TYPE_MENU && path.isEmpty()) {
            throw new BizException(400, "菜单类型必须填写前端路由路径");
        }
    }

    /** 平铺列表 → 树（parent_id=0 为根；父节点不在筛选结果中的节点提升为根，避免数据「消失」） */
    private List<SysMenuVO> buildTree(List<SysMenu> menus) {
        Map<Long, SysMenuVO> nodeMap = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodeMap.put(menu.getId(), SysMenuVO.from(menu));
        }
        List<SysMenuVO> roots = new ArrayList<>();
        for (SysMenuVO node : nodeMap.values()) {
            SysMenuVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
