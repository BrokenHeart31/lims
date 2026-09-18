package com.lims.service;

import com.lims.dto.ReportVoidDTO;
import com.lims.vo.ReportVoidResultVO;

/**
 * 报告作废 / 召回服务（feature B，T02，api-spec 第 18 章 B6）。
 *
 * <p><b>S80（已签发）/ S90（已出报告）的专门治理动作</b>：报告已对外生效（PRD Q2 假设），
 * 因此**不改状态机取值**（{@code status} 保持 80/90），只在 {@code sample_info.void_status}
 * 上打标记（1=已作废 2=已召回），并写 {@code report_void} + {@code sample_status_log(event_type=6)}。</p>
 *
 * <p>为什么与 {@code RollbackService} 分开：普通回退对被拒边 S80→S70 / S90→S80 显式拒绝
 * （4102/4103），治理动作语义、权限（{@code report:void}）与留痕（不改 status）都不同，
 * 合并会让「回退」这一个入口承担两种截然不同的合规语义。</p>
 */
public interface ReportVoidService {

    /**
     * 作废 / 召回一份已签发或已出报告。
     *
     * <p>前置：样品状态 ∈ {S80, S90}、当前未作废/未召回、原因必填、二次确认完成。
     * 成功后 {@code status} 不变，仅写标记 + 记录 + 流水。</p>
     */
    ReportVoidResultVO voidReport(ReportVoidDTO dto);
}
