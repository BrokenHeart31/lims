package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交检验结果请求（api-spec 6.5，T-601）。
 *
 * <p>语义：声明「该样品全部检测单项已录入完毕」，经状态机白名单流转至 S60（检验完成）。</p>
 */
@Data
public class ResultSubmitDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;
}
