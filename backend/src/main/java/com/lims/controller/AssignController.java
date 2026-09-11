package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.enums.SampleStatus;
import com.lims.dto.AssignAutoDTO;
import com.lims.dto.AssignConfirmDTO;
import com.lims.dto.AssignReassignDTO;
import com.lims.service.AssignService;
import com.lims.vo.AssignAutoResultVO;
import com.lims.vo.AssignDetailVO;
import com.lims.vo.AssignPendingVO;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检验任务安排接口（api-spec 第 5 章 /api/assign/*，T-501）。
 *
 * <p>权限标识与 seed sys_menu(id=51/52)、AGENTS 8.2 一致：
 * <ul>
 *   <li>{@code assign:confirm}：分页查询、自动分配、安排确认、明细查询</li>
 *   <li>{@code assign:reassign}：人工改派（要求更严，单独权限避免误操作）</li>
 * </ul>
 * </p>
 */
@Validated
@RestController
@RequestMapping("/assign")
@RequiredArgsConstructor
public class AssignController {

    private final AssignService assignService;

    /** 5.2 分页查询待安排样品（status=S30 且已分解出明细） */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('assign:confirm')")
    public R<PageResult<AssignPendingVO>> pending(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName) {
        return R.ok(PageResult.of(assignService.pagePending(current, size, sampleNo, sampleName)));
    }

    /** 5.3 查询样品安排明细（含指派结果与有资质候选） */
    @GetMapping("/detail/{sampleId}")
    @PreAuthorize("hasAuthority('assign:confirm')")
    public R<AssignDetailVO> detail(@PathVariable Long sampleId) {
        return R.ok(assignService.detail(sampleId));
    }

    /** 5.4 执行自动分配（可重跑；已人工改派的项不覆盖） */
    @PostMapping("/auto")
    @PreAuthorize("hasAuthority('assign:confirm')")
    public R<AssignAutoResultVO> autoAssign(@Valid @RequestBody AssignAutoDTO dto) {
        return R.ok(assignService.autoAssign(dto.getSampleId()));
    }

    /** 5.5 人工改派（仅允许有资质者） */
    @PostMapping("/reassign")
    @PreAuthorize("hasAuthority('assign:reassign')")
    public R<AssignDetailVO.Item> reassign(@Valid @RequestBody AssignReassignDTO dto) {
        return R.ok(assignService.reassign(dto.getItemId(), dto.getTesterNo()));
    }

    /** 5.6 安排确认：S30 → S40（要求全部单项已指派） */
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('assign:confirm')")
    public R<Map<String, Object>> confirm(@Valid @RequestBody AssignConfirmDTO dto) {
        int status = assignService.confirm(dto.getSampleId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sampleId", dto.getSampleId());
        data.put("status", status);
        data.put("statusLabel", SampleStatus.of(status).getLabel());
        return R.ok(data);
    }
}
