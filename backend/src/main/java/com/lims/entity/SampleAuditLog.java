package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.common.enums.AuditAction;
import com.lims.common.enums.SampleStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 样品审核签发流水（sample_audit_log，T-701）。
 *
 * <p><b>只追加、永不改写</b>——每一次审核通过 / 审核退回 / 签发各追加一行。
 * 它是 ALCOA+ 中 Audit Trail 的载体：谁（`operatedBy`）、何时（`operatedAt`）、
 * 把样品从什么状态（`fromStatus`）推到什么状态（`toStatus`）、给了什么意见（`opinion`）。</p>
 *
 * <p><b>与 `sample_info` 的分工</b>：流水表存**全部历史**；`sample_info.audit_by/audit_at/sign_by/sign_at`
 * 存**当前有效值**，供 T-702 报告合成直接取用（避免反查流水）。与 `confirmed_by/confirmed_at` 同一先例。</p>
 *
 * <p><b>退回也必须留痕</b>：审核退回是「否定」动作，比正向推进更需要证据——
 * 谁退回的、为什么退回，都在这张表里；`sample_info` 的 audit_* 同时清空，避免误出现在报告上。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_audit_log")
public class SampleAuditLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID（sample_info.id） */
    private Long sampleId;

    /** 样品编号（冗余，便于查询/报告打印） */
    private String sampleNo;

    /** 动作：1=审核通过 2=审核退回 3=签发 */
    private AuditAction action;

    /** 动作前样品状态 */
    private SampleStatus fromStatus;

    /** 动作后样品状态 */
    private SampleStatus toStatus;

    /** 意见 / 退回原因（退回时必填） */
    private String opinion;

    /**
     * 放行前是否已确认「异常项清单」（待判定 / 未录入）：
     * 0=否 1=是。落实 T-701 放行红线——存在异常项时不得静默放行。
     */
    private Integer abnormalConfirmed;

    /** 操作人工号 */
    private String operatedBy;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;

    /** 动作中文名（非持久化，出网供前端展示） */
    public String getActionLabel() {
        return action == null ? null : action.getLabel();
    }
}
