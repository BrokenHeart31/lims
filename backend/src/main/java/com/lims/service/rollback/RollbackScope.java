package com.lims.service.rollback;

import com.lims.common.enums.ArchiveTarget;
import com.lims.common.enums.RollbackEdgePolicy;
import com.lims.common.enums.RollbackGroup;
import com.lims.common.enums.SampleStatus;

import java.util.List;
import java.util.Set;

/**
 * 回退边 → 下游失效范围映射（纯函数，feature B，设计 §5.1）。
 *
 * <p>本类是 {@link RollbackEdgePolicy#invalidationScope} 的**组合语义门面**：
 * 失效范围的唯一权威仍在 {@code common/enums/RollbackEdgePolicy}（保持 common 层零外部依赖，
 * 避免 common → service 反向依赖）。此处只把「边策略」与「分组」组合成一个纯函数，
 * 供 {@link RollbackPlanner} 与 {@code RollbackServiceImpl} 统一调用，避免两处各算一遍。</p>
 */
public final class RollbackScope {

    private RollbackScope() {
    }

    /**
     * 该回退边需处置的下游数据范围。
     *
     * @param group 回退分组（校验用；实际范围由 from→to 决定）
     * @param from  回退前状态
     * @param to    回退后状态
     */
    public static Set<ArchiveTarget> targets(RollbackGroup group, SampleStatus from, SampleStatus to) {
        return RollbackEdgePolicy.invalidationScope(from, to);
    }

    /**
     * 一条**跨级链**的整体失效范围 = 各步失效范围的并集（见
     * {@link RollbackEdgePolicy#chainScope}）。
     *
     * <p>使用场景：跨级回退的「影响预览」必须一次性告诉用户「整条链会动到哪些数据」，
     * 而不是只报最后一级；预览与执行必须来自同一权威，故二者都调本方法。</p>
     *
     * @param from  回退起点状态
     * @param chain 逐级链（{@link RollbackEdgePolicy#chain} 的返回值）
     */
    public static Set<ArchiveTarget> chainTargets(SampleStatus from, List<SampleStatus> chain) {
        return RollbackEdgePolicy.chainScope(from, chain);
    }

    /** 是否需失效检测明细 */
    public static boolean invalidatesItems(Set<ArchiveTarget> targets) {
        return targets.contains(ArchiveTarget.ITEM);
    }

    /** 是否需失效检验结果 */
    public static boolean invalidatesResults(Set<ArchiveTarget> targets) {
        return targets.contains(ArchiveTarget.RESULT);
    }

    /** 是否需清空任务指派字段 */
    public static boolean resetsAssignFields(Set<ArchiveTarget> targets) {
        return targets.contains(ArchiveTarget.ASSIGN_FIELDS);
    }
}
