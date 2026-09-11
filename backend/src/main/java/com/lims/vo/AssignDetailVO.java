package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 样品安排明细（api-spec 5.3，T-501）。
 *
 * <p>聚合三层信息：样品摘要 + 各检测单项的指派结果 + 可选检验员候选。</p>
 */
@Data
public class AssignDetailVO {

    private Long sampleId;

    private String sampleNo;

    private String sampleName;

    /** 样品状态 code */
    private Integer status;

    private String statusLabel;

    /** 检测单项总数 */
    private Integer assignTotal;

    /** 已指派单项数 */
    private Integer assignDone;

    /** 是否已全部指派（前端据此决定「确认安排」按钮是否可点） */
    private Boolean inputPermitted;

    /** 检测单项及指派结果 */
    private List<Item> items;

    /**
     * 可选检验员候选（**仅含具备资质者**，AGENTS 7.4「仅列出有资质者」）。
     * 若某单项无任何有资质者，候选列表可能为空 —— 此时需先补录资质。
     */
    private List<Candidate> candidates;

    /** 检测单项的指派视图 */
    @Data
    public static class Item {

        private Long id;

        private Integer itemOrder;

        private String itemName;

        /** 检验方法（多个以 # 分隔），方法资质规则的匹配依据 */
        private String methods;

        private String unit;

        private String stdValue;

        private Integer judgeType;

        private Integer isReference;

        /** 指派状态：0=待指派 1=已指派 */
        private Integer assignStatus;

        /** 指派方式：0=未指派 1=分类规则 2=方法资质 3=人工改派 */
        private Integer assignType;

        private String testerNo;

        private String testerName;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime assignedAt;
    }

    /** 检验员候选 */
    @Data
    public static class Candidate {

        private String testerNo;

        private String testerName;

        /** 命中的方法标准号；由分类规则命中时为 null */
        private String matchedMethodNo;

        /** 命中来源：METHOD=方法资质 / CATEGORY=分类规则 */
        private String source;
    }
}
