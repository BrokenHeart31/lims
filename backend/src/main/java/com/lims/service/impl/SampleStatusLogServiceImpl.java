package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.entity.SampleStatusLog;
import com.lims.mapper.SampleStatusLogMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.SampleStatusLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 样品状态流水服务实现（feature B，T02）。
 *
 * <p><b>只追加、永不改写</b>：本类只有 insert 与 select，无 update / delete 路径——
 * 这是「审计流水只增不改」的代码级保证（PRD G5 / B-06）。</p>
 *
 * <p><b>为什么 append 要显式收 sample 对象而不是 sample_id</b>：状态流水冗余一份 sample_no，
 * 便于跨样品检索与时间线展示；调用方此刻手里已有样品对象，避免再查一次。</p>
 */
@Service
@RequiredArgsConstructor
public class SampleStatusLogServiceImpl implements SampleStatusLogService {

    private final SampleStatusLogMapper sampleStatusLogMapper;

    @Override
    public void append(Sample sample, StatusEventType eventType, SampleStatus from, SampleStatus to,
                       String actionLabel, String reason, String source, Long rollbackId, String disposition) {
        if (sample == null || sample.getId() == null) {
            throw new BizException(400, "状态流水写入失败：样品为空");
        }
        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        SampleStatusLog log = new SampleStatusLog();
        log.setSampleId(sample.getId());
        log.setSampleNo(sample.getSampleNo());
        log.setEventType(eventType);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setActionLabel(actionLabel);
        log.setReason(reason);
        log.setRollbackId(rollbackId);
        log.setDataDisposition(disposition);
        log.setSource(source);
        log.setOperatedBy(operator);
        log.setOperatedAt(now);
        // 审计四字段（created_by/at、updated_by/at）由 AuditMetaObjectHandler 自动填充，业务不手工赋值
        sampleStatusLogMapper.insert(log);
    }

    @Override
    public List<SampleStatusLog> timeline(Long sampleId) {
        if (sampleId == null) {
            throw new BizException(400, "样品ID不能为空");
        }
        return sampleStatusLogMapper.selectList(new LambdaQueryWrapper<SampleStatusLog>()
                .eq(SampleStatusLog::getSampleId, sampleId)
                .orderByAsc(SampleStatusLog::getId));
    }
}
