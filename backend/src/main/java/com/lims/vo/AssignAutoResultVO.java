package com.lims.vo;

import lombok.Data;

import java.util.List;

/**
 * 自动分配执行结果（api-spec 5.4，T-501）。
 *
 * <p>逐项返回指派结果与未指派原因 —— 未指派必须给出可读原因，
 * 便于「待人工指派」的排查（fail-loud：绝不静默指派到任意检验员）。</p>
 */
@Data
public class AssignAutoResultVO {

    private Long sampleId;

    /** 检测单项总数 */
    private Integer total;

    /** 本次已指派数（含此前已指派且未被覆盖的项） */
    private Integer assigned;

    /** 仍未指派数（需人工改派） */
    private Integer pending;

    private List<Detail> details;

    /** 单项指派明细 */
    @Data
    public static class Detail {

        private Integer itemOrder;

        private String itemName;

        private Integer assignStatus;

        private Integer assignType;

        private String testerNo;

        private String testerName;

        /** 未指派原因（已指派时为 null） */
        private String reason;
    }
}
