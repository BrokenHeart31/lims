package com.lims.common.enums;

/**
 * 下游失效类型（回退边 → 需处置的数据范围，设计 §2.8 / §5.1 RollbackScope）。
 *
 * <p>一次回退 = 「状态层反向补偿」+「数据层失效/留档/恢复」。本枚举回答
 * 「这条回退边需要处置哪些下游数据」，由 {@link RollbackEdgePolicy#invalidationScope} 给出。</p>
 */
public enum ArchiveTarget {

    /** 检测单项（sample_item）：分解明细失效（覆盖式重建的下游） */
    ITEM,

    /** 检验结果（sample_result）：结果失效（并避免指向已失效明细的孤儿引用） */
    RESULT,

    /** 任务安排字段（sample_item 的 assign_*）：清空指派，回到未安排 */
    ASSIGN_FIELDS,

    /**
     * 审核字段（sample_info 的 audit_*）：清空「当前有效值」。
     *
     * <p>⚠️ 无独立表需要失效——审核的**完整历史**在 `sample_audit_log`（只追加、不可改写）中，
     * 回退只清空 `sample_info` 上的「当前有效值」（避免退回后审核人仍出现在报告上），
     * 历史证据链不受影响。故此类型**不产生 sample_data_archive 记录**。</p>
     */
    AUDIT_FIELDS,

    /** 无下游数据（如 S20→S10、S60→S50） */
    NONE
}
