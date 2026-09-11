package com.lims.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.vo.AssignAutoResultVO;
import com.lims.vo.AssignDetailVO;
import com.lims.vo.AssignPendingVO;

/**
 * 检验任务安排服务（T-501，阶段五）—— 契约见 docs/api/api-spec.md 第 5 章。
 *
 * <p>把已分解（S30）样品的每个检测单项指派给有资格的检验员，确认后流转 S30 → S40。</p>
 */
public interface AssignService {

    /** 5.2 分页查询待安排样品（status=S30 且已分解出明细） */
    Page<AssignPendingVO> pagePending(long pageNum, long pageSize, String sampleNo, String sampleName);

    /** 5.3 查询样品安排明细（含检测单项、指派结果与有资质候选） */
    AssignDetailVO detail(Long sampleId);

    /** 5.4 执行自动分配（可重跑；已人工改派的项不覆盖） */
    AssignAutoResultVO autoAssign(Long sampleId);

    /** 5.5 人工改派（仅允许有资质者） */
    AssignDetailVO.Item reassign(Long itemId, String testerNo);

    /** 5.6 安排确认：S30 → S40（要求全部单项已指派） */
    int confirm(Long sampleId);
}
