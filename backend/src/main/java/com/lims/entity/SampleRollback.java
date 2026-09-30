package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.common.enums.RollbackGroup;
import com.lims.common.enums.SampleStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 样品回退记录（`sample_rollback`，feature B，设计 §3.1.2）。
 *
 * <p>回答 PRD B-03：一次回退的「谁 / 何时 / 从哪到哪 / 原因 / 是否可再撤销」。
 * `can_recover/recovered` 构成**可恢复状态机**（PRD B-07）：未产生新下游数据的回退可原路恢复。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_rollback")
public class SampleRollback extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID */
    private Long sampleId;

    /** 样品编号 */
    private String sampleNo;

    /**
     * 回退批次号（一次回退操作 = 一个批次）。
     *
     * <p>跨级回退时本表**只落 1 行**（记录起点与最终目标步），逐级过程由
     * `sample_status_log` 的 N 条 `event_type=4` 流水承载，二者以
     * `(batch_no, rollback_id)` 关联——这就是「1 批次 + 每级 1 条流水」不变式的落地形态。</p>
     */
    private String batchNo;

    /** 回退前状态 code */
    private SampleStatus fromStatus;

    /** 回退后状态 code（跨级回退时为**最终目标步**；中间落点见状态流水） */
    private SampleStatus toStatus;

    /** 本次回退的级数（1 = 单级逐级回退；>1 = 跨级链式回退） */
    private Integer stepCount;

    /** 回退分组（1 常规 / 2 敏感） */
    private RollbackGroup edgeGroup;

    /** 回退原因（必填） */
    private String reason;

    /** 是否完成二次确认 0=否 1=是 */
    private Integer secondConfirmed;

    /** 下游失效清单摘要（JSON 文本） */
    private String invalidatedSummary;

    /** 失效的 sample_item 数 */
    private Integer affectedItemCount;

    /** 失效的 sample_result 数 */
    private Integer affectedResultCount;

    /** 被回退覆盖的 sample_info 字段快照（JSON 文本，用于恢复） */
    private String restoredSampleJson;

    /** 是否可再撤销 0=否（已产生新下游数据）1=是 */
    private Integer canRecover;

    /** 是否已被恢复 0=否 1=是 */
    private Integer recovered;

    /** 恢复操作人 */
    private String recoverBy;

    /** 恢复时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recoverAt;

    /** 回退操作人 */
    private String operatedBy;

    /** 回退时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;

    /** 回退分组中文名（非持久化，出网供前端展示） */
    public String getEdgeGroupLabel() {
        return edgeGroup == null ? null : edgeGroup.getLabel();
    }

    /** 回退前状态中文名（非持久化） */
    public String getFromStatusLabel() {
        return fromStatus == null ? null : fromStatus.getLabel();
    }

    /** 回退后状态中文名（非持久化） */
    public String getToStatusLabel() {
        return toStatus == null ? null : toStatus.getLabel();
    }
}
