package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 在检样品查询行（T-801 A2，GET /api/query/testing/page）。
 *
 * <p><b>范围</b>：{@code sample_info.status IN (10..70)}（未出报告的在检样品）。
 * 除样品头信息外，还带出「检测进度」（itemTotal/enteredCount/pendingCount/abnormalCount）、
 * 「当前处理人」（currentHandler）与「当前状态停留时长」（stageEnteredAt/stageStayHours）。</p>
 *
 * <p><b>派生字段一律由 Service 计算</b>：进度复用 {@code ResultEntryPolicy.isEntered}（唯一权威口径），
 * 处理人 / 停留时长按既有字段近似推导（不新建状态流水表）。为支持推导，
 * 下面若干「内部原始字段」参与 MyBatis 映射但以 {@link JsonIgnore} 阻止出网，避免污染契约列。</p>
 */
@Data
public class TestingQueryVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    /** 受检单位 */
    private String clientName;

    private String taskNo;

    /** 检验类别（如 监督抽检） */
    private String inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    /** 样品状态 code（10..70，见 common/enums/SampleStatus） */
    private Integer status;

    /** 状态中文名（Service 由 SampleStatus 枚举取值，禁止硬编码） */
    private String statusLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 已有效录入项数（口径 = ResultEntryPolicy.isEntered） */
    private Integer enteredCount;

    /** 未录入项数（isEntered=false；即 itemTotal - enteredCount） */
    private Integer blankCount;

    /** 待判定项数（已录入但结论为「待判定」） */
    private Integer pendingCount;

    /** 异常项数 = 未录入 + 待判定（同 T-701 AuditDetailVO 口径） */
    private Integer abnormalCount;

    /** 整体结论 code（1=合格 2=不合格 3=待判定；可能为 null） */
    private Integer conclusion;

    /** 整体结论中文名 */
    private String conclusionLabel;

    /** 当前处理人（按状态推导，见 Service） */
    private String currentHandler;

    /** 进入当前阶段的时间（按状态用既有字段近似推导） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stageEnteredAt;

    /** 在当前阶段的停留小时数（一位小数，由 stageEnteredAt 至当前时刻换算） */
    private Double stageStayHours;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // ------------------------------------------------------------------
    // 内部原始字段（参与映射、不出网）：供 Service 推导处理人 / 阶段时间
    // ------------------------------------------------------------------

    /** 登记确认人工号（S10/S20 推导当前处理人用） */
    @JsonIgnore
    private String confirmedBy;

    /** 审核人工号（S70 推导当前处理人用） */
    @JsonIgnore
    private String auditBy;

    /** 创建时间（S10 阶段起始） */
    @JsonIgnore
    private LocalDateTime createdAt;

    /** 登记确认时间（S20 阶段起始） */
    @JsonIgnore
    private LocalDateTime confirmedAt;

    /** 审核时间（S70 阶段起始） */
    @JsonIgnore
    private LocalDateTime auditAt;
}
