package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.exception.BizException;
import com.lims.dto.TesterMethodSaveDTO;
import com.lims.service.TesterMethodService;
import com.lims.vo.TesterMethodVO;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 方法-检验员资质接口（api-spec 第 11 章 /api/base/tester-method/*，T-105）。
 *
 * <p>权限标识与 AGENTS 8.2 / seed `sys_menu`（id=931~934）一致：
 * {@code base:tester-method:list/add/edit/remove}。查询额外放行 {@code base:tester-method:list}
 * 已覆盖，无需借道其他域权限。</p>
 */
@Validated
@RestController
@RequestMapping("/base/tester-method")
@RequiredArgsConstructor
public class TesterMethodController {

    private final TesterMethodService testerMethodService;

    /** B1 分页查询 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('base:tester-method:list')")
    public R<PageResult<TesterMethodVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String methodNo,
            @RequestParam(required = false) String testerNo,
            @RequestParam(required = false) String methodName,
            @RequestParam(required = false) Integer qualStatus) {
        return R.ok(testerMethodService.pageQuery(current, size, methodNo, testerNo, methodName, qualStatus));
    }

    /** B2 新增 */
    @PostMapping
    @PreAuthorize("hasAuthority('base:tester-method:add')")
    public R<Long> create(@Valid @RequestBody TesterMethodSaveDTO dto) {
        return R.ok(testerMethodService.create(dto));
    }

    /** B3 更新 */
    @PutMapping
    @PreAuthorize("hasAuthority('base:tester-method:edit')")
    public R<Void> update(@Valid @RequestBody TesterMethodSaveDTO dto) {
        testerMethodService.update(dto);
        return R.ok();
    }

    /** B4 逻辑删除 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:tester-method:remove')")
    public R<Void> remove(@PathVariable Long id) {
        testerMethodService.remove(id);
        return R.ok();
    }

    /**
     * B5 Excel 批量导入（说明书第二(2)节「添加检验员-检验方法」的批量形态）。
     *
     * <p>部分失败不回滚：合法行入库、错误行逐条报行号+原因；同一「方法+工号」幂等覆盖，
     * 使同一份表格可反复导入而不产生重复行。</p>
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:tester-method:add')")
    public R<TesterMethodService.ImportResult> importExcel(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "导入文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new BizException(400, "仅支持 .xlsx / .xls 格式的 Excel 文件");
        }
        try (InputStream in = file.getInputStream()) {
            return R.ok(testerMethodService.importExcel(in, null));
        } catch (IOException e) {
            throw new BizException(400, "Excel 文件读取失败: " + e.getMessage());
        }
    }
}
