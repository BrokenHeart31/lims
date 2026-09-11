package com.lims.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 监抽任务保存请求（api-spec 任务域，新建/更新共用；更新时 id 必填）
 */
@Data
public class SuperviseTaskSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 更新时必填，新建时忽略 */
    private Long id;

    @NotBlank(message = "任务编号不能为空")
    @Size(max = 50, message = "任务编号长度不能超过 50")
    private String taskNo;

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    @NotBlank(message = "任务性质不能为空")
    @Pattern(regexp = "监督抽检|委托抽样|委托送样", message = "任务性质须为：监督抽检/委托抽样/委托送样")
    private String taskNature;

    @Size(max = 100)
    private String taskSource;

    @Pattern(regexp = "^(省级|市级|区级)?$", message = "区域级别须为：省级/市级/区级")
    private String regionLevel;

    @Size(max = 50)
    private String leader;

    @Size(max = 50)
    private String batchNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate completeDate;

    @Size(max = 20)
    private String priority;

    @Size(max = 50)
    private String positiveRateRequirement;

    @Pattern(regexp = "^(生产|流通|餐饮)?$", message = "抽样环节须为：生产/流通/餐饮")
    private String samplingStage;

    @Size(max = 500)
    private String testScope;

    @Pattern(regexp = "^(草稿|进行中|已完成|已中止)?$", message = "任务状态须为：草稿/进行中/已完成/已中止")
    private String status;

    @Size(max = 500)
    private String remark;
}
