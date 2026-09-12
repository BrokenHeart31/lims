package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 待录入样品行（api-spec 6.2，T-601）。
 *
 * <p>检验员工作台列表：样品摘要 + 录入进度（已录 / 总数）+ 当前整体结论。</p>
 */
@Data
public class ResultPendingVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    private String taskBatchNo;

    private String inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    /** 样品状态 code（S40 已安排 / S50 检验中） */
    private Integer status;

    private String statusLabel;

    /** 检测单项总数 */
    private Integer itemTotal;

    /** 已录入结果的单项数 */
    private Integer enteredCount;

    /** 整体结论 code（未录齐时为 null 或 3） */
    private Integer conclusion;

    private String conclusionLabel;
}
