package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目标准库-产品保存请求（api-spec 第 12 章，T-106）。
 */
@Data
public class ProductLibSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 更新时必填，新建时忽略 */
    private Long id;

    @NotBlank(message = "产品编号不能为空")
    @Size(max = 20, message = "产品编号长度不能超过 20")
    private String productCode;

    @Size(max = 100, message = "产品名称长度不能超过 100")
    private String productName;

    @Size(max = 100, message = "食品大类长度不能超过 100")
    private String category;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
