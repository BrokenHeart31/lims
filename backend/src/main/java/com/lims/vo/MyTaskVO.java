package com.lims.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 检验员任务查询行（api-spec 第 11 章，T-603）。
 *
 * <p>一行 = 一个「检验任务（样品 × 已指派检测单项）」。与导出用的 {@code MyTaskExportRow}
 * 字段同源，但查询行额外带 `sampleId`/`itemId`/`sampleStatus` 以便前端跳转到结果录入页，
 * 并带 `entered`/`conclusion` 让检验员在列表上就能看出「哪一项还没录」。</p>
 *
 * <p><b>`entered` 口径</b>：由 Service 复用 {@code ResultEntryPolicy.isEntered()} 填充，
 * 不在此处自算——见 T-912「有效录入唯一口径」。</p>
 */
@Data
public class MyTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 检测单项 id（前端跳转结果录入的定位键） */
    private Long itemId;

    private Long sampleId;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    private Integer itemOrder;

    private String itemName;

    private String methods;

    private String basisCode;

    private String stdValue;

    private String unit;

    private String lowerLimit;

    /** 判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
    private Integer judgeType;

    /** 样品实物状态（如「鲜活」），非流程状态 code */
    private String sampleState;

    /** 样品流程状态 code（10..90），用于跳转与阶段展示 */
    private Integer sampleStatus;

    private String sampleStatusLabel;

    /** 是否已有效录入（ResultEntryPolicy 口径） */
    private Boolean entered;

    /** 单项结论 code 1=合格 2=不合格 3=待判定；未录入时为 null（不出网） */
    private Integer conclusion;

    /** 结论来源 code（1=自动判定 2=人工）；文本感官项判定「已录入」时需要 */
    private Integer conclusionSource;

    private String conclusionLabel;

    /** 录入的检测值 */
    private String testValue;
}
