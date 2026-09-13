package com.lims.controller;

import com.lims.common.R;
import com.lims.dto.SysMenuSaveDTO;
import com.lims.service.SysMenuService;
import com.lims.vo.SysMenuVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单/权限管理接口（api-spec 第 13 章 /api/sys/menu/*，T-107）。
 *
 * <p>权限标识与 seed `sys_menu`（id=1131~1134）一致：{@code sys:menu:list/add/edit/remove}。</p>
 *
 * <p>注意：{@code GET /sys/menu/tree} 同时被两个场景复用——
 * 菜单管理页（需要看到按钮权限行）与角色授权页（权限树复选）。因此本接口返回**含按钮**的完整树，
 * 与登录后导航树 {@code /auth/me?menus} 的「仅目录/菜单」语义严格区分。</p>
 */
@Validated
@RestController
@RequestMapping("/sys/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /** F1 完整菜单树（含按钮权限行） */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:menu:list')")
    public R<List<SysMenuVO>> tree(@RequestParam(required = false) String title,
                                   @RequestParam(required = false) Integer menuType) {
        return R.ok(sysMenuService.tree(title, menuType));
    }

    /** F2 详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:menu:list')")
    public R<SysMenuVO> detail(@PathVariable Long id) {
        return R.ok(sysMenuService.detail(id));
    }

    /** F3 新增（按钮类型必须带 resource:action 权限标识） */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:menu:add')")
    public R<Long> create(@Valid @RequestBody SysMenuSaveDTO dto) {
        return R.ok(sysMenuService.create(dto));
    }

    /** F4 更新（parent_id 成环会被拒绝） */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:menu:edit')")
    public R<Void> update(@Valid @RequestBody SysMenuSaveDTO dto) {
        sysMenuService.update(dto);
        return R.ok();
    }

    /** F5 逻辑删除（有子节点拒绝；级联清理 sys_role_menu） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:menu:remove')")
    public R<Void> remove(@PathVariable Long id) {
        sysMenuService.remove(id);
        return R.ok();
    }
}
