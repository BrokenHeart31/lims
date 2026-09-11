package com.lims.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保存项目分解请求（api-spec 4.4，T-401）。
 *
 * <p>覆盖式保存：先逻辑删除该样品已有明细，再按 items 全量重建。</p>
 */
@Data
public class ItemSaveDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    /** 分解明细（至少 1 项；项次在样品内唯一，后端不自动重排） */
    @NotEmpty(message = "请至少保留一个检验项目")
    @Valid
    private List<ItemSaveDTO.Item> items;

    /**
     * 单个分解明细。
     *
     * <p>不含 sampleId / sampleNo（由父级与样品带入），不含审计字段（自动填充）。</p>
     */
    @Data
    public static class Item {

        @NotNull(message = "项次不能为空")
        private Integer itemOrder;

        @NotEmpty(message = "检验项目名称不能为空")
        @Size(max = 255, message = "检验项目名称不能超过 255 字")
        private String itemName;

        /** 来源标准库明细ID；人工新增为 null */
        private Long libItemId;

        @Size(max = 50, message = "单位不能超过 50 字")
        private String unit;

        @Size(max = 100, message = "判定依据不能超过 100 字")
        private String basisCode;

        @Size(max = 500, message = "检验方法不能超过 500 字")
        private String methods;

        @Size(max = 50, message = "标准值不能超过 50 字")
        private String stdValue;

        /** 判定类型：1=限量比较 2=不得检出/不得使用 3=文本/感官人工（后端校验 ∈ {1,2,3}） */
        @NotNull(message = "判定类型不能为空")
        private Integer judgeType;

        /** 是否参考性限量：0=否 1=是（后端校验 ∈ {0,1}） */
        @NotNull(message = "是否参考性限量不能为空")
        private Integer isReference;

        @Size(max = 20, message = "最低检出限不能超过 20 字")
        private String lowerLimit;

        @Size(max = 100, message = "方法备注不能超过 100 字")
        private String methodNote;

        /** 来源：1=标准库自动套用 2=人工新增（后端校验 ∈ {1,2}） */
        @NotNull(message = "来源类型不能为空")
        private Integer sourceType;

        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
    }
}
