package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.ResultCode;
import com.lims.common.enums.RollbackEdgePolicy;
import com.lims.common.enums.RollbackGroup;
import com.lims.common.enums.SampleStatus;
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
import com.lims.service.rollback.RollbackExecutor;
import com.lims.service.rollback.RollbackPlanner;
import com.lims.service.rollback.SampleDataDisposer;
import com.lims.service.rollback.SampleFieldSnapshot;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackBatchResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTargetsVO;
import com.lims.vo.RollbackTimelineVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 流程回溯服务实现（feature B，2026-09-30 改造为「环节内嵌 + 可选目标步 + 批量」）。
 *
 * <p><b>职责边界</b>：本类只做「读路径 + 批量编排」——目标步推导、影响预览、时间线、历史分页、
 * 批量循环与逐条结果汇总。**真正的每次回退写操作在 {@link RollbackExecutor}**
 * （独立 Bean，`REQUIRES_NEW` 事务），原因见该类注释（自调用不会开启新事务）。</p>
 *
 * <p><b>四条不变式的落点</b>：
 * <ul>
 *   <li>①②（乐观 UPDATE / 失效处置在状态变更之后且同事务）→ {@link RollbackExecutor}；</li>
 *   <li>③（**每级恰好 1 条** {@code event_type=4} 流水 + 整批 1 行 {@code sample_rollback}）→ 同上；</li>
 *   <li>④（绝不物理删除）→ {@link SampleDataDisposer}（本类不直接写明细/结果表）。</li>
 * </ul>
 * 本类**不持有** {@code SampleItemMapper}/{@code SampleResultMapper} 的写路径，只用于恢复前的
 * 一致性护栏读取（{@link #hasNewDownstreamData}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollbackServiceImpl implements RollbackService {

    /** can_recover = 是 */
    private static final int CAN_RECOVER = 1;
    /** recovered = 是 */
    private static final int RECOVERED = 1;

    private final SampleMapper sampleMapper;
    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;
    private final SampleRollbackMapper rollbackMapper;
    private final SampleDataDisposer disposer;
    private final SampleStatusLogService statusLogService;
    private final RollbackPlanner rollbackPlanner;
    private final RollbackExecutor rollbackExecutor;
    private final SampleFieldSnapshot sampleFieldSnapshot;

    // =========================================================================
    // B7 可回退目标步 + 影响预览（纯读，不落库）
    // =========================================================================

    @Override
    public RollbackTargetsVO targets(Long sampleId) {
        Sample sample = requireSample(sampleId);
        SampleStatus from = sample.getStatus();

        RollbackTargetsVO vo = new RollbackTargetsVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setCurrentStatus(from.getCode());
        vo.setCurrentStatusLabel(from.getLabel());

        List<SampleStatus> reachable = RollbackEdgePolicy.reachableTargets(from);
        vo.setRollbackAvailable(!reachable.isEmpty());
        for (SampleStatus target : reachable) {
            vo.getTargets().add(buildTarget(sample.getId(), from, target));
        }

        // 被拒说明：S80/S90 无任何回退路径，引导用户改走「作废 / 召回」
        RollbackEdgePolicy.noPathHint(from).ifPresent(reject -> {
            RollbackTargetsVO.Rejected item = new RollbackTargetsVO.Rejected();
            item.setFrom(from.getCode());
            item.setCode(reject.code());
            item.setMsg(reject.msg());
            vo.getRejected().add(item);
        });
        return vo;
    }

    private RollbackTargetsVO.Target buildTarget(Long sampleId, SampleStatus from, SampleStatus target) {
        List<SampleStatus> chain = RollbackEdgePolicy.chain(from, target);
        RollbackGroup group = RollbackEdgePolicy.chainGroup(from, chain);

        RollbackTargetsVO.Target item = new RollbackTargetsVO.Target();
        item.setStatus(target.getCode());
        item.setStatusLabel(target.getLabel());
        item.setStepCount(chain.size());
        item.setChainCodes(RollbackEdgePolicy.chainCodes(chain));
        item.setChainLabels(RollbackEdgePolicy.chainLabels(chain));
        item.setChainText(RollbackEdgePolicy.chainText(from, chain));
        item.setGroup(group == null ? null : group.getCode());
        item.setGroupLabel(group == null ? null : group.getLabel());
        item.setNeedSensitive(group == RollbackGroup.SENSITIVE);
        item.setNeedSecondConfirm(group == RollbackGroup.SENSITIVE);

        List<RollbackPreviewVO.Invalidation> invalidations =
                rollbackPlanner.invalidationList(sampleId, from, chain);
        item.setInvalidations(invalidations);
        int total = invalidations.stream().mapToInt(RollbackPreviewVO.Invalidation::getCount).sum();
        item.setInvalidatedTotal(total);
        item.setHint(total == 0
                ? "该回退仅回退状态，无下游数据需要处置。"
                : "回退后将失效上述下游数据（保留留档，可撤销）。");
        return item;
    }

    // =========================================================================
    // B2 单目标步预览（纯读，不落库）
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

        var reject = RollbackEdgePolicy.rejectReason(from, to);
        if (reject.isPresent()) {
            // 被拒：allowed=false + code/msg（不抛 HTTP 错误，前端据此改走作废/召回）
            vo.setAllowed(false);
            vo.setCode(reject.get().code());
            vo.setMsg(reject.get().msg());
            vo.setIrreversible(from == SampleStatus.S80 || from == SampleStatus.S90);
            vo.setHint(reject.get().msg());
            return vo;
        }

        List<SampleStatus> chain = RollbackEdgePolicy.chain(from, to);
        RollbackGroup group = RollbackEdgePolicy.chainGroup(from, chain);
        vo.setAllowed(true);
        vo.setGroup(group == null ? null : group.getCode());
        vo.setGroupLabel(group == null ? null : group.getLabel());
        vo.setReasonRequired(true);
        vo.setNeedSensitive(group == RollbackGroup.SENSITIVE);
        vo.setNeedSecondConfirm(group == RollbackGroup.SENSITIVE);
        vo.setIrreversible(false);
        vo.setStepCount(chain.size());
        vo.setChainCodes(RollbackEdgePolicy.chainCodes(chain));
        vo.setChainLabels(RollbackEdgePolicy.chainLabels(chain));
        vo.setChainText(RollbackEdgePolicy.chainText(from, chain));
        vo.setInvalidations(rollbackPlanner.invalidationList(sample.getId(), from, chain));
        vo.setHint(vo.getInvalidations().isEmpty()
                ? "该回退仅回退状态，无下游数据需要处置；请填写原因后确认。"
                : "回退后将失效上述下游数据（保留留档，可撤销）；请填写原因后确认。");
        return vo;
    }

    // =========================================================================
    // B3 批量执行回退（逐条独立事务）
    // =========================================================================

    @Override
    public RollbackBatchResultVO execute(RollbackExecuteDTO dto) {
        SampleStatus target = requireStatus(dto.getTargetStatus());
        List<Long> ids = dedupe(dto.getIds());

        RollbackBatchResultVO vo = new RollbackBatchResultVO();
        vo.setTargetStatus(target.getCode());
        vo.setTargetStatusLabel(target.getLabel());
        vo.setTotal(ids.size());

        int success = 0;
        int fail = 0;
        for (Long sampleId : ids) {
            try {
                RollbackActionResultVO result = rollbackExecutor.executeOne(
                        sampleId, target, dto.getReason(), dto.getSecondConfirmed());
                vo.getItems().add(RollbackBatchResultVO.Item.ok(result.getSampleId(), result.getSampleNo(), result));
                success++;
            } catch (BizException e) {
                // 业务失败：逐条留痕并回传可展示原因（绝不静默跳过）
                vo.getItems().add(RollbackBatchResultVO.Item.fail(sampleId, null, e.getCode(), e.getMessage()));
                fail++;
            } catch (Exception e) {
                // 非预期失败：同样逐条回传（否则用户会以为这条「没反应」）
                log.warn("回退失败（非业务异常）：sampleId={}", sampleId, e);
                vo.getItems().add(RollbackBatchResultVO.Item.fail(
                        sampleId, null, ResultCode.ERROR.getCode(), "回退失败：" + e.getMessage()));
                fail++;
            }
        }
        vo.setSuccessCount(success);
        vo.setFailCount(fail);
        return vo;
    }

    // =========================================================================
    // B4 撤销回退（恢复）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RollbackActionResultVO recover(RollbackRecoverDTO dto) {
        SampleRollback rollback = rollbackMapper.selectById(dto.getRollbackId());
        if (rollback == null) {
            throw new BizException(400, "回退记录不存在: id=" + dto.getRollbackId());
        }
        if (Objects.equals(rollback.getRecovered(), RECOVERED)
                || !Objects.equals(rollback.getCanRecover(), CAN_RECOVER)) {
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

        // 状态从 to 一步回到 from（乐观条件）。
        // 说明：跨级回退的「撤销」也是一步复位——它的语义是「把快照放回去」，不是业务推进，
        // 因此不走 VALID 正向白名单，也不产生 N 条中间态流水（中间态从未被业务观察过）。
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
        sampleFieldSnapshot.restore(sample, rollback.getRestoredSampleJson());

        // 标记已恢复
        SampleRollback patch = new SampleRollback();
        patch.setId(rollback.getId());
        patch.setRecovered(RECOVERED);
        patch.setRecoverBy(operator);
        patch.setRecoverAt(now);
        rollbackMapper.updateById(patch);

        // 追加流水（恢复事件；与回退批次同批次号，便于时间线按批次聚合）
        statusLogService.append(sample, com.lims.common.enums.StatusEventType.RECOVER,
                rollback.getToStatus(), rollback.getFromStatus(),
                "撤销回退至" + rollback.getFromStatus().getLabel(),
                StringUtils.hasText(dto.getReason()) ? dto.getReason() : "撤销回退",
                RollbackExecutor.SOURCE_ROLLBACK, rollback.getId(), rollback.getBatchNo(),
                disposition.summary());

        RollbackActionResultVO vo = new RollbackActionResultVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setStatus(rollback.getFromStatus().getCode());
        vo.setStatusLabel(rollback.getFromStatus().getLabel());
        vo.setRollbackId(rollback.getId());
        vo.setBatchNo(rollback.getBatchNo());
        vo.setStepCount(rollback.getStepCount());
        vo.setChainText(rollback.getFromStatus().getLabel());
        vo.setCanRecover(false);
        vo.setAffectedItemCount(disposition.itemCount());
        vo.setAffectedResultCount(disposition.resultCount());
        vo.setInvalidatedSummary(disposition.summary());
        return vo;
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

        // 可达目标步（含跨级）：与 B7 同源，避免两处口径漂移
        List<SampleStatus> reachable = RollbackEdgePolicy.reachableTargets(current);
        vo.setCanRollbackTo(reachable.stream().map(SampleStatus::getCode).sorted().toList());
        for (SampleStatus target : reachable) {
            List<SampleStatus> chain = RollbackEdgePolicy.chain(current, target);
            RollbackGroup group = RollbackEdgePolicy.chainGroup(current, chain);
            RollbackTimelineVO.Edge edge = new RollbackTimelineVO.Edge();
            edge.setFrom(current.getCode());
            edge.setTo(target.getCode());
            edge.setStepCount(chain.size());
            edge.setGroup(group == null ? null : group.getCode());
            edge.setGroupLabel(group == null ? null : group.getLabel());
            edge.setReasonRequired(true);
            vo.getRollbackEdges().add(edge);
        }

        // 被拒说明（静态参考）：S80/S90 出发
        addRejected(vo, current);

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

    /** 回退后又是否新建了明细 / 结果（撤销回退前的一致性护栏） */
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

    private void addRejected(RollbackTimelineVO vo, SampleStatus current) {
        RollbackEdgePolicy.noPathHint(current).ifPresent(reject -> {
            RollbackTimelineVO.Rejected item = new RollbackTimelineVO.Rejected();
            item.setFrom(current.getCode());
            item.setCode(reject.code());
            item.setMsg(reject.msg());
            vo.getRejectedEdges().add(item);
        });
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
        event.setBatchNo(log.getBatchNo());
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
        vo.setBatchNo(rollback.getBatchNo());
        vo.setStepCount(rollback.getStepCount());
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

    /** 去重且保序：同一 id 出现两次会让「1 成功 + 1 失败(4108)」这种结果看起来像 bug */
    private List<Long> dedupe(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(400, "请至少选择一个样品");
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
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
}
