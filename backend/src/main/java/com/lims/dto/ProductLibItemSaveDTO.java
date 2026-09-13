package com.lims.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目标准库-检测单项保存请求（api-spec 第 12 章，T-106）。
 *
 * <p>⚠️ 本表是 T-401「项目分解自动套库」与 T-601「判定引擎」的**上游数据源**。
 * 保存时若 `judgeType` 与 `stdValue` 形态矛盾（如 jt2 配数值、jt1 配「不得检出」），
 * 服务层按既有自裁口径**拒绝写入**（见 DECISIONS 2026-09-12「形态与判定类型矛盾」），
 * 而不是等到分解/判定时才暴露——污染源头比污染下游更贵。</p>
 */
@Data
public class ProductLibItemSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 更新时必填，新建时忽略 */
    private Long id;

    /** 所属产品库 id（新增时必填） */
    private Long productLibId;

    @NotNull(message = "顺序号不能为空")
    @Min(value = 1, message = "顺序号必须从 1 开始")
    private Integer itemOrder;

    @NotBlank(message = "检测项目名称不能为空")
    @Size(max = 255, message = "检测项目名称长度不能超过 255")
    private String itemName;

    @Size(max = 50, message = "计量单位长度不能超过 50")
    private String unit;

    @Size(max = 100, message = "判定依据标准号长度不能超过 100")
    private String basisCode;

    @Size(max = 500, message = "检验方法长度不能超过 500")
    private String methods;

    @Size(max = 50, message = "限量值长度不能超过 50")
    private String stdValue;

    /** 判定类型：1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
    @NotNull(message = "判定类型不能为空")
    @Min(value = 1, message = "判定类型只能为 1/2/3")
    private Integer judgeType;

    /** 是否参考项：0=否 1=是（参考项参与单项判定展示，不计入整体结论） */
    private Integer isReference;

    @Size(max = 20, message = "最低检出限长度不能超过 20")
    private String lowerLimit;

    @Size(max = 100, message = "方法备注长度不能超过 100")
    private String methodNote;
}
