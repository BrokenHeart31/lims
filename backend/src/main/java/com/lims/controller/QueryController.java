package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.QueryLibraryDTO;
import com.lims.dto.QuerySampleDTO;
import com.lims.service.QueryService;
import com.lims.vo.HistoryQueryVO;
import com.lims.vo.LibraryItemVO;
import com.lims.vo.LibraryQueryVO;
import com.lims.vo.TestingQueryVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 查询域接口（api-spec /api/query/*，T-801）。
 *
 * <p>权限标识与 AGENTS 8.2 / seed 一致：{@code query:testing}（在检样品）、
 * {@code query:history}（历史样品）、{@code base:lib:list}（项目库，与 T-106 项目库维护
 * 的 {@code base:lib:add/edit/remove} 同族——项目库是独立资源，不借道历史样品权限）。
 * 分页统一 {@code current}/{@code size}。</p>
 */
@Validated
@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /** A1 在检样品分页（status 10..70） */
    @GetMapping("/testing/page")
    @PreAuthorize("hasAuthority('query:testing')")
    public R<PageResult<TestingQueryVO>> pageTesting(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            QuerySampleDTO q) {
        return R.ok(queryService.pageTesting(current, size, q));
    }

    /** A2 历史样品分页（status 80/90，可按整体结论 / 是否已生成报告筛选） */
    @GetMapping("/history/page")
    @PreAuthorize("hasAuthority('query:history')")
    public R<PageResult<HistoryQueryVO>> pageHistory(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            QuerySampleDTO q) {
        return R.ok(queryService.pageHistory(current, size, q));
    }

    /** A3 项目库（产品）分页 */
    @GetMapping("/library/page")
    @PreAuthorize("hasAuthority('base:lib:list')")
    public R<PageResult<LibraryQueryVO>> pageLibrary(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            QueryLibraryDTO q) {
        return R.ok(queryService.pageLibrary(current, size, q));
    }

    /** A3 某产品的检测单项列表 */
    @GetMapping("/library/{productLibId}/items")
    @PreAuthorize("hasAuthority('base:lib:list')")
    public R<List<LibraryItemVO>> libraryItems(@PathVariable Long productLibId) {
        return R.ok(queryService.listLibraryItems(productLibId));
    }
}
