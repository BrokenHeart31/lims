package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.ReportGenerateDTO;
import com.lims.service.ReportGenerateService;
import com.lims.vo.ReportPendingVO;
import com.lims.vo.ReportVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检验报告生成 / 重打接口（T-702，api-spec 第 7/8 章 /api/report/*）。
 *
 * <p>与既有 {@code ReportController}（T-701 审核签发）<b>共用 /report 前缀但互不影响</b>：
 * 旧控制器承载 /report/audit/*、/report/sign、/report/detail/{sampleId}；
 * 本控制器承载 /report/generate、/report/generate/pending 与 /report/detail（按样品编号查询）。
 * 拆分为独立类的目的：不触碰已验收链路，降低回归风险（施工约定）。</p>
 *
 * <p>权限标识 {@code report:generate}（seed sys_menu id=721，R100 已授权）。</p>
 */
@Validated
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportGenerateController {

    private final ReportGenerateService reportGenerateService;

    /** 8.2 分页查询可生成/可重打样品（status ∈ {S80, S90}）：GET /report/generate/pending */
    @GetMapping("/generate/pending")
    @PreAuthorize("hasAuthority('report:generate')")
    public R<PageResult<ReportPendingVO>> pendingGenerate(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName,
            @RequestParam(required = false) String taskNo) {
        return R.ok(reportGenerateService.pagePendingGenerate(current, size, sampleNo, sampleName, taskNo));
    }

    /** 生成检验报告（S80→S90）：POST /report/generate */
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('report:generate')")
    public R<ReportVO> generate(@Valid @RequestBody ReportGenerateDTO dto) {
        return R.ok(reportGenerateService.generate(dto));
    }

    /** 查询报告详情（供打印/重打，不改状态）：GET /report/detail?sampleNo=&reportType= */
    @GetMapping("/detail")
    @PreAuthorize("hasAuthority('report:generate')")
    public R<ReportVO> detail(@RequestParam String sampleNo,
                              @RequestParam(required = false) Integer reportType) {
        return R.ok(reportGenerateService.detail(sampleNo, reportType));
    }
}
