package com.lims.controller;

import com.lims.common.R;
import com.lims.dto.DeptSaveDTO;
import com.lims.service.DeptService;
import com.lims.vo.DeptVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理接口（api-spec 第 13 章 /api/sys/dept/*，T-107）。
 *
 * <p>权限标识与 seed `sys_menu`（id=1141~1144）一致：{@code sys:dept:list/add/edit/remove}。</p>
 *
 * <p>部门树同时被用户编辑页的下拉复用（{@code /list} 扁平接口）。</p>
 */
@Validated
@RestController
@RequestMapping("/sys/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    /** G1 部门树（含每部门用户数） */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:dept:list')")
    public R<List<DeptVO>> tree() {
        return R.ok(deptService.tree());
    }

    /** G2 部门扁平列表（下拉选项） */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('sys:dept:list')")
    public R<List<DeptVO>> listAll() {
        return R.ok(deptService.listAll());
    }

    /** G3 详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:dept:list')")
    public R<DeptVO> detail(@PathVariable Long id) {
        return R.ok(deptService.detail(id));
    }

    /** G4 新增 */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:dept:add')")
    public R<Long> create(@Valid @RequestBody DeptSaveDTO dto) {
        return R.ok(deptService.create(dto));
    }

    /** G5 更新（parent_id 成环会被拒绝） */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:dept:edit')")
    public R<Void> update(@Valid @RequestBody DeptSaveDTO dto) {
        deptService.update(dto);
        return R.ok();
    }

    /** G6 逻辑删除（有子部门或有用户时拒绝） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:dept:remove')")
    public R<Void> remove(@PathVariable Long id) {
        deptService.remove(id);
        return R.ok();
    }
}
