package com.lims.service.ai;

/**
 * 样品检验单项只读投影（feature 增量 ai_flow_assistant，T02，设计 §2.3 / §5.1）。
 *
 * <p>{@code ValueAnchorAssembler} 用它取「该样品当前项目的标准值快照」，从而**只读**投影即可
 * 装配数值对齐卡片——不必引 {@code SampleItemMapper}（后者含失效/回滚写方法）。</p>
 *
 * <p><b>不暴露任何判定结论字段</b>（{@code conclusion} 等一律不在投影内）。</p>
 */
public record SampleItemFact(
        Long id,
        String itemName,
        String basisCode,
        String stdValue,
        String unit,
        Integer judgeType,
        Integer isReference) {
}
