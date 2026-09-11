package com.lims.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 样品登记信息维护请求（T-301，id 必填，仅 S10 状态可改）。
 *
 * <p>对应说明书「样品登记信息维护与确认：可对样品登记信息进一步补充完善」。</p>
 */
@Data
public class SampleUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 样品主键（必填） */
    private Long id;

    @NotBlank(message = "样品编号不能为空")
    @Size(max = 50, message = "样品编号长度不能超过 50")
    private String sampleNo;

    @NotBlank(message = "样品名称不能为空")
    @Size(max = 255, message = "样品名称长度不能超过 255")
    private String sampleName;

    @Size(max = 255)
    private String clientName;

    @Size(max = 255)
    private String samplingAddress;

    @Size(max = 50)
    private String payee;

    private BigDecimal fee;

    @Size(max = 50)
    private String sampleQuantity;

    @Size(max = 100)
    private String projectName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    @Size(max = 500)
    private String remark;

    @Size(max = 50)
    private String sampler;

    @Size(max = 255)
    private String manufacturer;

    @Size(max = 50)
    private String samplingBase;

    @Size(max = 50)
    private String sampleState;

    @Size(max = 100)
    private String spec;

    @Size(max = 100)
    private String brand;

    @Size(max = 50)
    private String grade;

    @Size(max = 100)
    private String originalNo;

    @Size(max = 50)
    private String inspectType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate requireCompleteDate;

    @Size(max = 50)
    private String taskNo;

    @Size(max = 50)
    private String taskBatchNo;
}
