package com.lims.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保存检验结果请求（api-spec 6.4，T-601）。
 *
 * <p>支持**分次录入**：一次可只提交部分检测单项（检验员按批次录），未提交的项保持原状。
 * 全部录齐后由 6.5「提交」流转 S60。</p>
 */
@Data
public class ResultSaveDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    /** 本次录入的检测单项结果（至少 1 项） */
    @NotEmpty(message = "请至少录入一个检测单项的结果")
    @Valid
    private List<ResultSaveDTO.Item> items;

    /** 单个检测单项的录入结果 */
    @Data
    public static class Item {

        @NotNull(message = "检测单项ID不能为空")
        private Long itemId;

        /** 检验结果原始值（数值 或 未检出） */
        @Size(max = 100, message = "检验结果不能超过 100 字")
        private String testValue;

        /** 人工结论 code（仅 judgeType=3 文本/感官项使用：1=合格 2=不合格） */
        private Integer manualConclusion;

        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
    }
}
