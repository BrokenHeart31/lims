package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.enums.SampleStatus;
import com.lims.dto.ItemConfirmDTO;
import com.lims.dto.ItemSaveDTO;
import com.lims.entity.SampleItem;
import com.lims.service.ItemService;
import com.lims.vo.ItemMatchVO;
import com.lims.vo.ItemPendingVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目分解接口（api-spec 第 4 章 /api/item/*，T-401）。
 *
 * <p>权限标识与 seed sys_menu(id=41)、AGENTS 8.2 一致：`item:decompose`；
 * 查询明细额外放行 `sample:query`（分解页需先查样品）。</p>
 */
@Validated
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /** 套库预览：按样品名匹配产品标准库，返回检测单项初稿（不落库） */
    @GetMapping("/match/{sampleId}")
    @PreAuthorize("hasAuthority('item:decompose')")
    public R<ItemMatchVO> match(@PathVariable Long sampleId) {
        return R.ok(itemService.match(sampleId));
    }

    /** 查询样品已保存的分解明细 */
    @GetMapping("/list/{sampleId}")
    @PreAuthorize("hasAuthority('item:decompose') or hasAuthority('sample:query')")
    public R<Map<String, Object>> list(@PathVariable Long sampleId) {
        List<SampleItem> items = itemService.listBySampleId(sampleId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sampleId", sampleId);
        data.put("items", items);
        return R.ok(data);
    }

    /** 保存分解（覆盖式，要求样品 S20） */
    @PutMapping("/save")
    @PreAuthorize("hasAuthority('item:decompose')")
    public R<Map<String, Object>> save(@Valid @RequestBody ItemSaveDTO dto) {
        int count = itemService.saveDecompose(dto);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sampleId", dto.getSampleId());
        data.put("itemCount", count);
        return R.ok(data);
    }

    /** 分解确认：S20 → S30 */
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('item:decompose')")
    public R<Map<String, Object>> confirm(@Valid @RequestBody ItemConfirmDTO dto) {
        int status = itemService.confirm(dto.getSampleId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sampleId", dto.getSampleId());
        data.put("status", status);
        data.put("statusLabel", SampleStatus.of(status).getLabel());
        return R.ok(data);
    }

    /** 分页查询待分解样品（status=S20） */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('item:decompose')")
    public R<PageResult<ItemPendingVO>> pending(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName) {
        return R.ok(PageResult.of(itemService.pagePending(current, size, sampleNo, sampleName)));
    }
}
