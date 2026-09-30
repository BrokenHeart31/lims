package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.RollbackExecuteDTO;
import com.lims.dto.RollbackHistoryQueryDTO;
import com.lims.dto.RollbackPreviewDTO;
import com.lims.dto.RollbackRecoverDTO;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackBatchResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTargetsVO;
import com.lims.vo.RollbackTimelineVO;

/**
 * 流程回溯服务（feature B，api-spec 第 17 章）。
 *
 * <p>状态层「反向补偿」+ 数据层「失效 / 留档 / 恢复」的编排者。所有写方法在事务内，
 * 状态变更一律乐观条件 UPDATE（{@code WHERE id=? AND status=旧值}，{@code updated==0} → 4108），
 * 失效处置在状态 UPDATE 之后、同一事务内。</p>
 *
 * <p>2026-09-30 改造：回退能力从「独立功能区」下沉为**环节内嵌入口**，并支持
 * <b>可选目标步（跨级链式）</b>与<b>同环节批量</b>。为此接口新增
 * {@link #targets(Long)}（一次拿到全部可选目标步 + 影响预览），并把 {@link #execute}
 * 收敛为批量语义。</p>
 */
public interface RollbackService {

    /** 该样品**可达的全部回退目标步** + 每步的下游影响预览（纯读、不落库，api-spec B7） */
    RollbackTargetsVO targets(Long sampleId);

    /** 单目标步预览（纯读，不落库；被拒目标返回 allowed=false + code/msg） */
    RollbackPreviewVO preview(RollbackPreviewDTO dto);

    /** 批量执行回退（`ids` 可含多个同状态样品；逐条独立事务，返回逐条明细） */
    RollbackBatchResultVO execute(RollbackExecuteDTO dto);

    /** 撤销某次未产生新下游数据的回退 */
    RollbackActionResultVO recover(RollbackRecoverDTO dto);

    /** 该样品全链路事件时间线（正向 + 逆向） */
    RollbackTimelineVO timeline(Long sampleId);

    /** 分页查询回退记录（跨样品） */
    PageResult<RollbackHistoryVO> history(long current, long size, RollbackHistoryQueryDTO query);
}
