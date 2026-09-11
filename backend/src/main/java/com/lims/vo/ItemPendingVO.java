package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 待分解样品行（api-spec 4.6，T-401）。
 *
 * <p>分页列表用：Sample 字段子集 + 已保存明细数（itemCount）。</p>
 */
@Data
public class ItemPendingVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    private String taskBatchNo;

    /** 样品状态 code（本接口固定为 20=登记确认） */
    private Integer status;

    private String statusLabel;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    private String inspectType;

    /** 已保存的分解明细数（供前端显示分解进度） */
    private Integer itemCount;
}
