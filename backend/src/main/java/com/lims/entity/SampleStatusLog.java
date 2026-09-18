package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 样品状态流水（`sample_status_log`，feature B 核心新增，设计 §3.1.1）。
 *
 * <p><b>定位</b>：把「样品状态变更」这件事的**全部事件**（正向 + 逆向 + 回退 + 恢复 + 作废 + 报告）
 * 收敛成一条可精确回答「谁 / 何时 / 从哪到哪 / 为何 / 入口」的**追加型**事件流。</p>
 *
 * <p><b>推翻旧自裁</b>：本表的诞生推翻了 DECISIONS 2026-09-13
 * 「不新建状态流水表、用既有字段近似推导」——回退要求「从哪到哪」可精确查询，近似推导已不成立。</p>
 *
 * <p><b>只追加、永不改写</b>：无 UPDATE / DELETE 入口（检验机构审计要求）。
 * 口径与 {@link SampleAuditLog} 对齐（from_status/to_status/operated_by/operated_at/reason）；
 * audit 域动作**双写**（`sample_audit_log` 保持不变，报告/打印/既有读路径零改动）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_status_log")
public class SampleStatusLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID（sample_info.id） */
    private Long sampleId;

    /** 样品编号（冗余，便于查询） */
    private String sampleNo;

    /** 事件类型（1 正向 / 2 退回 / 3 签发 / 4 回退 / 5 恢复 / 6 作废 / 7 报告） */
    private StatusEventType eventType;

    /** 变更前状态 code */
    private SampleStatus fromStatus;

    /** 变更后状态 code */
    private SampleStatus toStatus;

    /** 人类可读动作（如「登记确认」「审核退回」「回退至已安排」） */
    private String actionLabel;

    /** 原因（回退/退回/作废必填，正向可空） */
    private String reason;

    /** 关联 sample_rollback.id（回退/恢复事件） */
    private Long rollbackId;

    /** 下游数据处置摘要（如「失效 12 项结果、3 项明细」） */
    private String dataDisposition;

    /** 入口/来源：SAMPLE/ITEM/ASSIGN/RESULT/AUDIT/REPORT/ROLLBACK_PANEL */
    private String source;

    /** 操作人工号 */
    private String operatedBy;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;

    /** 事件类型中文名（非持久化，出网供前端展示） */
    public String getEventTypeLabel() {
        return eventType == null ? null : eventType.getLabel();
    }

    /** 变更前状态中文名（非持久化，出网供前端展示） */
    public String getFromStatusLabel() {
        return fromStatus == null ? null : fromStatus.getLabel();
    }

    /** 变更后状态中文名（非持久化，出网供前端展示） */
    public String getToStatusLabel() {
        return toStatus == null ? null : toStatus.getLabel();
    }
}
