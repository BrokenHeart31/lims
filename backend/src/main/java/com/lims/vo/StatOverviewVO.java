package com.lims.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 质量分析总览卡片（api-spec 第 14 章，T-803）。
 *
 * <p>每个数值都是**真实聚合查询**的结果，无任何 mock / 占位常数
 * （见 DECISIONS 2026-09-13「禁 mock 假数据」）。</p>
 */
@Data
public class StatOverviewVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 样品总数（未删除） */
    private long totalSamples;

    /** 在检样品数（status 10..70） */
    private long testingSamples;

    /** 已完成样品数（status >= 80，即已签发及以上） */
    private long completedSamples;

    /** 检测单项总数（未删除） */
    private long totalItems;

    /** 已出具报告数（report_generated_at 非空） */
    private long reportCount;

    /** 整体结论已判定的样品数（conclusion 非空） */
    private long judgedSamples;

    /** 合格样品数（conclusion=1） */
    private long qualifiedSamples;

    /** 不合格样品数（conclusion=2） */
    private long unqualifiedSamples;

    /**
     * 合格率（%）= 合格 / (合格 + 不合格) × 100，保留一位小数。
     *
     * <p><b>分母刻意排除「待判定」</b>：待判定是数据缺口（缺检出限/缺标准文本）而非质量结论，
     * 计入分母会凭空拉低合格率，误导管理判断。无有效结论时返回 {@code null}
     * （前端显示「暂无数据」而不是 0%，两者含义不同）。</p>
     */
    private Double qualifiedRate;

    /** 待判定样品数（conclusion=3） */
    private long pendingSamples;
}
