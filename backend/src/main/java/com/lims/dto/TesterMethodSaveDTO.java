package com.lims.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 方法-检验员资质保存请求（api-spec 第 11 章，T-105；新建/更新共用，更新时 id 必填）。
 *
 * <p>说明书第二(2)节：「选择『添加检验员-检验方法』按钮，添加检验员拥有资质的检验方法。
 * 方法-检验员设置的目的是在任务安排时，系统可以将检验任务根据检验方法自动分配给合适的检验员。」
 * —— 本表即 T-501 自动分配第三级「方法资质规则」的唯一数据源。</p>
 */
@Data
public class TesterMethodSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 更新时必填，新建时忽略 */
    private Long id;

    @NotBlank(message = "检验方法名称不能为空")
    @Size(max = 255, message = "检验方法名称长度不能超过 255")
    private String methodName;

    @NotBlank(message = "方法标准号不能为空")
    @Size(max = 100, message = "方法标准号长度不能超过 100")
    private String methodNo;

    @NotBlank(message = "检验员工号不能为空")
    @Size(max = 32, message = "检验员工号长度不能超过 32")
    private String testerNo;

    /** 资质状态：1=有效 0=失效；不传默认 1（有效） */
    @Min(value = 0, message = "资质状态只能为 0(失效) 或 1(有效)")
    @Max(value = 1, message = "资质状态只能为 0(失效) 或 1(有效)")
    private Integer qualStatus;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
