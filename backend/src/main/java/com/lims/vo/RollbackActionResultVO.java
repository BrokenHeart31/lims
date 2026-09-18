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

    /** 本次回退是否仍可再撤销（未产生新下游数据时为 true） */
    private Boolean canRecover;

    /** 失效的 sample_item 数 */
    private Integer affectedItemCount;

    /** 失效的 sample_result 数 */
    private Integer affectedResultCount;

    /** 下游失效清单摘要 */
    private String invalidatedSummary;
}
