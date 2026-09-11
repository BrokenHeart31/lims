package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分解确认请求（api-spec 4.5，T-401）：S20 → S30。
 */
@Data
public class ItemConfirmDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;
}
