package com.lims.service;

import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.entity.Sample;
import com.lims.entity.SampleStatusLog;

import java.util.List;

/**
 * 样品状态流水服务（feature B，T02）。
 *
 * <p><b>统一写入口（唯一）</b>：既有 9 处状态流转 + 新增回退/恢复/作废，全部经
 * {@link #append} 追加一条「从哪到哪」事件。禁止任何其它代码直接写 `sample_status_log`。</p>
 *
 * <p><b>只追加、永不改写</b>：本接口不提供 update / delete 方法。audit 域动作**双写**——
 * `sample_audit_log` 由原调用方保持写入不变，本服务只负责统一流水，二者互为补充。</p>
 */
public interface SampleStatusLogService {

    /**
     * 追加一条状态流水（调用方须在**同一事务**内、且状态 UPDATE 已成功之后调用）。
     *
     * @param sample      样品（取 id / sampleNo；须非空）
     * @param eventType   事件类型（正向/退回/签发/回退/恢复/作废/报告）
     * @param from        变更前状态
     * @param to          变更后状态
     * @param actionLabel 人类可读动作（如「登记确认」「回退至已安排」）
     * @param reason      原因（回退/退回/作废必填，正向可空）
     * @param source      入口来源：SAMPLE/ITEM/ASSIGN/RESULT/AUDIT/REPORT/ROLLBACK_PANEL
     * @param rollbackId  关联 sample_rollback.id（回退/恢复事件），其余传 null
     * @param disposition 下游数据处置摘要，无则传 null
     */
    void append(Sample sample, StatusEventType eventType, SampleStatus from, SampleStatus to,
                String actionLabel, String reason, String source, Long rollbackId, String disposition);

    /**
     * 按样品查询全链路事件（时间线），按 id 升序（发生顺序）。
     */
    List<SampleStatusLog> timeline(Long sampleId);
}
