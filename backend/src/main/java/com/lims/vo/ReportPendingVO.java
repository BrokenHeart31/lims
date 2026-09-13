package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报告生成列表行（api-spec 8.2，T-702）。
 *
 * <p>收录 status ∈ {S80 已签发, S90 已出报告} 的样品：S80 用于**首次生成**，
 * S90 用于**重打印**（reportGeneratedAt 非空即已生成）。</p>
 *
 * <p>状态/结论中文一律取枚举 {@code getLabel()}，前端不维护 code→label 字典。</p>
 */
@Data
public class ReportPendingVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    private String inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    /** 样品状态 code：80=已签发 90=已出报告 */
    private Integer status;

    private String statusLabel;

    /** 整体结论 code：1=合格 2=不合格 3=待判定 */
    private Integer conclusion;

    private String conclusionLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 审核人工号（当前有效值） */
    private String auditBy;

    /** 签发人工号 */
    private String signBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signAt;

    /** 报告类型 code（未生成时为 null） */
    private Integer reportType;

    private String reportTypeLabel;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reportGeneratedAt;
}
