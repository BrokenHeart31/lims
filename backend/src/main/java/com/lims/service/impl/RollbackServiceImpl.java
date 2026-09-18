package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.PageResult;
import com.lims.common.ResultCode;
import com.lims.common.enums.ArchiveTarget;
import com.lims.common.enums.RollbackEdgePolicy;
import com.lims.common.enums.RollbackGroup;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.dto.RollbackExecuteDTO;
import com.lims.dto.RollbackHistoryQueryDTO;
import com.lims.dto.RollbackPreviewDTO;
import com.lims.dto.RollbackRecoverDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.entity.SampleRollback;
import com.lims.entity.SampleStatusLog;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.mapper.SampleRollbackMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.RollbackService;
import com.lims.service.SampleStatusLogService;
import com.lims.service.rollback.RollbackPlanner;
import com.lims.service.rollback.RollbackScope;
import com.lims.service.rollback.SampleDataDisposer;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTimelineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 流程回溯服务实现（feature B，T02）。
 *
 * <p><b>四条不变式（本类实现的全部依据，设计 §0 / §10-R3）</b>：</p>
 * <ol>
 *   <li>状态变更用**乐观条件 UPDATE**（{@code WHERE id=? AND status=旧值}），{@code updated==0} → 4108；</li>
 *   <li>下游**失效处置在状态 UPDATE 之后、同一事务内**（先锁定状态再处置下游）；</li>
 *   <li>一次回退后 {@code sample_status_log} **新增且仅新增 1 条** {@code event_type=4} 记录，
 *       其 {@code (from_status,to_status,rollback_id)} 与 {@code sample_rollback} 行一致；</li>
 *   <li>**绝不物理删除**任何业务历史；{@code sample_audit_log}/{@code sys_operation_log} 只增不改。</li>
 * </ol>
 *
 * <p><b>执行顺序</b>：白名单/策略校验 → 快照 sample_info → 插入 sample_rollback（取 rollbackId）
 * → 状态乐观 UPDATE（4108）→ 失效处置 → 回写失效摘要 → 追加流水。任一步失败整事务回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class RollbackServiceImpl implements RollbackService {

    /** 流水来源：回溯面板（与 SAMPLE/ITEM/ASSIGN/RESULT/AUDIT/REPORT 区分） */
    private static final String SOURCE_ROLLBACK = "ROLLBACK_PANEL";
    /** can_recover = 是 */
    private static final int CAN_RECOVER = 1;
    /** 已恢复 = 是 */
    private static final int RECOVERED = 1;

    private final SampleMapper sampleMapper;
    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;
    private final SampleRollbackMapper rollbackMapper;
    private final SampleDataDisposer disposer;
    private final SampleStatusLogService statusLogService;
    private final RollbackPlanner rollbackPlanner;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // B2 预览（纯读，不落库）
    // =========================================================================

    @Override
    public RollbackPreviewVO preview(RollbackPreviewDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        SampleStatus from = sample.getStatus();
        SampleStatus to = requireStatus(dto.getTargetStatus());

        RollbackPreviewVO vo = new RollbackPreviewVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setFromStatus(from.getCode());
        vo.setFromStatusLabel(from.getLabel());
        vo.setToStatus(to.getCode());
        vo.setToStatusLabel(to.getLabel());

        RollbackGroup group = RollbackEdgePolicy.groupOf(from, to);
        if (group == null) {
            // 被拒边：allowed=false + code/msg（不抛 HTTP 错误，前端据此改走作废/召回）
            RollbackEdgePolicy.RollbackReject reject = RollbackEdgePolicy.rejectReason(from, to)
                    .orElse(new RollbackEdgePolicy.RollbackReject(
                            ResultCode.ROLLBACK_ILLEGAL.getCode(), ResultCode.ROLLBACK_ILLEGAL.getMsg()));
            vo.setAllowed(false);
            vo.setCode(reject.code());
            vo.setMsg(reject.msg());
            vo.setIrreversible(from == SampleStatus.S80 || from == SampleStatus.S90);
            vo.setHint(reject.msg());
            return vo;
        }

        vo.setAllowed(true);
        vo.setGroup(group.getCode());
        vo.setGroupLabel(group.getLabel());
        vo.setReasonRequired(true);
        vo.setNeedSensitive(group == RollbackGroup.SENSITIVE);
        vo.setNeedSecondConfirm(RollbackEdgePolicy.needSecondConfirm(from, to));
        vo.setIrreversible(false);
        vo.setInvalidations(rollbackPlanner.invalidationList(sample.getId(), from, to));
        vo.setHint(vo.getInvalidations().isEmpty()
                ? "该回退仅回退状态，无下游数据需要处置；请填写原因后确认。"
                : "回退后将失效上述下游数据（保留留档，可恢复）；请填写原因后确认。");
        return vo;
    }

    // =========================================================================
    // B3 执行回退
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RollbackActionResultVO execute(RollbackExecuteDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        SampleStatus from = sample.getStatus();
        SampleStatus to = requireStatus(dto.getTargetStatus());

        // ① 白名单（非法/跨级 → 4101）
        SampleStatusTransition.assertRollback(from, to);
        // ② 专门拒绝码（4102/4103）双保险：白名单已挡 S80/S90，此处再次显式拒绝
        Optional<RollbackEdgePolicy.RollbackReject> reject = RollbackEdgePolicy.rejectReason(from, to);
        if (reject.isPresent()) {
            throw new BizException(reject.get().code(), reject.get().msg());
        }
        RollbackGroup group = RollbackEdgePolicy.groupOf(from, to);

        // ③ 敏感边：权限 + 二次确认；④ 原因必填
        if (group == RollbackGroup.SENSITIVE) {
            if (!SecurityUtils.hasAuthority(RollbackEdgePolicy.PERM_SENSITIVE)) {
                throw new BizException(ResultCode.ROLLBACK_SENSITIVE_FORBIDDEN.getCode(),
                        ResultCode.ROLLBACK_SENSITIVE_FORBIDDEN.getMsg());
            }
            if (!Boolean.TRUE.equals(dto.getSecondConfirmed())) {
                throw new BizException(ResultCode.ROLLBACK_SECOND_CONFIRM_REQUIRED.getCode(),
                        ResultCode.ROLLBACK_SECOND_CONFIRM_REQUIRED.getMsg());
            }
        }
        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(ResultCode.ROLLBACK_REASON_REQUIRED.getCode(),
                    ResultCode.ROLLBACK_REASON_REQUIRED.getMsg());
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        // ⑤ 快照 sample_info 相关字段（用于恢复）
        String restoredJson = snapshotSample(sample);

        // ⑥ 先插入 sample_rollback 取 rollbackId（供留档关联；状态 UPDATE 失败时整事务回滚）
        SampleRollback rollback = new SampleRollback();
        rollback.setSampleId(sample.getId());
        rollback.setSampleNo(sample.getSampleNo());
        rollback.setFromStatus(from);
        rollback.setToStatus(to);
        rollback.setEdgeGroup(group);
        rollback.setReason(dto.getReason());
        rollback.setSecondConfirmed(group == RollbackGroup.SENSITIVE && Boolean.TRUE.equals(dto.getSecondConfirmed()) ? 1 : 0);
        rollback.setAffectedItemCount(0);
        rollback.setAffectedResultCount(0);
        rollback.setRestoredSampleJson(restoredJson);
        rollback.setCanRecover(CAN_RECOVER);
        rollback.setRecovered(0);
        rollback.setOperatedBy(operator);
        rollback.setOperatedAt(now);
        rollbackMapper.insert(rollback);
        Long rollbackId = rollback.getId();

        // ⑦ 状态乐观 UPDATE（WHERE id=? AND status=旧值；updated==0 → 4108）
        int updated = updateStatusOptimistic(sample, from, to);
        if (updated == 0) {
            throw new BizException(ResultCode.ROLLBACK_CONFLICT.getCode(),
                    ResultCode.ROLLBACK_CONFLICT.getMsg());
        }

        // ⑧ 失效处置（必须在状态 UPDATE 之后、同一事务内）
        Set<ArchiveTarget> scope = RollbackScope.targets(group, from, to);
        SampleDataDisposer.DispositionResult disposition = new SampleDataDisposer.DispositionResult(0, 0, null);
        if (RollbackScope.invalidatesItems(scope)) {
            disposition = disposition.plus(disposer.invalidateItems(sample.getId(), rollbackId));
        }
        if (RollbackScope.invalidatesResults(scope)) {
            disposition = disposition.plus(disposer.invalidateResults(sample.getId(), rollbackId));
        }
        if (RollbackScope.resetsAssignFields(scope)) {
            disposition = disposition.plus(disposer.resetAssignFields(sample.getId(), rollbackId));
        }

        // ⑨ 回写失效摘要与计数
        SampleRollback patch = new SampleRollback();
        patch.setId(rollbackId);
        patch.setAffectedItemCount(disposition.itemCount());
        patch.setAffectedResultCount(disposition.resultCount());
        patch.setInvalidatedSummary(disposition.summary());
        rollbackMapper.updateById(patch);

        // ⑩ 追加流水（恰好 1 条 event_type=ROLLBACK）
        statusLogService.append(sample, StatusEventType.ROLLBACK, from, to,
                "回退至" + to.getLabel(), dto.getReason(), SOURCE_ROLLBACK, rollbackId, disposition.summary());

        return buildActionVO(sample, to, rollbackId, Boolean.TRUE, disposition);
    }

    // =========================================================================
    // B4 恢复（撤销一次回退）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RollbackActionResultVO recover(RollbackRecoverDTO dto) {
        SampleRollback rollback = rollbackMapper.selectById(dto.getRollbackId());
        if (rollback == null) {
            throw new BizException(400, "回退记录不存在: id=" + dto.getRollbackId());
        }
        if (Objects.equals(rollback.getRecovered(), RECOVERED)) {
            throw new BizException(ResultCode.ROLLBACK_NOT_RECOVERABLE.getCode(),
                    ResultCode.ROLLBACK_NOT_RECOVERABLE.getMsg());
        }
        if (!Objects.equals(rollback.getCanRecover(), CAN_RECOVER)) {
            throw new BizException(ResultCode.ROLLBACK_NOT_RECOVERABLE.getCode(),
                    ResultCode.ROLLBACK_NOT_RECOVERABLE.getMsg());
        }
        Sample sample = requireSample(rollback.getSampleId());
        // 状态已前进 → 视为已产生新下游数据
        if (sample.getStatus() != rollback.getToStatus()) {
            throw new BizException(ResultCode.ROLLBACK_NOT_RECOVERABLE.getCode(),
                    ResultCode.ROLLBACK_NOT_RECOVERABLE.getMsg());
        }
        // 回退后又新建了明细/结果 → 原路恢复会撞唯一键 → 拒绝
        if (hasNewDownstreamData(rollback)) {
            throw new BizException(ResultCode.ROLLBACK_NOT_RECOVERABLE.getCode(),
                    ResultCode.ROLLBACK_NOT_RECOVERABLE.getMsg());
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        // 状态从 to 回到 from（乐观条件）
        Sample upd = new Sample();
        upd.setStatus(rollback.getFromStatus());
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, rollback.getToStatus()));
        if (updated == 0) {
            throw new BizException(ResultCode.ROLLBACK_CONFLICT.getCode(),
                    ResultCode.ROLLBACK_CONFLICT.getMsg());
        }

        // 恢复下游数据（deleted 复位 + 指派字段回填）
        SampleDataDisposer.DispositionResult disposition = disposer.restoreByRollback(rollback.getId());
        // 恢复被清空的 sample_info 字段（登记确认 / 审核信息）
        restoreSampleFields(sample, rollback.getRestoredSampleJson());

        // 标记已恢复
        SampleRollback patch = new SampleRollback();
        patch.setId(rollback.getId());
        patch.setRecovered(RECOVERED);
        patch.setRecoverBy(operator);
        patch.setRecoverAt(now);
        rollbackMapper.updateById(patch);

        // 追加流水（恢复事件）
        statusLogService.append(sample, StatusEventType.RECOVER, rollback.getToStatus(), rollback.getFromStatus(),
                "恢复至" + rollback.getFromStatus().getLabel(),
                StringUtils.hasText(dto.getReason()) ? dto.getReason() : "撤销回退",
                SOURCE_ROLLBACK, rollback.getId(), disposition.summary());

        return buildActionVO(sample, rollback.getFromStatus(), rollback.getId(), Boolean.FALSE, disposition);
    }

    // =========================================================================
    // B1 时间线
    // =========================================================================

    @Override
    public RollbackTimelineVO timeline(Long sampleId) {
        Sample sample = requireSample(sampleId);
        SampleStatus current = sample.getStatus();

        RollbackTimelineVO vo = new RollbackTimelineVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setCurrentStatus(current.getCode());
        vo.setCurrentStatusLabel(current.getLabel());

        Set<SampleStatus> allowed = SampleStatusTransition.rollbackAllowed(current);
        vo.setCanRollbackTo(allowed.stream().map(SampleStatus::getCode).sorted().toList());
        for (SampleStatus target : allowed) {
            RollbackTimelineVO.Edge edge = new RollbackTimelineVO.Edge();
            edge.setFrom(current.getCode());
            edge.setTo(target.getCode());
            RollbackGroup group = RollbackEdgePolicy.groupOf(current, target);
            edge.setGroup(group == null ? null : group.getCode());
            edge.setGroupLabel(group == null ? null : group.getLabel());
            edge.setReasonRequired(true);
            vo.getRollbackEdges().add(edge);
        }

        // 被拒边（静态参考列表，与样例一致）：S80→S70、S90→S80
        addRejected(vo, SampleStatus.S80, SampleStatus.S70);
        addRejected(vo, SampleStatus.S90, SampleStatus.S80);

        // 事件（按 id 升序）；回退/恢复事件补 canRecover/recovered
        List<SampleStatusLog> logs = statusLogService.timeline(sampleId);
        Map<Long, SampleRollback> rollbackMap = loadRollbacks(logs);
        for (SampleStatusLog log : logs) {
            RollbackTimelineVO.Event event = toEvent(log);
            if (log.getRollbackId() != null) {
                SampleRollback rb = rollbackMap.get(log.getRollbackId());
                if (rb != null) {
                    event.setCanRecover(Objects.equals(rb.getCanRecover(), CAN_RECOVER)
                            && !Objects.equals(rb.getRecovered(), RECOVERED));
                    event.setRecovered(Objects.equals(rb.getRecovered(), RECOVERED));
                }
            }
            vo.getEvents().add(event);
        }
        return vo;
    }

    // =========================================================================
    // B5 回退记录分页
    // =========================================================================

    @Override
    public PageResult<RollbackHistoryVO> history(long current, long size, RollbackHistoryQueryDTO query) {
        SampleStatus from = query.getFromStatus() == null ? null : requireStatus(query.getFromStatus());
        SampleStatus to = query.getToStatus() == null ? null : requireStatus(query.getToStatus());
        Page<SampleRollback> page = new Page<>(current, size);
        Page<SampleRollback> result = rollbackMapper.selectPage(page, new LambdaQueryWrapper<SampleRollback>()
                .like(StringUtils.hasText(query.getSampleNo()), SampleRollback::getSampleNo, query.getSampleNo())
                .eq(from != null, SampleRollback::getFromStatus, from)
                .eq(to != null, SampleRollback::getToStatus, to)
                .orderByDesc(SampleRollback::getId));
        return PageResult.of(result, this::toHistoryVO);
    }

    // =========================================================================
    // 内部实现
    // =========================================================================

    /**
     * 状态乐观条件 UPDATE；并清空该回退需要清空的「当前有效值」字段。
     *
     * <p>清空规则：回退到 S10 → 清 {@code confirmed_*}；S70→S60（敏感）→ 清 {@code audit_*}。
     * MP 实体式 update 忽略 null 字段，故「清空」必须显式 {@code set(...)}。</p>
     */
    private int updateStatusOptimistic(Sample sample, SampleStatus from, SampleStatus to) {
        LambdaUpdateWrapper<Sample> wrapper = new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, from);
        if (to == SampleStatus.S10) {
            wrapper.set(Sample::getConfirmedBy, null).set(Sample::getConfirmedAt, null);
        }
        if (from == SampleStatus.S70 && to == SampleStatus.S60) {
            wrapper.set(Sample::getAuditBy, null).set(Sample::getAuditAt, null).set(Sample::getAuditOpinion, null);
        }
        Sample upd = new Sample();
        upd.setStatus(to);
        return sampleMapper.update(upd, wrapper);
    }

    /** 回退后又是否新建了明细 / 结果（恢复前的一致性护栏） */
    private boolean hasNewDownstreamData(SampleRollback rollback) {
        LocalDateTime since = rollback.getOperatedAt();
        Long newItems = sampleItemMapper.selectCount(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, rollback.getSampleId())
                .gt(SampleItem::getCreatedAt, since));
        Long newResults = sampleResultMapper.selectCount(new LambdaQueryWrapper<SampleResult>()
                .eq(SampleResult::getSampleId, rollback.getSampleId())
                .gt(SampleResult::getCreatedAt, since));
        return (newItems != null && newItems > 0) || (newResults != null && newResults > 0);
    }

    /** 快照 sample_info 中被回退可能覆盖的字段（JSON 文本） */
    private String snapshotSample(Sample sample) {
        SampleSnapshot snap = new SampleSnapshot();
        snap.setStatus(sample.getStatus() == null ? null : sample.getStatus().getCode());
        snap.setConfirmedBy(sample.getConfirmedBy());
        snap.setConfirmedAt(sample.getConfirmedAt());
        snap.setAuditBy(sample.getAuditBy());
        snap.setAuditAt(sample.getAuditAt());
        snap.setAuditOpinion(sample.getAuditOpinion());
        snap.setSignBy(sample.getSignBy());
        snap.setSignAt(sample.getSignAt());
        snap.setVoidStatus(sample.getVoidStatus());
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "样品字段快照序列化失败：" + e.getOriginalMessage());
        }
    }

    /** 恢复被回退清空的 sample_info 字段（登记确认 / 审核信息） */
    private void restoreSampleFields(Sample sample, String restoredJson) {
        if (!StringUtils.hasText(restoredJson)) {
            return;
        }
        SampleSnapshot snap;
        try {
            snap = objectMapper.readValue(restoredJson, SampleSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "样品字段快照反序列化失败：" + e.getOriginalMessage());
        }
        sampleMapper.update(null, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .set(Sample::getConfirmedBy, snap.getConfirmedBy())
                .set(Sample::getConfirmedAt, snap.getConfirmedAt())
                .set(Sample::getAuditBy, snap.getAuditBy())
                .set(Sample::getAuditAt, snap.getAuditAt())
                .set(Sample::getAuditOpinion, snap.getAuditOpinion())
                .set(Sample::getSignBy, snap.getSignBy())
                .set(Sample::getSignAt, snap.getSignAt())
                .set(Sample::getVoidStatus, snap.getVoidStatus()));
    }

    private Map<Long, SampleRollback> loadRollbacks(List<SampleStatusLog> logs) {
        List<Long> ids = logs.stream()
                .map(SampleStatusLog::getRollbackId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SampleRollback> map = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            rollbackMapper.selectList(new LambdaQueryWrapper<SampleRollback>()
                            .in(SampleRollback::getId, ids))
                    .forEach(r -> map.put(r.getId(), r));
        }
        return map;
    }

    private void addRejected(RollbackTimelineVO vo, SampleStatus from, SampleStatus to) {
        RollbackEdgePolicy.rejectReason(from, to).ifPresent(reject -> {
            RollbackTimelineVO.Rejected item = new RollbackTimelineVO.Rejected();
            item.setFrom(from.getCode());
            item.setTo(to.getCode());
            item.setCode(reject.code());
            item.setMsg(reject.msg());
            vo.getRejectedEdges().add(item);
        });
    }

    private RollbackTimelineVO.Event toEvent(SampleStatusLog log) {
        RollbackTimelineVO.Event event = new RollbackTimelineVO.Event();
        event.setId(log.getId());
        event.setEventType(log.getEventType() == null ? null : log.getEventType().getCode());
        event.setEventTypeLabel(log.getEventTypeLabel());
        event.setFromStatus(log.getFromStatus() == null ? null : log.getFromStatus().getCode());
        event.setFromStatusLabel(log.getFromStatusLabel());
        event.setToStatus(log.getToStatus() == null ? null : log.getToStatus().getCode());
        event.setToStatusLabel(log.getToStatusLabel());
        event.setActionLabel(log.getActionLabel());
        event.setReason(log.getReason());
        event.setDataDisposition(log.getDataDisposition());
        event.setRollbackId(log.getRollbackId());
        event.setSource(log.getSource());
        event.setOperatedBy(log.getOperatedBy());
        event.setOperatedAt(log.getOperatedAt());
        return event;
    }

    private RollbackHistoryVO toHistoryVO(SampleRollback rollback) {
        RollbackHistoryVO vo = new RollbackHistoryVO();
        vo.setId(rollback.getId());
        vo.setSampleId(rollback.getSampleId());
        vo.setSampleNo(rollback.getSampleNo());
        vo.setFromStatus(rollback.getFromStatus() == null ? null : rollback.getFromStatus().getCode());
        vo.setFromStatusLabel(rollback.getFromStatusLabel());
        vo.setToStatus(rollback.getToStatus() == null ? null : rollback.getToStatus().getCode());
        vo.setToStatusLabel(rollback.getToStatusLabel());
        vo.setEdgeGroup(rollback.getEdgeGroup() == null ? null : rollback.getEdgeGroup().getCode());
        vo.setEdgeGroupLabel(rollback.getEdgeGroupLabel());
        vo.setReason(rollback.getReason());
        vo.setSecondConfirmed(rollback.getSecondConfirmed());
        vo.setAffectedItemCount(rollback.getAffectedItemCount());
        vo.setAffectedResultCount(rollback.getAffectedResultCount());
        vo.setCanRecover(rollback.getCanRecover());
        vo.setRecovered(rollback.getRecovered());
        vo.setOperatedBy(rollback.getOperatedBy());
        vo.setOperatedAt(rollback.getOperatedAt());
        vo.setRecoverBy(rollback.getRecoverBy());
        vo.setRecoverAt(rollback.getRecoverAt());
        return vo;
    }

    private RollbackActionResultVO buildActionVO(Sample sample, SampleStatus status, Long rollbackId,
                                                 Boolean canRecover, SampleDataDisposer.DispositionResult disposition) {
        RollbackActionResultVO vo = new RollbackActionResultVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setStatus(status.getCode());
        vo.setStatusLabel(status.getLabel());
        vo.setRollbackId(rollbackId);
        vo.setCanRecover(canRecover);
        vo.setAffectedItemCount(disposition.itemCount());
        vo.setAffectedResultCount(disposition.resultCount());
        vo.setInvalidatedSummary(disposition.summary());
        return vo;
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

    private SampleStatus requireStatus(Integer code) {
        SampleStatus status = SampleStatus.ofNullable(code);
        if (status == null) {
            throw new BizException(400, "非法的样品状态编码: " + code);
        }
        return status;
    }

    /** sample_info 关键字段快照（restored_sample_json 的结构） */
    @lombok.Data
    public static class SampleSnapshot {

        private Integer status;

        private String confirmedBy;

        private LocalDateTime confirmedAt;

        private String auditBy;

        private LocalDateTime auditAt;

        private String auditOpinion;

        private String signBy;

        private LocalDateTime signAt;

        private Integer voidStatus;
    }
}
