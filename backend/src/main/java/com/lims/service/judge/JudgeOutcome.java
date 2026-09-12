package com.lims.service.judge;

import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;

/**
 * 判定引擎输出（T-601）。
 *
 * <p>「结论 + 来源 + 判定依据说明」三件套必须**同时**产出：结论用于聚合与报告，
 * 依据说明用于人工复核与审计（ALCOA+ 的 Legible/Accurate——报告上的判定必须能被读懂、被回放）。</p>
 *
 * @param conclusion       单项结论（闭集：合格 / 不合格 / 待判定）
 * @param source           结论来源（自动判定 / 人工判定）
 * @param basis            判定依据说明（人可读，落库 {@code sample_result.judge_basis}）
 * @param requiresAttention 是否需人工关注（未录入 / 白名单外输入 / 依据不足）。
 *                          为 {@code true} 时引擎必记 WARN 日志，是「禁止静默判合格」的可测抓手
 *                          （见 {@code JudgeEngineTest} 的日志断言）。
 */
public record JudgeOutcome(ResultConclusion conclusion,
                           ConclusionSource source,
                           String basis,
                           boolean requiresAttention) {

    public static JudgeOutcome engine(ResultConclusion conclusion, String basis) {
        return new JudgeOutcome(conclusion, ConclusionSource.ENGINE, basis, false);
    }

    public static JudgeOutcome manual(ResultConclusion conclusion, String basis) {
        return new JudgeOutcome(conclusion, ConclusionSource.MANUAL, basis, false);
    }

    /**
     * 白名单外输入 / 依据不足的统一出口：待判定 + 自动判定来源 + 原因说明 + 需关注标记。
     * 调用方（{@link JudgeEngine}）同时记 WARN 日志。
     */
    public static JudgeOutcome pending(String basis) {
        return new JudgeOutcome(ResultConclusion.PENDING, ConclusionSource.ENGINE, basis, true);
    }
}
