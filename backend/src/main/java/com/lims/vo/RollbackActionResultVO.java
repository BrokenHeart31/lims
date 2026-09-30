package com.lims.vo;

import lombok.Data;

/**
 * 回退 / 恢复执行结果（api-spec B3/B4，feature B）。
 */
@Data
public class RollbackActionResultVO {

    private Long sampleId;

    private String sampleNo;

    /** 执行后的样品状态 code */
    private Integer status;

    private String statusLabel;

    /** 本次回退记录 id（恢复接口回传被恢复的 rollbackId） */
    private Long rollbackId;

    /** 本次回退批次号（跨级回退时各级流水共用；与前端的「一次操作」一一对应） */
    private String batchNo;

    /** 本次回退实际执行的级数（1 = 单级；>1 = 跨级链式） */
    private Integer stepCount;

    /** 链路展示文案（如「已安排 → 已分解 → 已登记」） */
    private String chainText;

    /** 本次回退是否仍可再撤销（未产生新下游数据时为 true） */
    private Boolean canRecover;

    /** 失效的 sample_item 数 */
    private Integer affectedItemCount;

    /** 失效的 sample_result 数 */
    private Integer affectedResultCount;

    /** 下游失效清单摘要 */
    private String invalidatedSummary;
}
