package com.lims.service.rollback;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lims.common.ResultCode;
import com.lims.common.enums.ArchiveTarget;
import com.lims.common.enums.RollbackEdgePolicy;
import com.lims.common.enums.RollbackGroup;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.entity.SampleRollback;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleRollbackMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.SampleStatusLogService;
import com.lims.vo.RollbackActionResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单样品回退执行器（feature B，2026-09-30 从 {@code RollbackServiceImpl} 抽出）。
 *
 * <p><b>为什么独立成 Bean</b>：批量回退要求「逐条独立事务、单条失败不影响其他」。
 * 若把循环与执行写在同一个方法里，只有两个坏选择——整个批量一个事务（一条失败全部回滚，
 * 用户拿到的是「全失败」，无法知道哪几条本来能成），或者在同 Bean 内自调用
 * （Spring 事务基于代理，<b>自调用不会开启新事务</b>，注解静默失效）。
 * 独立 Bean + 跨 Bean 调用让事务边界**由代码结构表达**，而不是靠调用者小心翼翼。</p>
 *
 * <p><b>传播行为 REQUIRES_NEW</b>：即使将来有人从一个事务方法里调用本执行器，
 * 每条样品仍各自独立提交/回滚——这正是「逐条独立」的字面含义。</p>
 *
 * <p><b>四条不变式（逐级保持，跨级同样成立）</b>：
 * <ol>
 *   <li>状态变更用**乐观条件 UPDATE**（{@code WHERE id=? AND status=旧值}），{@code updated==0} → 4108；</li>
 *   <li>下游**失效处置在状态 UPDATE 之后、同一事务内**；</li>
 *   <li>**每级恰好新增 1 条** {@code sample_status_log(event_type=4)}，同批次号；
 *       整个批次只在 {@code sample_rollback} 落 **1 行**（from=起点，to=最终目标步）；</li>
 *   <li>**绝不物理删除**任何业务历史（失效一律经 {@link SampleDataDisposer} 显式 {@code set(deleted, id)}）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RollbackExecutor {

    /** 流水来源：环节内嵌回退入口（与 SAMPLE/ITEM/ASSIGN/RESULT/AUDIT/REPORT 区分） */
    public static final String SOURCE_ROLLBACK = "ROLLBACK_PANEL";

    /** can_recover = 是 */
    private static final int CAN_RECOVER = 1;
    /** recovered = 否 */
    private static final int NOT_RECOVERED = 0;

    /** 批次号时间部分格式（到毫秒，配合随机后缀保证唯一） */
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final SampleMapper sampleMapper;
    private final SampleRollbackMapper rollbackMapper;
    private final SampleDataDisposer disposer;
    private final SampleStatusLogService statusLogService;
    private final SampleFieldSnapshot sampleFieldSnapshot;

    /**
     * 对**单个样品**执行一次回退（可跨级，内部逐级执行；整条链在同一事务内，任一级失败整体回滚）。
     *
     * @param sampleId       样品 id
     * @param target         目标步（已由调用方解析为合法枚举）
     * @param reason         回退原因（必填）
     * @param secondConfirmed 敏感链路是否已二次确认
     * @return 执行结果（含批次号、级数、链路文案、失效计数）
     * @throws BizException 校验失败（4101/4102/4103/4104/4105/4106）或并发冲突（4108）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RollbackActionResultVO executeOne(Long sampleId, SampleStatus target, String reason,
                                             Boolean secondConfirmed) {
        Sample sample = requireSample(sampleId);
        SampleStatus from = sample.getStatus();

        // ① 被拒边（S80/S90 → 4102/4103）与可达性（4101）
        Optional<RollbackEdgePolicy.RollbackReject> reject = RollbackEdgePolicy.rejectReason(from, target);
        if (reject.isPresent()) {
            throw new BizException(reject.get().code(), reject.get().msg());
        }
        List<SampleStatus> chain = RollbackEdgePolicy.chain(from, target);
        if (chain.isEmpty()) {
            throw new BizException(ResultCode.ROLLBACK_ILLEGAL.getCode(),
                    ResultCode.ROLLBACK_ILLEGAL.getMsg());
        }
        // ② 逐级断言「单步」合法性。
        //    链是从白名单推出来的，故这一步在数学上冗余；但**刻意保留**：链推导逻辑一旦被改动，
        //    这里会立刻 fail-loud 而不是静默产生一个「白名单不允许的边」被执行。
        //    不变式③（每级 1 条流水）的断言因此也能确定地指向「合法的边」。
        SampleStatus cursor = from;
        for (SampleStatus step : chain) {
            SampleStatusTransition.assertRollback(cursor, step);
            cursor = step;
        }

        // ③ 敏感链路（含 S70→S60 任一级）：权限 + 二次确认；④ 原因必填
        RollbackGroup group = RollbackEdgePolicy.chainGroup(from, chain);
        if (group == RollbackGroup.SENSITIVE) {
            if (!SecurityUtils.hasAuthority(RollbackEdgePolicy.PERM_SENSITIVE)) {
                throw new BizException(ResultCode.ROLLBACK_SENSITIVE_FORBIDDEN.getCode(),
                        ResultCode.ROLLBACK_SENSITIVE_FORBIDDEN.getMsg());
            }
            if (!Boolean.TRUE.equals(secondConfirmed)) {
                throw new BizException(ResultCode.ROLLBACK_SECOND_CONFIRM_REQUIRED.getCode(),
                        ResultCode.ROLLBACK_SECOND_CONFIRM_REQUIRED.getMsg());
            }
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.ROLLBACK_REASON_REQUIRED.getCode(),
                    ResultCode.ROLLBACK_REASON_REQUIRED.getMsg());
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();
        String batchNo = newBatchNo();

        // ⑤ 快照 sample_info 相关字段（供撤销回退时原样放回）
        String restoredJson = sampleFieldSnapshot.capture(sample);

        // ⑥ 先插入 sample_rollback 取 rollbackId（整批 1 行，记录起点与最终目标步）
        SampleRollback rollback = new SampleRollback();
        rollback.setSampleId(sample.getId());
        rollback.setSampleNo(sample.getSampleNo());
        rollback.setBatchNo(batchNo);
        rollback.setFromStatus(from);
        rollback.setToStatus(target);
        rollback.setStepCount(chain.size());
        rollback.setEdgeGroup(group);
        rollback.setReason(reason);
        rollback.setSecondConfirmed(group == RollbackGroup.SENSITIVE && Boolean.TRUE.equals(secondConfirmed) ? 1 : 0);
        rollback.setAffectedItemCount(0);
        rollback.setAffectedResultCount(0);
        rollback.setRestoredSampleJson(restoredJson);
        rollback.setCanRecover(CAN_RECOVER);
        rollback.setRecovered(NOT_RECOVERED);
        rollback.setOperatedBy(operator);
        rollback.setOperatedAt(now);
        rollbackMapper.insert(rollback);
        Long rollbackId = rollback.getId();

        // ⑦ 逐级执行：状态乐观 UPDATE → 失效处置（同一事务） → 追加 1 条本级流水
        SampleDataDisposer.DispositionResult disposition = new SampleDataDisposer.DispositionResult(0, 0, null);
        cursor = from;
        for (SampleStatus step : chain) {
            int updated = updateStatusOptimistic(sample, cursor, step);
            if (updated == 0) {
                // 并发下状态已被他人推进：整条链回滚（前面已执行的级一并撤销，不留半程状态）
                throw new BizException(ResultCode.ROLLBACK_CONFLICT.getCode(),
                        ResultCode.ROLLBACK_CONFLICT.getMsg());
            }
            Set<ArchiveTarget> scope = RollbackScope.targets(RollbackEdgePolicy.groupOf(cursor, step), cursor, step);
            SampleDataDisposer.DispositionResult stepDisposition = new SampleDataDisposer.DispositionResult(0, 0, null);
            if (RollbackScope.invalidatesItems(scope)) {
                stepDisposition = stepDisposition.plus(disposer.invalidateItems(sample.getId(), rollbackId));
            }
            if (RollbackScope.invalidatesResults(scope)) {
                stepDisposition = stepDisposition.plus(disposer.invalidateResults(sample.getId(), rollbackId));
            }
            if (RollbackScope.resetsAssignFields(scope)) {
                stepDisposition = stepDisposition.plus(disposer.resetAssignFields(sample.getId(), rollbackId));
            }
            // 不变式③（跨级版）：**每级恰好 1 条** event_type=4 流水，同批次号
            statusLogService.append(sample, StatusEventType.ROLLBACK, cursor, step,
                    "回退至" + step.getLabel(), reason, SOURCE_ROLLBACK, rollbackId, batchNo,
                    stepDisposition.summary());
            disposition = disposition.plus(stepDisposition);
            cursor = step;
        }

        // ⑧ 回写失效摘要与计数（整批汇总）
        SampleRollback patch = new SampleRollback();
        patch.setId(rollbackId);
        patch.setAffectedItemCount(disposition.itemCount());
        patch.setAffectedResultCount(disposition.resultCount());
        patch.setInvalidatedSummary(disposition.summary());
        rollbackMapper.updateById(patch);

        log.info("回退批次 {} 完成：sample={} {} -> {}（{} 级，失效明细 {} / 结果 {}）",
                batchNo, sample.getSampleNo(), from.getLabel(), target.getLabel(), chain.size(),
                disposition.itemCount(), disposition.resultCount());

        return buildActionVO(sample, target, rollbackId, batchNo, chain, true, disposition);
    }

    // =========================================================================
    // 内部实现
    // =========================================================================

    /**
     * 状态乐观条件 UPDATE；并清空该级回退需要清空的「当前有效值」字段。
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

    /** 批次号：`RB` + 毫秒时间戳 + 4 位随机（唯一性由时间戳 + 随机共同保证，长度 < 32） */
    private String newBatchNo() {
        return "RB" + LocalDateTime.now().format(BATCH_FMT)
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000)).toUpperCase();
    }

    private RollbackActionResultVO buildActionVO(Sample sample, SampleStatus status, Long rollbackId,
                                                 String batchNo, List<SampleStatus> chain, Boolean canRecover,
                                                 SampleDataDisposer.DispositionResult disposition) {
        RollbackActionResultVO vo = new RollbackActionResultVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setStatus(status.getCode());
        vo.setStatusLabel(status.getLabel());
        vo.setRollbackId(rollbackId);
        vo.setBatchNo(batchNo);
        vo.setStepCount(chain.size());
        vo.setChainText(RollbackEdgePolicy.chainText(sample.getStatus(), chain));
        vo.setCanRecover(canRecover);
        vo.setAffectedItemCount(disposition.itemCount());
        vo.setAffectedResultCount(disposition.resultCount());
        vo.setInvalidatedSummary(disposition.summary());
        return vo;
    }
}
