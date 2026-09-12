package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.ResultJudgeDTO;
import com.lims.dto.ResultSaveDTO;
import com.lims.dto.ResultSubmitDTO;
import com.lims.service.ResultService;
import com.lims.vo.ResultDetailVO;
import com.lims.vo.ResultJudgeVO;
import com.lims.vo.ResultPendingVO;
import com.lims.vo.ResultSaveVO;
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

/**
 * 检验数据录入 / 自动判定接口（api-spec 第 6 章 /api/result/*，T-601）。
 *
 * <p>权限标识与 seed sys_menu(id=61)、AGENTS 8.2 一致：{@code result:entry}（数据录入）。
 * 导出权限 {@code result:export-excel}（id=62）归 T-801/T-802，不在本域。</p>
 */
@Validated
@RestController
@RequestMapping("/result")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    /** 6.2 分页查询待录入样品（status ∈ {S40 已安排, S50 检验中}） */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('result:entry')")
    public R<PageResult<ResultPendingVO>> pending(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) long size,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName) {
        return R.ok(resultService.pagePending(current, size, sampleNo, sampleName));
    }

    /** 6.2 查询样品录入明细（含判定依据快照 + 已录入结果 + 录入进度） */
    @GetMapping("/detail/{sampleId}")
    @PreAuthorize("hasAuthority('result:entry')")
    public R<ResultDetailVO> detail(@PathVariable Long sampleId) {
        return R.ok(resultService.detail(sampleId));
    }

    /** 6.3 实时判定预览（纯计算不落库） */
    @PostMapping("/judge")
    @PreAuthorize("hasAuthority('result:entry')")
    public R<ResultJudgeVO> judge(@Valid @RequestBody ResultJudgeDTO dto) {
        return R.ok(resultService.judgePreview(dto.getItemId(), dto.getTestValue(), dto.getManualConclusion()));
    }

    /** 6.4 保存录入结果（可分次；首次保存 S40→S50） */
    @PutMapping("/save")
    @PreAuthorize("hasAuthority('result:entry')")
    public R<ResultSaveVO> save(@Valid @RequestBody ResultSaveDTO dto) {
        return R.ok(resultService.save(dto));
    }

    /** 6.5 提交（全部录齐 → S50→S60） */
    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('result:entry')")
    public R<ResultSaveVO> submit(@Valid @RequestBody ResultSubmitDTO dto) {
        return R.ok(resultService.submit(dto.getSampleId()));
    }
}
