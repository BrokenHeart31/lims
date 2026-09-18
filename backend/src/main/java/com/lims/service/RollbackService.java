package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.RollbackExecuteDTO;
import com.lims.dto.RollbackHistoryQueryDTO;
import com.lims.dto.RollbackPreviewDTO;
import com.lims.dto.RollbackRecoverDTO;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTimelineVO;

/**
 * 流程回溯服务（feature B，T02，api-spec 第 17 章）。
 *
 * <p>状态层「反向补偿」+ 数据层「失效 / 留档 / 恢复」的编排者。所有写方法在事务内，
 * 状态变更一律乐观条件 UPDATE（{@code WHERE id=? AND status=旧值}，{@code updated==0} → 4108），
 * 失效处置在状态 UPDATE 之后、同一事务内。</p>
 */
public interface RollbackService {

    /** 回退前预览（纯读，不落库；被拒边返回 allowed=false + code/msg） */
    RollbackPreviewVO preview(RollbackPreviewDTO dto);

    /** 执行一次逐级回退 */
    RollbackActionResultVO execute(RollbackExecuteDTO dto);

    /** 恢复某次未产生新下游数据的回退 */
    RollbackActionResultVO recover(RollbackRecoverDTO dto);

    /** 该样品全链路事件时间线（正向 + 逆向） */
    RollbackTimelineVO timeline(Long sampleId);

    /** 分页查询回退记录（跨样品） */
    PageResult<RollbackHistoryVO> history(long current, long size, RollbackHistoryQueryDTO query);
}
