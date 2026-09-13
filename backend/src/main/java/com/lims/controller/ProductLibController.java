package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.exception.BizException;
import com.lims.dto.ProductLibItemSaveDTO;
import com.lims.dto.ProductLibSaveDTO;
import com.lims.service.ProductLibService;
import com.lims.vo.ProductLibItemVO;
import com.lims.vo.ProductLibVO;
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
import java.util.List;

/**
 * 项目标准库接口（api-spec 第 12 章 /api/base/lib/*，T-106）。
 *
 * <p>说明书第二(3)节：本域的用途是「项目检测单项分解时系统根据项目标准库自动加载相关产品
 * 需要检测的检测单项，包括检测依据、检验方法、判定标准」，并提供「导入新的项目库」。</p>
 *
 * <p><b>权限标识</b>与 seed `sys_menu`（id 待补，命名遵循 {@code base:lib:*}）保持一致：
 * {@code base:lib:list} / {@code base:lib:add} / {@code base:lib:edit} / {@code base:lib:remove}。
 * 读取类（detail / items）复用 {@code base:lib:list}，因为「编辑弹窗要先读」是同一操作闭环的必然组成。</p>
 *
 * <p><b>边界（不可违反）</b>：本域是标准库的唯一写入方。项目分解（T-401）以「快照下沉」方式
 * 把本域字段复制进 {@code sample_item}，判定引擎（T-601）只读 {@code sample_item}。
 * 所以本域的任何修改都不会回溯影响已分解样品——这是报告可追溯性的前提（见 DECISIONS 2026-09-11）。</p>
 */
@Validated
@RestController
@RequestMapping("/base/lib")
@RequiredArgsConstructor
public class ProductLibController {

    private final ProductLibService productLibService;

    // ==================== 产品库 ====================

    /** C1 产品分页查询（列表页行数据，不含明细，避免 3000+ 明细被一次拉出） */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('base:lib:list')")
    public R<PageResult<ProductLibVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String category) {
        return R.ok(productLibService.pageProduct(current, size, productCode, productName, category));
    }

    /** C2 产品详情（含检测单项列表，供展开行 / 编辑弹窗使用） */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:lib:list')")
    public R<ProductLibVO> detail(@PathVariable Long id) {
        return R.ok(productLibService.detail(id));
    }

    /** C3 新增产品 */
    @PostMapping
    @PreAuthorize("hasAuthority('base:lib:add')")
    public R<Long> createProduct(@Valid @RequestBody ProductLibSaveDTO dto) {
        return R.ok(productLibService.createProduct(dto));
    }

    /** C4 更新产品 */
    @PutMapping
    @PreAuthorize("hasAuthority('base:lib:edit')")
    public R<Void> updateProduct(@Valid @RequestBody ProductLibSaveDTO dto) {
        productLibService.updateProduct(dto);
        return R.ok();
    }

    /**
     * C5 删除产品。
     *
     * <p>若产品下仍有检测单项，服务层会 fail-loud 拒绝——孤儿明细比报错更难排查（见 Service 注释）。
     * 前端应提示用户「先清空检测单项再删除」。</p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:lib:remove')")
    public R<Void> removeProduct(@PathVariable Long id) {
        productLibService.removeProduct(id);
        return R.ok();
    }

    // ==================== 检测单项 ====================

    /** C6 查询某产品的检测单项列表 */
    @GetMapping("/{productLibId}/items")
    @PreAuthorize("hasAuthority('base:lib:list')")
    public R<List<ProductLibItemVO>> listItems(@PathVariable Long productLibId) {
        return R.ok(productLibService.listItems(productLibId));
    }

    /** C7 新增单个检测单项 */
    @PostMapping("/item")
    @PreAuthorize("hasAuthority('base:lib:add')")
    public R<Long> createItem(@Valid @RequestBody ProductLibItemSaveDTO dto) {
        return R.ok(productLibService.createItem(dto));
    }

    /** C8 更新单个检测单项 */
    @PutMapping("/item")
    @PreAuthorize("hasAuthority('base:lib:edit')")
    public R<Void> updateItem(@Valid @RequestBody ProductLibItemSaveDTO dto) {
        productLibService.updateItem(dto);
        return R.ok();
    }

    /** C9 删除单个检测单项 */
    @DeleteMapping("/item/{id}")
    @PreAuthorize("hasAuthority('base:lib:remove')")
    public R<Void> removeItem(@PathVariable Long id) {
        productLibService.removeItem(id);
        return R.ok();
    }

    /**
     * C10 覆盖式替换某产品的全部检测单项。
     *
     * <p>用于「表格化编辑后整体保存」：Excel 是业务方对某产品的完整定义，增量合并会让
     * 「表格里删掉的一行」在系统里残留（与 T-401「保存分解为覆盖式」同一决策逻辑）。</p>
     *
     * @return 写入的明细条数
     */
    @PutMapping("/{productLibId}/items")
    @PreAuthorize("hasAuthority('base:lib:edit')")
    public R<Integer> replaceItems(@PathVariable Long productLibId,
                                   @RequestBody List<@Valid ProductLibItemSaveDTO> items) {
        return R.ok(productLibService.replaceItems(productLibId, items));
    }

    /**
     * C11 Excel 导入项目库（说明书第二(3)节「导入新的项目库」）。
     *
     * <p>按产品编号分组后覆盖式写入：同一产品在文件内多次出现会被合并；同一份表格可反复导入
     * 而不产生重复明细。逐行校验失败不回滚整批，返回「第 N 行：原因」。</p>
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:lib:add')")
    public R<ProductLibService.ImportResult> importExcel(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "导入文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new BizException(400, "仅支持 .xlsx / .xls 格式的 Excel 文件");
        }
        try (InputStream in = file.getInputStream()) {
            return R.ok(productLibService.importExcel(in));
        } catch (IOException e) {
            throw new BizException(400, "Excel 文件读取失败: " + e.getMessage());
        }
    }
}
