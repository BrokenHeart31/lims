package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.dto.ReportVoidDTO;
import com.lims.entity.ReportVoid;
import com.lims.entity.Sample;
import com.lims.mapper.ReportVoidMapper;
import com.lims.mapper.SampleMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.ReportVoidService;
import com.lims.service.SampleStatusLogService;
import com.lims.vo.ReportVoidResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * 报告作废 / 召回服务实现（feature B，T02）。
 *
 * <p><b>不变式（本类的全部依据）</b>：</p>
 * <ol>
 *   <li><b>不改 {@code status}</b>——报告已对外生效，作废/召回是「治理标记」而非状态回退；
 *       原签发记录与流水全部保留（PRD T4 / G5）。</li>
 *   <li><b>乐观条件 UPDATE</b>（{@code WHERE id=? AND void_status=0}）——并发下只有一次生效，
 *       后到者 {@code updated==0} → 400；防重复作废。</li>
 *   <li><b>恰好一条流水</b>（{@code event_type=6} 作废/召回），{@code from_status == to_status}
 *       （状态未变），reason 必填。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ReportVoidServiceImpl implements ReportVoidService {

    /** 允许作废/召回的状态集合：已签发 / 已出报告 */
    private static final Set<SampleStatus> VOIDABLE_STATUSES = EnumSet.of(SampleStatus.S80, SampleStatus.S90);

    /** void_status：正常 */
    private static final int VOID_NONE = 0;

    /** 流水来源：报告域（与 SAMPLE/ITEM/ASSIGN/RESULT/AUDIT/ROLLBACK_PANEL 区分） */
    private static final String SOURCE_REPORT = "REPORT";

    private final SampleMapper sampleMapper;
    private final ReportVoidMapper reportVoidMapper;
    private final SampleStatusLogService statusLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVoidResultVO voidReport(ReportVoidDTO dto) {
        // ① 类型白名单（1=作废 2=召回）；int 常量比较，null 亦非法
        Integer voidType = dto.getVoidType();
        if (voidType == null || (voidType != ReportVoid.TYPE_VOID && voidType != ReportVoid.TYPE_RECALL)) {
            throw new BizException(400, "作废类型非法（仅允许 1=作废 2=召回）");
        }
        // ② 二次确认（报告已对外生效，强约束）
        if (!Boolean.TRUE.equals(dto.getSecondConfirmed())) {
            throw new BizException(400, "作废 / 召回报告需二次确认");
        }
        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(400, "作废/召回原因不能为空");
        }

        Sample sample = requireByNo(dto.getSampleNo());
        SampleStatus atStatus = sample.getStatus();
        // ③ 仅已签发 / 已出报告可作废（其余状态走普通回退）
        if (atStatus == null || !VOIDABLE_STATUSES.contains(atStatus)) {
            throw new BizException(400, "样品当前为「" + sample.getStatusLabel()
                    + "」，仅「已签发 / 已出报告」可作废或召回");
        }
        // ④ 幂等护栏：已作废/已召回不再重复
        if (sample.getVoidStatus() != null && sample.getVoidStatus() != VOID_NONE) {
            throw new BizException(400, "该报告已作废或已召回，请勿重复操作");
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();
        boolean recall = voidType == ReportVoid.TYPE_RECALL;
        String actionLabel = recall ? "召回" : "作废";

        // ⑤ 写 marker：乐观条件 UPDATE（void_status=0 → 目标值），实体式 update 触发审计填充
        Sample patch = new Sample();
        patch.setVoidStatus(voidType);
        int updated = sampleMapper.update(patch, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getVoidStatus, VOID_NONE));
        if (updated == 0) {
            throw new BizException(400, "该报告状态已变更，请刷新后重试");
        }

        // ⑥ 写治理记录（report_void，只增不改）
        ReportVoid record = new ReportVoid();
        record.setSampleId(sample.getId());
        record.setSampleNo(sample.getSampleNo());
        record.setVoidType(voidType);
        record.setStatusAtVoid(atStatus);
        record.setReason(dto.getReason());
        record.setSecondConfirmed(1);
        record.setOperatedBy(operator);
        record.setOperatedAt(now);
        reportVoidMapper.insert(record);

        // ⑦ 追加一条流水（event_type=6；status 不变 → from==to）
        statusLogService.append(sample, StatusEventType.VOID, atStatus, atStatus,
                actionLabel, dto.getReason(), SOURCE_REPORT, null,
                (recall ? "报告召回" : "报告作废") + "（状态保持「" + atStatus.getLabel() + "」不变）");

        return buildVO(sample, record);
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    /** 按编号取样品；编号为空或不存在均抛 400（业务失败走 HTTP 200 + body.code） */
    private Sample requireByNo(String sampleNo) {
        if (!StringUtils.hasText(sampleNo)) {
            throw new BizException(400, "样品编号不能为空");
        }
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleNo, sampleNo.trim()));
        if (sample == null) {
            throw new BizException(400, "样品不存在: " + sampleNo);
        }
        return sample;
    }

    private ReportVoidResultVO buildVO(Sample sample, ReportVoid record) {
        ReportVoidResultVO vo = new ReportVoidResultVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setVoidType(record.getVoidType());
        vo.setVoidTypeLabel(record.getVoidTypeLabel());
        vo.setStatusAtVoid(record.getStatusAtVoid() == null ? null : record.getStatusAtVoid().getCode());
        vo.setStatusAtVoidLabel(record.getStatusAtVoidLabel());
        vo.setReason(record.getReason());
        vo.setSecondConfirmed(record.getSecondConfirmed());
        vo.setOperatedBy(record.getOperatedBy());
        vo.setOperatedAt(record.getOperatedAt());
        return vo;
    }
}
