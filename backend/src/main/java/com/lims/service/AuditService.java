package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.AuditApproveDTO;
import com.lims.dto.AuditReturnDTO;
import com.lims.dto.ReportSignDTO;
import com.lims.vo.AuditActionVO;
import com.lims.vo.AuditDetailVO;
import com.lims.vo.AuditPendingVO;

/**
 * 检验报告审核 / 签发服务（api-spec 第 7 章，T-701）。
 *
 * <p>覆盖阶段七：检验数据全部录齐（S60）→ 审核（S60→S70）→ 签发（S70→S80），
 * 并支持「审核退回 → S50」把样品打回检验员重录（AGENTS 7.2）。
 * 业务依据：说明书「八、检验业务流程之五：检验报告审核签发」。</p>
 */
public interface AuditService {

    /** 7.2 分页查询待审核样品（status=S60 检验完成） */
    PageResult<AuditPendingVO> pagePendingAudit(long pageNum, long pageSize, String sampleNo, String sampleName);

    /** 7.2 分页查询待签发样品（status=S70 已审核） */
    PageResult<AuditPendingVO> pagePendingSign(long pageNum, long pageSize, String sampleNo, String sampleName);

    /** 7.3 审核/签发明细（含全部单项结果、判定依据、异常项清单、审核流水） */
    AuditDetailVO detail(Long sampleId);

    /** 7.4 审核通过：S60 → S70（存在异常项时必须显式确认） */
    AuditActionVO approve(AuditApproveDTO dto);

    /** 7.5 审核退回：S60 → S50（独立退回白名单，原因必填） */
    AuditActionVO returnToTester(AuditReturnDTO dto);

    /** 7.6 签发：S70 → S80 */
    AuditActionVO sign(ReportSignDTO dto);
}
