package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 执行自动分配请求（api-spec 5.4）。
 */
@Data
public class AssignAutoDTO {

    /** 样品ID（sample_info.id） */
    @NotNull(message = "样品ID不能为空")
    private Long sampleId;
}
