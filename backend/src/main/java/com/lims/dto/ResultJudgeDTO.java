package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实时判定预览请求（api-spec 6.3，T-601）。
 *
 * <p>只做「输入 → 结论」的纯计算，**不落库**，供录入页在检验员输入时实时显示判定结果与依据。</p>
 */
@Data
public class ResultJudgeDTO {

    /** 检测单项ID（sample_item.id），判定依据参数由此项的快照字段取得 */
    @NotNull(message = "检测单项ID不能为空")
    private Long itemId;

    /** 检验结果原始值（数值 或 未检出；可空——未录入时返回「待判定」） */
    private String testValue;

    /** 人工结论 code（仅 judgeType=3 文本/感官项使用：1=合格 2=不合格） */
    private Integer manualConclusion;
}
