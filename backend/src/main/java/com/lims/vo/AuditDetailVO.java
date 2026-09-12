package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核/签发明细（api-spec 7.3，T-701）。
 *
 * <p>审核人做放行判断所需的全部信息：样品摘要 + 整体结论 + **全部单项结果与判定依据**
 * + **异常项清单（未录入 / 待判定）** + 历史审核流水。</p>
 *
 * <p><b>异常项清单是放行红线的一部分</b>：前端必须展示，且后端在
 * {@code abnormalConfirmed=false} 时拒绝审核通过（对接 T-912 口径）。</p>
 */
@Data
public class AuditDetailVO {

    private Long sampleId;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    private Integer status;

    private String statusLabel;

    /** 整体结论 code */
    private Integer conclusion;

    private String conclusionLabel;

    private Integer itemTotal;

    /** 已有效录入项数 */
    private Integer enteredCount;

    /** 未录入项数（testValue 空且非 jt3 人工结论） */
    private Integer blankCount;

    /** 待判定项数（引擎判定为待判定） */
    private Integer pendingCount;

    /** 异常项数 = 未录入 + 待判定 */
    private Integer abnormalCount;

    /** 是否可审核（status=S60） */
    private Boolean allowAudit;

    /** 是否可签发（status=S70） */
    private Boolean allowSign;

    // ---- 当前有效的审核/签发信息 ----

    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditAt;

    private String auditOpinion;

    private String signBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signAt;

    /** 全部检测单项（含结果与判定依据） */
    private List<Item> items;

    /** 异常项清单（未录入 / 待判定）——放行前必须确认 */
    private List<AbnormalItem> abnormalItems;

    /** 审核/签发流水（倒序） */
    private List<LogItem> logs;

    /** 检测单项视图 */
    @Data
    public static class Item {

        private Long id;

        private Integer itemOrder;

        private String itemName;

        private String unit;

        private String basisCode;

        /** 标准值（判定依据参数，快照） */
        private String stdValue;

        private Integer judgeType;

        private String judgeTypeLabel;

        private Integer isReference;

        private String lowerLimit;

        private String testerNo;

        private String testerName;

        /** 检验结果原始值 */
        private String testValue;

        /** 单项结论 code */
        private Integer conclusion;

        private String conclusionLabel;

        /** 结论来源 code：1=自动判定 2=人工判定 */
        private Integer conclusionSource;

        private String conclusionSourceLabel;

        /** 判定依据说明 */
        private String judgeBasis;

        private String enteredBy;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime enteredAt;

        /** 是否已有效录入（testValue 非空，或 jt3 已人工选结论） */
        private Boolean entered;
    }

    /** 异常项（未录入 / 待判定） */
    @Data
    public static class AbnormalItem {

        private Long itemId;

        private Integer itemOrder;

        private String itemName;

        /** 异常类型：BLANK=未录入 / PENDING=待判定 */
        private String type;

        private String typeLabel;

        /** 说明（判定的依据说明或未录入提示） */
        private String reason;
    }

    /** 审核/签发流水 */
    @Data
    public static class LogItem {

        private Long id;

        private Integer action;

        private String actionLabel;

        private Integer fromStatus;

        private String fromStatusLabel;

        private Integer toStatus;

        private String toStatusLabel;

        private String opinion;

        private Integer abnormalConfirmed;

        private String operatedBy;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime operatedAt;
    }
}
