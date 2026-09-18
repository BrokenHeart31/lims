package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.RollbackExecuteDTO;
import com.lims.dto.RollbackHistoryQueryDTO;
import com.lims.dto.RollbackPreviewDTO;
import com.lims.dto.RollbackRecoverDTO;
import com.lims.service.RollbackService;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTimelineVO;
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
 * 流程回溯接口（api-spec 第 17 章 /api/rollback/*，feature B，T02）。
 *
 * <p>权限标识与 seed sys_menu(id=131/132/133) 一致：
 * <ul>
 *   <li>{@code rollback:view}：时间线 / 预览 / 回退记录查询（131）；</li>
 *   <li>{@code rollback:execute}：执行回退 / 恢复（132）。</li>
 * </ul>
 * 敏感边（S70→S60）所需 {@code rollback:sensitive}（133）在**服务层**二次校验——
 * 它取决于「这条边是哪条边」，无法用一个注解表达，故不在此处 {@code @PreAuthorize}。</p>
 */
@Validated
@RestController
@RequestMapping("/rollback")
@RequiredArgsConstructor
public class RollbackController {

    private final RollbackService rollbackService;

    /** B1 该样品全链路事件时间线（正向 + 逆向，供回溯面板） */
    @GetMapping("/timeline/{sampleId}")
    @PreAuthorize("hasAuthority('rollback:view')")
    public R<RollbackTimelineVO> timeline(@PathVariable Long sampleId) {
        return R.ok(rollbackService.timeline(sampleId));
    }

    /** B2 回退前「下游影响预览」（纯读不落库；被拒边返回 allowed=false + code/msg） */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('rollback:view')")
    public R<RollbackPreviewVO> preview(@Valid @RequestBody RollbackPreviewDTO dto) {
        return R.ok(rollbackService.preview(dto));
    }

    /** B3 执行一次逐级回退（敏感边再由服务层校验 rollback:sensitive + 二次确认） */
    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('rollback:execute')")
    public R<RollbackActionResultVO> execute(@Valid @RequestBody RollbackExecuteDTO dto) {
        return R.ok(rollbackService.execute(dto));
    }

    /** B4 恢复某次未产生新下游数据的回退 */
    @PostMapping("/recover")
    @PreAuthorize("hasAuthority('rollback:execute')")
    public R<RollbackActionResultVO> recover(@Valid @RequestBody RollbackRecoverDTO dto) {
        return R.ok(rollbackService.recover(dto));
    }

    /** B5 分页查询回退记录（跨样品） */
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('rollback:view')")
    public R<PageResult<RollbackHistoryVO>> history(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            RollbackHistoryQueryDTO query) {
        return R.ok(rollbackService.history(current, size, query));
    }
}
