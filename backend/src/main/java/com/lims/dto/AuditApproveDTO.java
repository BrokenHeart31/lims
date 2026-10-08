package com.lims.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审核通过请求（api-spec 7.4，T-701 / F20）。
 *
 * <p><b>放行红线（T-912 口径落地）</b>：样品存在「待判定」或「未录入」项时，
 * 审核人必须先在页面看到完整清单，再以 {@code abnormalConfirmed=true} 显式确认；
 * 否则后端拒绝放行（{@code code=400}）。这样「有异常仍放行」是一个
 * <b>有意识、有留痕</b>的决定，而不是被忽略的默认值。</p>
 *
 * <p><b>F20 合规升级（CMA 报告是法律文书，不允许未决结论项）</b>：
 * <ul>
 *   <li><b>未录入项（BLANK）</b>：属于操作缺漏，审核不能放行——必须退回检验员补录；</li>
 *   <li><b>待判定项（PENDING）</b>：有数据但引擎判不出，审核人必须<b>逐条人工裁决</b>
 *       （合格/不合格 + 必填说明），落库为该单项的人工结论；未逐条裁决则 {@code 400}。</li>
 * </ul>
 * 因此 {@code adjudications} 在「存在待判定项」时必填，且需与待判定项一一对应。</p>
 *
 * <p>请求体示例：
 * <pre>{@code
 * {
 *   "sampleId": 21,
 *   "opinion": "已逐条裁决",
 *   "abnormalConfirmed": true,
 *   "adjudications": [
 *     { "itemId": 1, "conclusion": 1, "reason": "检出值低于最低检出限，按未检出判定合格" }
 *   ]
 * }
 * }</pre></p>
 */
@Data
public class AuditApproveDTO {

    @NotNull(message = "样品ID不能为空")
    private Long sampleId;

    @Size(max = 500, message = "审核意见不能超过 500 字")
    private String opinion;

    /** 是否已确认异常项清单（待判定/未录入）；前端强制勾选后置 true */
    @NotNull(message = "请先确认「异常项清单」后再审核通过")
    private Boolean abnormalConfirmed;

    /**
     * 待判定项人工裁决清单（F20）。
     *
     * <p>可空；但当样品存在「待判定」项时必填，且每个待判定项须恰好提供一条裁决。</p>
     */
    @Valid
    private List<Adjudication> adjudications;

    /**
     * 单个「待判定」项的人工裁决（F20）。
     *
     * <p>落库口径：{@code sample_result} 该行 {@code conclusion}=裁决值、
     * {@code conclusion_source}=2（人工判定）、{@code judge_basis} 追加人工裁决留痕。</p>
     */
    @Data
    public static class Adjudication {

        /** 检测单项ID（sample_item.id，与异常项清单 itemId 同口径） */
        @NotNull(message = "待判定项的单项ID不能为空")
        private Long itemId;

        /** 裁决结论：仅允许 1=合格 / 2=不合格（服务层复核闭集） */
        @NotNull(message = "请为待判定项选择裁决结论")
        private Integer conclusion;

        /** 裁决说明（必填，留痕入 judge_basis） */
        @NotBlank(message = "裁决说明不能为空")
        @Size(max = 200, message = "裁决说明不能超过 200 字")
        private String reason;
    }
}
