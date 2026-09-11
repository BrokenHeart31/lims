package com.lims.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 套库预览结果（api-spec 4.2，T-401）。
 *
 * <p>按样品名匹配产品标准库，返回检测单项初稿；**不落库**，由前端展示并允许增删调整。</p>
 */
@Data
public class ItemMatchVO {

    private Long sampleId;

    private String sampleName;

    /** 是否匹配到唯一产品标准库 */
    private Boolean matched;

    /** 命中的产品库ID（未命中为 null；命中多条取 id 最小者） */
    private Long matchedLibId;

    /** 命中产品名称 */
    private String matchedProductName;

    /** 候选产品列表（仅命中多条时有值，供前端提示选择） */
    private List<Candidate> candidates;

    /** 细则：标准库明细初稿（按 item_order 升序） */
    private List<MatchedItem> items;

    /** 候选产品 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private Long libId;
        private String productName;
        private String category;
    }

    /** 套库得到的单个检测单项（字段与 sample_item 的下沉快照一致） */
    @Data
    public static class MatchedItem {
        private Integer itemOrder;
        private String itemName;
        private Long libItemId;
        private String unit;
        private String basisCode;
        private String methods;
        private String stdValue;
        private Integer judgeType;
        private Integer isReference;
        private String lowerLimit;
        private String methodNote;
    }
}
