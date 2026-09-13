package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.SysRoleSaveDTO;
import com.lims.service.SysRoleService;
import com.lims.vo.SysRoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * 角色管理接口（api-spec 第 13 章 /api/sys/role/*，T-107）。
 *
 * <p>权限标识与 seed `sys_menu`（id=1121~1124）一致：{@code sys:role:list/add/edit/remove}。</p>
 */
@Validated
@RestController
@RequestMapping("/sys/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    /** E1 分页查询 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public R<PageResult<SysRoleVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String roleName) {
        return R.ok(sysRoleService.pageQuery(current, size, roleCode, roleName));
    }

    /** E2 全部角色（下拉选项；用户编辑页的「角色」多选需要它） */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public R<List<SysRoleVO>> listAll() {
        return R.ok(sysRoleService.listAll());
    }

    /** E3 详情（含 menuIds，供权限树回显） */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public R<SysRoleVO> detail(@PathVariable Long id) {
        return R.ok(sysRoleService.detail(id));
    }

    /** E4 新增（含全量覆盖式绑定菜单权限） */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:role:add')")
    public R<Long> create(@Valid @RequestBody SysRoleSaveDTO dto) {
        return R.ok(sysRoleService.create(dto));
    }

    /** E5 更新（R100 的 roleCode 不可变；R100 的权限绑定被忽略） */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:role:edit')")
    public R<Void> update(@Valid @RequestBody SysRoleSaveDTO dto) {
        sysRoleService.update(dto);
        return R.ok();
    }

    /** E6 逻辑删除（服务层保护：R100 不可删；有用户绑定则拒绝） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:remove')")
    public R<Void> remove(@PathVariable Long id) {
        sysRoleService.remove(id);
        return R.ok();
    }
}
