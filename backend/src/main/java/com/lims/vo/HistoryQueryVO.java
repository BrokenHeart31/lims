package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 历史样品查询行（T-801 A2，GET /api/query/history/page）。
 *
 * <p><b>范围</b>：{@code sample_info.status IN (80, 90)}（已签发 / 已出报告）。</p>
 *
 * <p>{@code auditBy}/{@code signBy} 出网为<b>姓名</b>（由工号经 sys_user 解析，取不到则回落工号本身）；
 * 原始工号字段 {@code auditByNo}/{@code signByNo} 仅供 Service 解析用，以 {@link JsonIgnore} 阻止出网。</p>
 */
@Data
public class HistoryQueryVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    private String inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    /** 样品状态 code（80=已签发 90=已出报告） */
    private Integer status;

    /** 状态中文名 */
    private String statusLabel;

    /** 整体结论 code（1=合格 2=不合格 3=待判定） */
    private Integer conclusion;

    /** 整体结论中文名 */
    private String conclusionLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 未录入项数（isEntered=false） */
    private Integer blankCount;

    /** 待判定项数（已录入但结论为「待判定」） */
    private Integer pendingCount;

    /** 异常项数 = 未录入 + 待判定（同 T-701 AuditDetailVO 口径） */
    private Integer abnormalCount;

    /** 审核人姓名（由 audit_by 工号解析） */
    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditAt;

    /** 签发人姓名（由 sign_by 工号解析） */
    private String signBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signAt;

    /** 报告类型 code（1=CMA 2=CMA-CATL，见 sample_info.report_type） */
    private Integer reportType;

    /** 报告类型中文名（由 common/enums/ReportType 取 label） */
    private String reportTypeLabel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportGeneratedAt;

    // ------------------------------------------------------------------
    // 内部原始字段（参与映射、不出网）
    // ------------------------------------------------------------------

    /** 审核人工号（原始） */
    @JsonIgnore
    private String auditByNo;

    /** 签发人工号（原始） */
    @JsonIgnore
    private String signByNo;
}
