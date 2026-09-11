package com.lims.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 样品登记确认请求（T-301，S10→S20）。
 */
@Data
public class SampleConfirmDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待确认样品主键集合（批量确认） */
    @NotEmpty(message = "请至少选择一条样品进行登记确认")
    private List<Long> ids;
}
