package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.ResultSaveDTO;
import com.lims.vo.ResultDetailVO;
import com.lims.vo.ResultJudgeVO;
import com.lims.vo.ResultPendingVO;
import com.lims.vo.ResultSaveVO;

/**
 * 检验数据录入 + 自动判定服务（api-spec 第 6 章，T-601）。
 *
 * <p>覆盖阶段六：检验员按已安排的检测单项录入检验结果 → 判定引擎按白名单矩阵自动判定单项结论
 * → 全部录齐后 S50→S60（AGENTS 7.3 六条规则 / 判定引擎白名单定稿）。</p>
 */
public interface ResultService {

    /** 6.2 分页查询待录入样品（status ∈ {S40 已安排, S50 检验中}） */
    PageResult<ResultPendingVO> pagePending(long pageNum, long pageSize, String sampleNo, String sampleName);

    /** 6.2 查询样品录入明细（含判定依据快照 + 已录入结果 + 录入进度） */
    ResultDetailVO detail(Long sampleId);

    /** 6.3 实时判定预览（纯计算不落库，供录入页即时展示结论与依据） */
    ResultJudgeVO judgePreview(Long itemId, String testValue, Integer manualConclusion);

    /** 6.4 保存录入结果（可分次；首次保存 S40→S50） */
    ResultSaveVO save(ResultSaveDTO dto);

    /** 6.5 提交（要求全部单项已录入；S50→S60） */
    ResultSaveVO submit(Long sampleId);
}
