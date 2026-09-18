package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.common.enums.SampleStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报告作废 / 召回记录（`report_void`，feature B，设计 §3.1.4）。
 *
 * <p>S80（已签发）/ S90（已出报告）的**专门治理动作**：报告已对外生效（PRD Q2 假设），
 * 因此**不改 status**，只在 `sample_info.void_status` 上打标记（1=已作废 2=已召回），
 * 并追加一条 `sample_status_log(event_type=6)`，保留原签发记录（PRD T4）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("report_void")
public class ReportVoid extends BaseEntity {

    /** 作废类型：作废 */
    public static final int TYPE_VOID = 1;
    /** 作废类型：召回 */
    public static final int TYPE_RECALL = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID */
    private Long sampleId;

    /** 样品编号 */
    private String sampleNo;

    /** 类型 1=作废 2=召回 */
    private Integer voidType;

    /** 操作时样品状态（80/90） */
    private SampleStatus statusAtVoid;

    /** 强理由（必填） */
    private String reason;

    /** 是否二次确认 0=否 1=是 */
    private Integer secondConfirmed;

    /** 操作人工号 */
    private String operatedBy;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operatedAt;

    /** 类型中文名（非持久化）：1=作废 2=召回 */
    public String getVoidTypeLabel() {
        if (voidType == null) {
            return null;
        }
        return voidType == TYPE_VOID ? "作废" : voidType == TYPE_RECALL ? "召回" : "未知";
    }

    /** 操作时状态中文名（非持久化） */
    public String getStatusAtVoidLabel() {
        return statusAtVoid == null ? null : statusAtVoid.getLabel();
    }
}
