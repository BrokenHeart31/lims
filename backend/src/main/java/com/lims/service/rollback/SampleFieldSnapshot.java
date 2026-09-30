package com.lims.service.rollback;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.mapper.SampleMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * `sample_info` 关键字段快照 / 恢复（feature B，2026-09-30 从 {@code RollbackServiceImpl} 抽出）。
 *
 * <p><b>为什么需要它</b>：回退不仅改状态，还可能**清空**「当前有效值」字段——
 * S20→S10 清 {@code confirmed_*}、S70→S60 清 {@code audit_*}。这些被清掉的值是
 * 「谁在何时确认/审核过」的当前事实，撤销回退（recover）必须能原样放回，
 * 否则一次误操作 + 恢复会让审核人凭空消失（报告署名为空）。</p>
 *
 * <p><b>为什么抽成独立组件</b>：链路执行（{@link RollbackExecutor}）与恢复
 * （{@code RollbackServiceImpl#recover}）都要用同一套字段口径；若各写一份，
 * 将来给快照加字段时必然只改一处，导致恢复丢字段且**很难被发现**
 * （表现为「恢复了但审核信息没了」）。</p>
 *
 * <p>⚠️ MP 实体式 {@code update} 会忽略 null 字段，故「清空/回填」一律
 * {@code LambdaUpdateWrapper.set(col, value)} 显式指定。</p>
 */
@Component
@RequiredArgsConstructor
public class SampleFieldSnapshot {

    private final SampleMapper sampleMapper;

    private final ObjectMapper objectMapper;

    /** 捕获回退前需要保护的字段（JSON 文本，落 `sample_rollback.restored_sample_json`） */
    public String capture(Sample sample) {
        Snapshot snap = new Snapshot();
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

    /** 把快照中的字段原样放回（撤销回退时调用；空 JSON 视为无需恢复） */
    public void restore(Sample sample, String restoredJson) {
        if (!StringUtils.hasText(restoredJson)) {
            return;
        }
        Snapshot snap;
        try {
            snap = objectMapper.readValue(restoredJson, Snapshot.class);
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

    /** `sample_info` 关键字段快照（`restored_sample_json` 的结构，字段名即 JSON key） */
    @Data
    public static class Snapshot {

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
