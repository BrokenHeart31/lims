package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目标准库-产品（api-spec 第 12 章，T-106）。
 */
@Data
public class ProductLibVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String productCode;
    private String productName;
    private String category;
    private String remark;

    /** 该产品下的检测单项数（列表页展示，避免逐行展开才知有没有数据） */
    private Long itemCount;

    /** 检测单项明细（仅 detail 接口填充；列表接口为 null 以控制响应体积） */
    private List<ProductLibItemVO> items;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
