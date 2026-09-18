package com.lims.controller;

import com.lims.common.R;
import com.lims.dto.ReportVoidDTO;
import com.lims.service.ReportVoidService;
import com.lims.vo.ReportVoidResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告作废 / 召回接口（api-spec 第 18 章 /api/report/void，feature B，T02）。
 *
 * <p>独立于 {@code ReportController}：作废/召回是**治理动作**（不改 status、需
 * {@code report:void} 权限，seed sys_menu id=134），与审核/签发（改 status）语义不同，
 * 独立控制器让权限与路由一眼可辨。</p>
 */
@Validated
@RestController
@RequestMapping("/report/void")
@RequiredArgsConstructor
public class ReportVoidController {

    private final ReportVoidService reportVoidService;

    /** B6 已签发 / 已出报告 作废 / 召回（不改 status，只写标记 + 流水） */
    @PostMapping
    @PreAuthorize("hasAuthority('report:void')")
    public R<ReportVoidResultVO> voidReport(@Valid @RequestBody ReportVoidDTO dto) {
        return R.ok(reportVoidService.voidReport(dto));
    }
}
