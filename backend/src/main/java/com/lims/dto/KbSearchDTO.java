package com.lims.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * GB 标准检索请求（feature A，T03 / api-spec A12）。
 */
@Data
public class KbSearchDTO {

    /** 检索词（必填；单字词会被 ngram 忽略，见 GbRetriever 注释） */
    @NotBlank(message = "检索词不能为空")
    @Size(max = 200, message = "检索词过长")
    private String query;

    /** 返回条数（默认取 lims.ai.top-n） */
    @Min(value = 1, message = "topN 至少为 1")
    @Max(value = 50, message = "topN 至多 50")
    private Integer topN;

    /** 限定标准号（可选，模糊匹配） */
    private String stdNo;
}
