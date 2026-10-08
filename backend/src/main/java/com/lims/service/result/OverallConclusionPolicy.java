package com.lims.service.result;

import com.lims.common.enums.ResultConclusion;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 样品「整体结论」聚合策略（T-912 唯一口径 / F20 共享抽取）。
 *
 * <p><b>为什么抽成共享类</b>：整体结论的聚合规则原先私藏于
 * {@code ResultServiceImpl}。F20 合规改造后，审核裁决（{@code AuditServiceImpl}）
 * 在人工裁决待判定项后必须<b>用同一套规则</b>重算样品整体结论——若各写一份，
 * 两处逻辑迟早漂移，报告上就会出现「单项已定、整体仍待判定」的不一致。
 * 故把纯函数上移为无状态工具类，录入域与审核域共同委托，口径只有一处真相。</p>
 *
 * <p><b>聚合规则（顺序即优先级）</b>：
 * <pre>
 * 无非参考项（全部是参考项）           → 待判定（D3 补充，禁止自动判合格）
 * 存在非参考项未有效录入               → 待判定
 * 存在非参考项不合格                   → 不合格
 * 存在非参考项待判定/无结论            → 待判定
 * 其余（非参考项全部合格）             → 合格
 * </pre>
 * 参考项（{@code is_reference=1}）单项结论照常计算，但<b>不计入整体</b>——白名单 D3。</p>
 *
 * <p><b>无副作用</b>：仅读入参，不写库、不查库，可被单测穷举覆盖。</p>
 */
public final class OverallConclusionPolicy {

    /** 参考项标记（sample_item.is_reference = 1） */
    private static final int REFERENCE_YES = 1;

    private OverallConclusionPolicy() {
    }

    /**
     * 聚合样品整体结论（纯函数）。
     *
     * @param items   样品检测单项（含参考项）
     * @param results 以 {@code sample_item.id} 为键的结果行映射（缺失即视为未录入）
     * @return 整体结论：合格 / 不合格 / 待判定
     */
    public static ResultConclusion computeOverall(List<SampleItem> items, Map<Long, SampleResult> results) {
        List<SampleItem> nonReference = items.stream().filter(i -> !isReference(i)).toList();
        if (nonReference.isEmpty()) {
            // 全为参考项：整体结论交给人工（参考项不作放行依据）
            return ResultConclusion.PENDING;
        }
        boolean anyUnqualified = false;
        boolean anyPending = false;
        for (SampleItem item : nonReference) {
            SampleResult r = results.get(item.getId());
            // 未有效录入（无结果行 / 空值行）→ 未录齐 → 待判定
            if (!ResultEntryPolicy.isEntered(item.getJudgeType(), r)) {
                return ResultConclusion.PENDING;
            }
            ResultConclusion c = r.getConclusion();
            if (c == null || c == ResultConclusion.PENDING) {
                anyPending = true;
            } else if (c == ResultConclusion.UNQUALIFIED) {
                anyUnqualified = true;
            }
        }
        if (anyUnqualified) {
            return ResultConclusion.UNQUALIFIED;
        }
        return anyPending ? ResultConclusion.PENDING : ResultConclusion.QUALIFIED;
    }

    /** 是否为参考性限量项（sample_item.is_reference = 1） */
    public static boolean isReference(SampleItem item) {
        return Objects.equals(item.getIsReference(), REFERENCE_YES);
    }
}
