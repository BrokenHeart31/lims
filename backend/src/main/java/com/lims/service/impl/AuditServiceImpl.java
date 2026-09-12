package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lims.common.PageResult;
import com.lims.common.enums.AuditAction;
import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.exception.BizException;
import com.lims.dto.AuditApproveDTO;
import com.lims.dto.AuditReturnDTO;
import com.lims.dto.ReportSignDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleAuditLog;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleAuditLogMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.AuditService;
import com.lims.service.result.ResultEntryPolicy;
import com.lims.vo.AuditActionVO;
import com.lims.vo.AuditDetailVO;
import com.lims.vo.AuditPendingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验报告审核 / 签发服务实现（T-701）。
 *
 * <p><b>三条不变式</b>（本类的全部实现都围绕它们）：</p>
 * <ol>
 *   <li><b>正向用 {@code assertTransition}，退回用 {@code assertReturn}</b>——
 *       两张独立白名单，互不可替代（见 {@link SampleStatusTransition} 类注释）。</li>
 *   <li><b>每次动作都追加一条流水</b>（{@code sample_audit_log}）且<b>永不改写流水</b>——
 *       这是报告可追溯的唯一证据（ALCOA+ Audit Trail）。</li>
 *   <li><b>存在异常项（未录入 / 待判定）时不得静默放行</b>——审核人必须显式确认
 *       （{@code abnormalConfirmed=true}），否则 {@code code=400}。</li>
 * </ol>
 *
 * <p><b>为什么退回要清空 {@code sample_info.audit_*} 却保留流水</b>：
 * 「当前有效值」用于报告打印，退回意味着审核未通过，报告上不应出现审核人；
 * 而「谁因何退回」属于历史事实，必须留在流水表里供追溯与责任界定。</p>
 *
 * <p><b>「通知检验员」怎么实现</b>：本项目无独立消息通道——样品退回后回到 S50，
 * 会重新出现在检验员的「结果录入」待办列表（{@code /api/result/pending} 收录 S40/S50）
 * 与审核流水里，即「通知」由待办可见性承担，不引入额外通道（避免半成品通知组件）。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditServiceImpl extends ServiceImpl<SampleAuditLogMapper, SampleAuditLog> implements AuditService {

    private final SampleMapper sampleMapper;
    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;

    private static final int REFERENCE_YES = 1;
    private static final int ABNORMAL_CONFIRMED = 1;

    private static final String TYPE_BLANK = "BLANK";
    private static final String TYPE_PENDING = "PENDING";

    // =========================================================================
    // 7.2 待审核 / 待签发列表
    // =========================================================================

    @Override
    public PageResult<AuditPendingVO> pagePendingAudit(long pageNum, long pageSize,
                                                       String sampleNo, String sampleName) {
        return pageByStatus(SampleStatus.S60, pageNum, pageSize, sampleNo, sampleName);
    }

    @Override
    public PageResult<AuditPendingVO> pagePendingSign(long pageNum, long pageSize,
                                                      String sampleNo, String sampleName) {
        return pageByStatus(SampleStatus.S70, pageNum, pageSize, sampleNo, sampleName);
    }

    private PageResult<AuditPendingVO> pageByStatus(SampleStatus status, long pageNum, long pageSize,
                                                    String sampleNo, String sampleName) {
        Page<Sample> page = new Page<>(pageNum, pageSize);
        Page<Sample> result = sampleMapper.selectPage(page, new LambdaQueryWrapper<Sample>()
                .eq(Sample::getStatus, status)
                // 只列出「有检测单项」的样品（无明细的样品无从审核）
                .inSql(Sample::getId, "SELECT DISTINCT sample_id FROM sample_item WHERE deleted = 0")
                .like(StringUtils.hasText(sampleNo), Sample::getSampleNo, sampleNo)
                .like(StringUtils.hasText(sampleName), Sample::getSampleName, sampleName)
                .orderByDesc(Sample::getId));

        List<Long> ids = result.getRecords().stream().map(Sample::getId).toList();
        Map<Long, List<SampleItem>> itemsBySample = itemsBySample(ids);
        Map<Long, Map<Long, SampleResult>> resultsBySample = resultsBySample(ids);

        List<AuditPendingVO> rows = result.getRecords().stream().map(s -> {
            List<SampleItem> items = itemsBySample.getOrDefault(s.getId(), Collections.emptyList());
            Map<Long, SampleResult> results = resultsBySample.getOrDefault(s.getId(), Collections.emptyMap());
            AuditPendingVO vo = new AuditPendingVO();
            vo.setId(s.getId());
            vo.setSampleNo(s.getSampleNo());
            vo.setSampleName(s.getSampleName());
            vo.setClientName(s.getClientName());
            vo.setTaskNo(s.getTaskNo());
            vo.setInspectType(s.getInspectType());
            vo.setSamplingDate(s.getSamplingDate());
            vo.setStatus(s.getStatus() == null ? null : s.getStatus().getCode());
            vo.setStatusLabel(s.getStatusLabel());
            vo.setConclusion(s.getConclusion() == null ? null : s.getConclusion().getCode());
            vo.setConclusionLabel(s.getConclusionLabel());
            vo.setItemTotal(items.size());
            vo.setEnteredCount(countEntered(items, results));
            vo.setAbnormalCount(countAbnormal(items, results));
            vo.setAuditBy(s.getAuditBy());
            vo.setAuditAt(s.getAuditAt());
            vo.setAuditOpinion(s.getAuditOpinion());
            return vo;
        }).toList();

        PageResult<AuditPendingVO> out = new PageResult<>();
        out.setRecords(rows);
        out.setTotal(result.getTotal());
        out.setCurrent(result.getCurrent());
        out.setSize(result.getSize());
        return out;
    }

    // =========================================================================
    // 7.3 明细
    // =========================================================================

    @Override
    public AuditDetailVO detail(Long sampleId) {
        Sample sample = requireSample(sampleId);
        List<SampleItem> items = listItems(sampleId);
        Map<Long, SampleResult> results = resultsByItem(sampleId);

        AuditDetailVO vo = new AuditDetailVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setSampleName(sample.getSampleName());
        vo.setClientName(sample.getClientName());
        vo.setTaskNo(sample.getTaskNo());
        vo.setStatus(sample.getStatus() == null ? null : sample.getStatus().getCode());
        vo.setStatusLabel(sample.getStatusLabel());
        vo.setConclusion(sample.getConclusion() == null ? null : sample.getConclusion().getCode());
        vo.setConclusionLabel(sample.getConclusionLabel());
        vo.setItemTotal(items.size());
        vo.setEnteredCount(countEntered(items, results));

        List<AuditDetailVO.AbnormalItem> abnormal = buildAbnormalItems(items, results);
        vo.setAbnormalItems(abnormal);
        vo.setAbnormalCount(abnormal.size());
        vo.setBlankCount((int) abnormal.stream().filter(a -> TYPE_BLANK.equals(a.getType())).count());
        vo.setPendingCount((int) abnormal.stream().filter(a -> TYPE_PENDING.equals(a.getType())).count());

        vo.setAllowAudit(sample.getStatus() == SampleStatus.S60);
        vo.setAllowSign(sample.getStatus() == SampleStatus.S70);
        vo.setAuditBy(sample.getAuditBy());
        vo.setAuditAt(sample.getAuditAt());
        vo.setAuditOpinion(sample.getAuditOpinion());
        vo.setSignBy(sample.getSignBy());
        vo.setSignAt(sample.getSignAt());

        vo.setItems(items.stream().map(i -> toItemVO(i, results.get(i.getId()))).toList());
        vo.setLogs(listLogs(sampleId).stream().map(this::toLogVO).toList());
        return vo;
    }

    // =========================================================================
    // 7.4 审核通过（S60 → S70）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuditActionVO approve(AuditApproveDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        requireStatus(sample, SampleStatus.S60, "审核");

        // 放行红线：有异常项必须先显式确认（T-912 口径落地）
        List<AuditDetailVO.AbnormalItem> abnormal =
                buildAbnormalItems(listItems(sample.getId()), resultsByItem(sample.getId()));
        if (!abnormal.isEmpty() && !Boolean.TRUE.equals(dto.getAbnormalConfirmed())) {
            throw new BizException(400, "该样品存在 " + abnormal.size()
                    + " 个待判定/未录入项，请先逐项确认「异常项清单」后再审核通过");
        }

        SampleStatusTransition.assertTransition(SampleStatus.S60, SampleStatus.S70);

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        Sample upd = new Sample();
        upd.setStatus(SampleStatus.S70);
        upd.setAuditBy(operator);
        upd.setAuditAt(now);
        upd.setAuditOpinion(dto.getOpinion());
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, SampleStatus.S60));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }

        Integer confirmed = abnormal.isEmpty() || Boolean.TRUE.equals(dto.getAbnormalConfirmed())
                ? ABNORMAL_CONFIRMED : 0;
        insertLog(sample, AuditAction.APPROVE, SampleStatus.S60, SampleStatus.S70,
                dto.getOpinion(), confirmed, operator, now);

        return buildActionVO(sample, SampleStatus.S70, AuditAction.APPROVE,
                dto.getOpinion(), confirmed, operator, now);
    }

    // =========================================================================
    // 7.5 审核退回（S60 → S50，独立退回白名单）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuditActionVO returnToTester(AuditReturnDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        requireStatus(sample, SampleStatus.S60, "审核退回");

        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(400, "退回原因不能为空");
        }
        // 退回走**独立**白名单：正向表里 S60 的出边只有 S70
        SampleStatusTransition.assertReturn(SampleStatus.S60, SampleStatus.S50);

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        Sample upd = new Sample();
        upd.setStatus(SampleStatus.S50);
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, SampleStatus.S60)
                // 注意：MP 的实体式 update 会忽略 null 字段，故「清空」必须显式 set
                // 审核未通过 → 清空「当前有效」的审核信息（报告上不应出现审核人）
                .set(Sample::getAuditBy, null)
                .set(Sample::getAuditAt, null)
                .set(Sample::getAuditOpinion, null));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }

        insertLog(sample, AuditAction.RETURN, SampleStatus.S60, SampleStatus.S50,
                dto.getReason(), 0, operator, now);

        return buildActionVO(sample, SampleStatus.S50, AuditAction.RETURN,
                dto.getReason(), 0, operator, now);
    }

    // =========================================================================
    // 7.6 签发（S70 → S80）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuditActionVO sign(ReportSignDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        requireStatus(sample, SampleStatus.S70, "签发");

        SampleStatusTransition.assertTransition(SampleStatus.S70, SampleStatus.S80);

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        Sample upd = new Sample();
        upd.setStatus(SampleStatus.S80);
        upd.setSignBy(operator);
        upd.setSignAt(now);
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, SampleStatus.S70));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }

        insertLog(sample, AuditAction.SIGN, SampleStatus.S70, SampleStatus.S80,
                dto.getOpinion(), ABNORMAL_CONFIRMED, operator, now);

        return buildActionVO(sample, SampleStatus.S80, AuditAction.SIGN,
                dto.getOpinion(), ABNORMAL_CONFIRMED, operator, now);
    }

    // =========================================================================
    // 内部实现
    // =========================================================================

    /** 追加一条审核流水（只追加、永不改写） */
    private void insertLog(Sample sample, AuditAction action, SampleStatus from, SampleStatus to,
                           String opinion, int abnormalConfirmed, String operator, LocalDateTime now) {
        SampleAuditLog log = new SampleAuditLog();
        log.setSampleId(sample.getId());
        log.setSampleNo(sample.getSampleNo());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOpinion(opinion);
        log.setAbnormalConfirmed(abnormalConfirmed);
        log.setOperatedBy(operator);
        log.setOperatedAt(now);
        baseMapper.insert(log);
    }

    /**
     * 构造异常项清单：**未录入（BLANK）** 与 **待判定（PENDING）** 两类。
     *
     * <p>两类性质不同（前者是操作缺漏、后者是数据缺口），但都属于「审核人放行前必须看见」的信息，
     * 故合并为一张清单但用 {@code type} 区分，前端按类型着色提示。</p>
     *
     * <p><b>排序：未录入在前、待判定在后</b>——审核人应当先看到「根本没数据」的项
     * （必须打回补录），再看「有数据但判不出」的项（可人工裁决或打回），
     * 且顺序确定（不随项次/类型抖动），便于测试与人工核对。</p>
     */
    private List<AuditDetailVO.AbnormalItem> buildAbnormalItems(List<SampleItem> items,
                                                               Map<Long, SampleResult> results) {
        List<AuditDetailVO.AbnormalItem> blanks = new ArrayList<>();
        List<AuditDetailVO.AbnormalItem> pendings = new ArrayList<>();
        for (SampleItem item : items) {
            SampleResult r = results.get(item.getId());
            boolean entered = ResultEntryPolicy.isEntered(item.getJudgeType(), r);
            if (!entered) {
                AuditDetailVO.AbnormalItem abnormal = new AuditDetailVO.AbnormalItem();
                abnormal.setItemId(item.getId());
                abnormal.setItemOrder(item.getItemOrder());
                abnormal.setItemName(item.getItemName());
                abnormal.setType(TYPE_BLANK);
                abnormal.setTypeLabel("未录入");
                abnormal.setReason(r == null ? "该检测单项尚无检验结果，请补录" : "检验结果为空，请补录");
                blanks.add(abnormal);
            } else if (r.getConclusion() == null || r.getConclusion() == ResultConclusion.PENDING) {
                AuditDetailVO.AbnormalItem abnormal = new AuditDetailVO.AbnormalItem();
                abnormal.setItemId(item.getId());
                abnormal.setItemOrder(item.getItemOrder());
                abnormal.setItemName(item.getItemName());
                abnormal.setType(TYPE_PENDING);
                abnormal.setTypeLabel("待判定");
                abnormal.setReason(StringUtils.hasText(r.getJudgeBasis())
                        ? r.getJudgeBasis() : "判定引擎无法自动判定，需人工确认");
                pendings.add(abnormal);
            }
        }
        List<AuditDetailVO.AbnormalItem> list = new ArrayList<>(blanks.size() + pendings.size());
        list.addAll(blanks);
        list.addAll(pendings);
        return list;
    }

    private List<SampleAuditLog> listLogs(Long sampleId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SampleAuditLog>()
                .eq(SampleAuditLog::getSampleId, sampleId)
                .orderByDesc(SampleAuditLog::getId));
    }

    private AuditDetailVO.Item toItemVO(SampleItem item, SampleResult result) {
        AuditDetailVO.Item vo = new AuditDetailVO.Item();
        vo.setId(item.getId());
        vo.setItemOrder(item.getItemOrder());
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setBasisCode(item.getBasisCode());
        vo.setStdValue(item.getStdValue());
        vo.setJudgeType(item.getJudgeType());
        vo.setJudgeTypeLabel(ResultEntryPolicy.judgeTypeLabel(item.getJudgeType()));
        vo.setIsReference(item.getIsReference());
        vo.setLowerLimit(item.getLowerLimit());
        vo.setTesterNo(item.getTesterNo());
        vo.setTesterName(item.getTesterName());

        boolean entered = ResultEntryPolicy.isEntered(item.getJudgeType(), result);
        vo.setEntered(entered);
        if (result != null) {
            vo.setTestValue(result.getTestValue());
            vo.setEnteredBy(result.getEnteredBy());
            vo.setEnteredAt(result.getEnteredAt());
        }
        if (entered) {
            vo.setConclusion(result.getConclusion() == null ? null : result.getConclusion().getCode());
            vo.setConclusionLabel(result.getConclusionLabel());
            vo.setConclusionSource(result.getConclusionSource() == null
                    ? null : result.getConclusionSource().getCode());
            vo.setConclusionSourceLabel(result.getConclusionSourceLabel());
            vo.setJudgeBasis(result.getJudgeBasis());
        }
        return vo;
    }

    private AuditDetailVO.LogItem toLogVO(SampleAuditLog log) {
        AuditDetailVO.LogItem vo = new AuditDetailVO.LogItem();
        vo.setId(log.getId());
        vo.setAction(log.getAction() == null ? null : log.getAction().getCode());
        vo.setActionLabel(log.getActionLabel());
        vo.setFromStatus(log.getFromStatus() == null ? null : log.getFromStatus().getCode());
        vo.setFromStatusLabel(log.getFromStatus() == null ? null : log.getFromStatus().getLabel());
        vo.setToStatus(log.getToStatus() == null ? null : log.getToStatus().getCode());
        vo.setToStatusLabel(log.getToStatus() == null ? null : log.getToStatus().getLabel());
        vo.setOpinion(log.getOpinion());
        vo.setAbnormalConfirmed(log.getAbnormalConfirmed());
        vo.setOperatedBy(log.getOperatedBy());
        vo.setOperatedAt(log.getOperatedAt());
        return vo;
    }

    private AuditActionVO buildActionVO(Sample sample, SampleStatus status, AuditAction action,
                                        String opinion, int abnormalConfirmed,
                                        String operator, LocalDateTime now) {
        AuditActionVO vo = new AuditActionVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setStatus(status.getCode());
        vo.setStatusLabel(status.getLabel());
        vo.setAction(action.getCode());
        vo.setActionLabel(action.getLabel());
        vo.setOpinion(opinion);
        vo.setAbnormalConfirmed(abnormalConfirmed);
        vo.setOperatedBy(operator);
        vo.setOperatedAt(now);
        return vo;
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    private List<SampleItem> listItems(Long sampleId) {
        return sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getItemOrder));
    }

    private Map<Long, SampleResult> resultsByItem(Long sampleId) {
        return sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .eq(SampleResult::getSampleId, sampleId))
                .stream()
                .filter(r -> r.getSampleItemId() != null)
                .collect(Collectors.toMap(SampleResult::getSampleItemId,
                        Function.identity(), (a, b) -> a));
    }

    private Map<Long, List<SampleItem>> itemsBySample(List<Long> sampleIds) {
        if (sampleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                        .in(SampleItem::getSampleId, sampleIds))
                .stream()
                .collect(Collectors.groupingBy(SampleItem::getSampleId));
    }

    private Map<Long, Map<Long, SampleResult>> resultsBySample(List<Long> sampleIds) {
        if (sampleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<Long, SampleResult>> out = new LinkedHashMap<>();
        sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .in(SampleResult::getSampleId, sampleIds))
                .forEach(r -> out.computeIfAbsent(r.getSampleId(), k -> new LinkedHashMap<>())
                        .put(r.getSampleItemId(), r));
        return out;
    }

    private int countEntered(List<SampleItem> items, Map<Long, SampleResult> results) {
        return (int) items.stream()
                .filter(i -> ResultEntryPolicy.isEntered(i.getJudgeType(), results.get(i.getId())))
                .count();
    }

    private int countAbnormal(List<SampleItem> items, Map<Long, SampleResult> results) {
        int count = 0;
        for (SampleItem item : items) {
            SampleResult r = results.get(item.getId());
            if (!ResultEntryPolicy.isEntered(item.getJudgeType(), r)) {
                count++;
            } else if (r.getConclusion() == null || r.getConclusion() == ResultConclusion.PENDING) {
                count++;
            }
        }
        return count;
    }

    private Sample requireSample(Long sampleId) {
        if (sampleId == null) {
            throw new BizException(400, "样品ID不能为空");
        }
        Sample sample = sampleMapper.selectById(sampleId);
        if (sample == null) {
            throw new BizException(400, "样品不存在或已删除: id=" + sampleId);
        }
        return sample;
    }

    private void requireStatus(Sample sample, SampleStatus expected, String action) {
        if (sample.getStatus() != expected) {
            throw new BizException(400, "样品当前状态为「" + sample.getStatusLabel()
                    + "」，不允许" + action + "（要求「" + expected.getLabel() + "」）");
        }
    }
}
