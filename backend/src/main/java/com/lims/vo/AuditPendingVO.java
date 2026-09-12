package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待审核 / 待签发样品行（api-spec 7.2，T-701）。
 *
 * <p>同一结构服务两个列表：待审核（status=S60）与待签发（status=S70）——
 * 后者才有 {@code auditBy/auditAt}。</p>
 */
@Data
public class AuditPendingVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    /** 受检单位 */
    private String clientName;

    /** 关联监抽任务编号 */
    private String taskNo;

    /** 检验类别 */
    private String inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    /** 样品状态 code：60=检验完成（待审核） / 70=已审核（待签发） */
    private Integer status;

    private String statusLabel;

    /** 整体结论 code（1 合格 / 2 不合格 / 3 待判定） */
    private Integer conclusion;

    private String conclusionLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 已有效录入项数 */
    private Integer enteredCount;

    /** 异常项数（未录入 + 待判定），审核放行前必须确认 */
    private Integer abnormalCount;

    // ---- 待签发列表才有 ----

    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditAt;

    private String auditOpinion;
}
