package com.lims.service.judge;

/**
 * 判定引擎输入（T-601）。
 *
 * <p>全部字段来自 {@code sample_item} 的**快照下沉字段** + 检验员录入的原始值，
 * <b>不包含任何标准库回溯</b>（AGENTS 7.3 / 白名单定稿 D5 追认）。</p>
 *
 * @param judgeType        判定类型：1=限量比较 2=不得检出/不得使用 3=文本/感官人工
 * @param stdValue         标准值文本（白名单 5 形态：纯数值 / ≤数值 / 不得检出 / 不得使用 / --）
 * @param lowerLimit       最低检出限文本（可空）
 * @param testValue        检验员录入的原始值（数值文本 或 未检出 形态；可空）
 * @param manualConclusion 人工结论 code（仅 judgeType=3 时使用：1=合格 2=不合格；可空）
 */
public record JudgeInput(
        Integer judgeType,
        String stdValue,
        String lowerLimit,
        String testValue,
        Integer manualConclusion) {
}
