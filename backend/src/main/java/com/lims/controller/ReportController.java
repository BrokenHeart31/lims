package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.AuditApproveDTO;
import com.lims.dto.AuditReturnDTO;
import com.lims.dto.ReportSignDTO;
import com.lims.service.AuditService;
import com.lims.vo.AuditActionVO;
import com.lims.vo.AuditDetailVO;
import com.lims.vo.AuditPendingVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 检验报告审核 / 签发接口（api-spec 第 7 章 /api/report/*，T-701）。
 *
 * <p>权限标识与 seed sys_menu（id=711/712）、AGENTS 8.2 严格一致：
 * <ul>
 *   <li>{@code report:audit}：审核列表 / 明细 / 审核通过 / 审核退回</li>
 *   <li>{@code report:sign}：待签发列表 / 签发</li>
 * </ul>
 * 按 AGENTS 8.1，审核与签发属 R100（综合管理）；明细查询两个权限任一即可
 * （签发人同样需要看到单项数据与异常项清单）。</p>
 */
@Validated
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final AuditService auditService;

    /** 7.2 分页查询待审核样品（status=S60 检验完成） */
    @GetMapping("/audit/pending")
    @PreAuthorize("hasAuthority('report:audit')")
    public R<PageResult<AuditPendingVO>> pendingAudit(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName) {
        return R.ok(auditService.pagePendingAudit(current, size, sampleNo, sampleName));
    }

    /** 7.2 分页查询待签发样品（status=S70 已审核） */
    @GetMapping("/sign/pending")
    @PreAuthorize("hasAuthority('report:sign')")
    public R<PageResult<AuditPendingVO>> pendingSign(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName) {
        return R.ok(auditService.pagePendingSign(current, size, sampleNo, sampleName));
    }

    /** 7.3 审核/签发明细（含全部单项结果、判定依据、异常项清单、审核流水） */
    @GetMapping("/detail/{sampleId}")
    @PreAuthorize("hasAnyAuthority('report:audit','report:sign')")
    public R<AuditDetailVO> detail(@PathVariable Long sampleId) {
        return R.ok(auditService.detail(sampleId));
    }

    /** 7.4 审核通过：S60 → S70（存在异常项时必须显式确认） */
    @PostMapping("/audit/approve")
    @PreAuthorize("hasAuthority('report:audit')")
    public R<AuditActionVO> approve(@Valid @RequestBody AuditApproveDTO dto) {
        return R.ok(auditService.approve(dto));
    }

    /** 7.5 审核退回：S60 → S50（独立退回白名单，原因必填） */
    @PostMapping("/audit/return")
    @PreAuthorize("hasAuthority('report:audit')")
    public R<AuditActionVO> returnToTester(@Valid @RequestBody AuditReturnDTO dto) {
        return R.ok(auditService.returnToTester(dto));
    }

    /** 7.6 签发：S70 → S80 */
    @PostMapping("/sign")
    @PreAuthorize("hasAuthority('report:sign')")
    public R<AuditActionVO> sign(@Valid @RequestBody ReportSignDTO dto) {
        return R.ok(auditService.sign(dto));
    }
}
