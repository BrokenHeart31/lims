package com.lims.controller;

import com.lims.common.R;
import com.lims.service.StatService;
import com.lims.vo.StatNameValueVO;
import com.lims.vo.StatOverviewVO;
import com.lims.vo.StatTrendVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 质量分析统计接口（api-spec 第 14 章 /api/stat/*，T-803）。
 *
 * <p>权限：全部接口统一 {@code stat:view}（AGENTS 8.2 已同步）。这是一个独立的权限域——
 * 「看趋势」与「找样本」（query:*）是两类不同的信息需求，复用会造成权限语义混淆
 * （DECISIONS 2026-09-13）。</p>
 *
 * <p><b>返回的都是真实聚合值</b>：无数据时给 0 / 空列表 / {@code qualifiedRate=null}，
 * 绝不返回 mock 数据——空数据的正确表达是「空图表 + 空状态提示」，
 * 编造的演示数据会让使用单位误判系统已在工作（DECISIONS 已落档）。</p>
 */
@Validated
@RestController
@RequestMapping("/stat")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    /** H1 总览卡片（样品总数/在检/已完成/报告数/合格率） */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<StatOverviewVO> overview() {
        return R.ok(statService.overview());
    }

    /** H2 样品状态分布（已翻译中文阶段名 + 百分比） */
    @GetMapping("/sample-status")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> sampleStatus() {
        return R.ok(statService.sampleStatusDistribution());
    }

    /** H3 检验类别分布（如「监督抽检」） */
    @GetMapping("/inspect-type")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> inspectType() {
        return R.ok(statService.inspectTypeDistribution());
    }

    /** H4 受检单位样品量 Top N */
    @GetMapping("/top-clients")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> topClients(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return R.ok(statService.topClients(limit));
    }

    /** H5 食品大类检测量 Top N */
    @GetMapping("/category")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> category(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return R.ok(statService.categoryDistribution(limit));
    }

    /** H6 检验员任务量 Top N（工号 → 姓名已翻译） */
    @GetMapping("/tester-workload")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> testerWorkload(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return R.ok(statService.testerWorkload(limit));
    }

    /** H7 部门样品量分布 */
    @GetMapping("/dept")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> dept() {
        return R.ok(statService.deptDistribution());
    }

    /** H8 不合格项目 Top N */
    @GetMapping("/unqualified-items")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatNameValueVO>> unqualifiedItems(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return R.ok(statService.topUnqualifiedItems(limit));
    }

    /** H9 月度趋势（近 N 个月，含本月，已补零月） */
    @GetMapping("/monthly-trend")
    @PreAuthorize("hasAuthority('stat:view')")
    public R<List<StatTrendVO>> monthlyTrend(
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months) {
        return R.ok(statService.monthlyTrend(months));
    }
}
