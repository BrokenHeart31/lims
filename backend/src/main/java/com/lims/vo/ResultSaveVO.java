package com.lims.vo;

import lombok.Data;

import java.util.List;

/**
 * 保存/提交检验结果的响应（api-spec 6.4 / 6.5，T-601）。
 *
 * <p>返回保存后每项的判定结果与样品整体进度/结论，前端据此刷新录入页（无需二次请求明细）。</p>
 */
@Data
public class ResultSaveVO {

    private Long sampleId;

    private String sampleNo;

    /** 保存/提交后的样品状态 code（保存 S40→S50，提交 S50→S60） */
    private Integer status;

    private String statusLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 已录入结果的单项数 */
    private Integer enteredCount;

    /** 整体结论 code（1=合格 2=不合格 3=待判定；未录齐恒为 3） */
    private Integer conclusion;

    private String conclusionLabel;

    /** 本次落库的单项判定结果 */
    private List<Item> items;

    /** 单项判定结果 */
    @Data
    public static class Item {

        private Long itemId;

        private Integer itemOrder;

        private String itemName;

        private String testValue;

        private Integer conclusion;

        private String conclusionLabel;

        private Integer conclusionSource;

        private String conclusionSourceLabel;

        /** 判定依据说明 */
        private String judgeBasis;
    }
}
