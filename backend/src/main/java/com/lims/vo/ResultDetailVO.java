package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 样品录入明细（api-spec 6.2，T-601）。
 *
 * <p>录入工作台主数据：样品摘要 + 全部检测单项（含量规依据快照 + 已录入结果）+ 录入进度与整体结论。</p>
 */
@Data
public class ResultDetailVO {

    private Long sampleId;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    /** 样品状态 code */
    private Integer status;

    private String statusLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 已录入结果的单项数 */
    private Integer enteredCount;

    /** 整体结论 code（1=合格 2=不合格 3=待判定） */
    private Integer conclusion;

    private String conclusionLabel;

    /** 是否允许录入（状态 ∈ {S40, S50}）；前端据此禁用录入控件 */
    private Boolean allowEdit;

    private List<Item> items;

    /** 检测单项 + 已录入结果 */
    @Data
    public static class Item {

        /** 检测单项ID（sample_item.id），录入时回传 */
        private Long id;

        private Integer itemOrder;

        private String itemName;

        private String unit;

        /** 判定依据标准号 */
        private String basisCode;

        /** 检验方法（多个以 # 分隔） */
        private String methods;

        /** 标准值（判定依据参数，快照） */
        private String stdValue;

        /** 判定类型 1/2/3 */
        private Integer judgeType;

        private String judgeTypeLabel;

        /** 是否参考性限量：0=否 1=是（参考项不计入整体结论） */
        private Integer isReference;

        /** 最低检出限 */
        private String lowerLimit;

        /** 指派检验员工号/姓名（来自 T-501） */
        private String testerNo;

        private String testerName;

        // ---- 已录入结果（未录入时为 null） ----

        private String testValue;

        private Integer conclusion;

        private String conclusionLabel;

        private Integer conclusionSource;

        private String conclusionSourceLabel;

        /** 判定依据说明 */
        private String judgeBasis;

        private String enteredBy;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime enteredAt;

        private String remark;

        /**
         * 是否已**有效录入**（T-912 定稿口径，由后端 {@code ResultEntryPolicy} 判定）：
         * {@code testValue} 非空，或文本/感官项已人工选定合格/不合格。
         *
         * <p>未有效录入时，{@code conclusion} 相关字段一律不出网，前端统一显示「未录入」——
         * 避免空值行遗留的 {@code conclusion=3} 伪装成「待判定」。前端据此高亮未录行。</p>
         */
        private Boolean entered;
    }
}
