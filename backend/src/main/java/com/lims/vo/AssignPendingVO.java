package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 待安排样品行（api-spec 5.2，T-501）。
 *
 * <p>分页列表用：Sample 字段子集 + 指派进度（assignTotal / assignDone）。</p>
 */
@Data
public class AssignPendingVO {

    private Long id;

    private String sampleNo;

    private String sampleName;

    private String clientName;

    private String taskNo;

    /** 样品状态 code（本接口固定为 30=已分解） */
    private Integer status;

    private String statusLabel;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    private String inspectType;

    /** 检测单项总数 */
    private Integer assignTotal;

    /** 已指派单项数 */
    private Integer assignDone;
}
