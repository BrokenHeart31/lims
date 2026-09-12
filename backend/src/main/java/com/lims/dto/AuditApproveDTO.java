package com.lims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核通过请求（api-spec 7.4，T-701）。
 *
 * <p><b>放行红线（T-912 口径落地）</b>：样品存在「待判定」或「未录入」项时，
 * 审核人必须先在页面看到完整清单，再以 {@code abnormalConfirmed=true} 显式确认；
 * 否则后端拒绝放行（`code=400`）。这样「有异常仍放行」是一个**有意识、有留痕**的决定，
 * 而不是被忽略的默认值。</p>
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
}
