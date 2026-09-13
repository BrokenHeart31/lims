package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.ResetPasswordDTO;
import com.lims.dto.SysUserSaveDTO;
import com.lims.service.SysUserService;
import com.lims.vo.SysUserVO;
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

/**
 * 用户管理接口（api-spec 第 13 章 /api/sys/user/*，T-107）。
 *
 * <p>权限标识与 seed `sys_menu`（id=1111~1114）一致：{@code sys:user:list/add/edit/remove}。</p>
 */
@Validated
@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** D1 分页查询 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public R<PageResult<SysUserVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer status) {
        return R.ok(sysUserService.pageQuery(current, size, username, nickname, deptId, status));
    }

    /** D2 详情（含角色 id，供编辑弹窗回显） */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public R<SysUserVO> detail(@PathVariable Long id) {
        return R.ok(sysUserService.detail(id));
    }

    /** D3 新增 */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:add')")
    public R<Long> create(@Valid @RequestBody SysUserSaveDTO dto) {
        return R.ok(sysUserService.create(dto));
    }

    /** D4 更新（不含密码；改密码走 D5） */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public R<Void> update(@Valid @RequestBody SysUserSaveDTO dto) {
        sysUserService.update(dto);
        return R.ok();
    }

    /**
     * D5 重置密码。
     *
     * <p>复用 {@code sys:user:edit} 权限而非单列权限点：重置密码是「编辑用户」的组成部分，
     * 单列会制造一个「能改资料不能改密码」的奇怪中间态。密码单向进入，永不回吐。</p>
     */
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        sysUserService.resetPassword(id, dto.getPassword());
        return R.ok();
    }

    /** D6 逻辑删除（服务层保护：不可删当前登录账号 / 最后一个 R100） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:remove')")
    public R<Void> remove(@PathVariable Long id) {
        sysUserService.remove(id);
        return R.ok();
    }
}
