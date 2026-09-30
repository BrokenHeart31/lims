package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.RollbackExecuteDTO;
import com.lims.dto.RollbackHistoryQueryDTO;
import com.lims.dto.RollbackPreviewDTO;
import com.lims.dto.RollbackRecoverDTO;
import com.lims.service.RollbackService;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackBatchResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTargetsVO;
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
 * 流程回溯接口（api-spec 第 17 章 /api/rollback/*，feature B）。
 *
 * <p>2026-09-30：「流程回溯」**不再是一个独立功能区**（独立页面/路由/侧栏菜单已移除），
 * 回退能力下沉到各业务环节页面内嵌使用——哪个环节能回退，入口就只出现在哪个环节。
 * 因此本章接口是**被各业务页复用的能力接口**，而不是某个页面的专属后端。</p>
 *
 * <p>权限标识与 seed sys_menu 一致（菜单节点 13 已删除，权限位保留为隐藏点位）：
 * <ul>
 *   <li>{@code rollback:view}：目标步查询 / 时间线 / 预览 / 回退记录查询；</li>
 *   <li>{@code rollback:execute}：执行回退 / 撤销回退。</li>
 * </ul>
 * 敏感链路（含 S70→S60）所需 {@code rollback:sensitive} 在**服务层**二次校验——
 * 它取决于「这次退的是哪条链」，无法用一个注解表达，故不在此处 {@code @PreAuthorize}。</p>
 */
@Validated
@RestController
@RequestMapping("/rollback")
@RequiredArgsConstructor
public class RollbackController {

    private final RollbackService rollbackService;

    /** B1 该样品全链路事件时间线（正向 + 逆向，供留痕抽屉 / 回退确认框） */
    @GetMapping("/timeline/{sampleId}")
    @PreAuthorize("hasAuthority('rollback:view')")
    public R<RollbackTimelineVO> timeline(@PathVariable Long sampleId) {
        return R.ok(rollbackService.timeline(sampleId));
    }

    /**
     * B7 该样品**可达的全部回退目标步** + 每步的下游影响预览（纯读、不落库）。
     *
     * <p>环节内嵌入口的核心读接口：点一次「回退」即拿到「能退到哪几步、退到每步会动什么」，
     * 避免「查时间线 + 逐个 preview」的 N+1 请求与两处口径漂移。</p>
     */
    @GetMapping("/targets/{sampleId}")
    @PreAuthorize("hasAuthority('rollback:view')")
    public R<RollbackTargetsVO> targets(@PathVariable Long sampleId) {
        return R.ok(rollbackService.targets(sampleId));
    }

    /** B2 单个目标步的「下游影响预览」（纯读不落库；被拒目标返回 allowed=false + code/msg） */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('rollback:view')")
    public R<RollbackPreviewVO> preview(@Valid @RequestBody RollbackPreviewDTO dto) {
        return R.ok(rollbackService.preview(dto));
    }

    /**
     * B3 执行回退（支持**可选目标步**与**批量** `ids`）。
     *
     * <p>权限仍是 {@code rollback:execute}；含 S70→S60 的**敏感链路**由服务层二次校验
     * {@code rollback:sensitive} + 二次确认。批量采用逐条独立事务，响应中逐条给出成功/失败原因。</p>
     */
    @PostMapping("/execute")
    @PreAuthorize("hasAuthority('rollback:execute')")
    public R<RollbackBatchResultVO> execute(@Valid @RequestBody RollbackExecuteDTO dto) {
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
