package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.ReportGenerateDTO;
import com.lims.vo.ReportPendingVO;
import com.lims.vo.ReportVO;

/**
 * 检验报告生成 / 查询服务（T-702）。
 *
 * <p>覆盖阶段七下半：已签发样品（S80）→ 生成 CMA / CMA-CATL 报告（S80→S90），
 * 并支持按样品编号重打（不改变状态）。业务依据：说明书「九、检验业务流程之六：自动生成检验报告」。</p>
 */
public interface ReportGenerateService {

    /**
     * 8.2 分页查询可生成/可重打的样品（status ∈ {S80 已签发, S90 已出报告}）。
     *
     * @param pageNum    页码（从 1 起）
     * @param pageSize   每页条数
     * @param sampleNo   样品编号（模糊，可空）
     * @param sampleName 样品名称（模糊，可空）
     * @param taskNo     任务编号（精确，可空）
     */
    PageResult<ReportPendingVO> pagePendingGenerate(long pageNum, long pageSize,
                                                    String sampleNo, String sampleName, String taskNo);

    /**
     * 生成检验报告：校验 S80 → 状态流转 S80→S90 → 落库报告元信息 → 返回完整报告数据。
     *
     * @param dto 样品编号 + 报告类型 code（null 按 CMA）
     * @return 报告渲染模型
     */
    ReportVO generate(ReportGenerateDTO dto);

    /**
     * 查询报告详情（供打印 / 重打；<b>不改变样品状态</b>）。
     *
     * @param sampleNo   样品编号
     * @param reportType 报告类型 code（可空；为空时回退样品已存的类型，再回退 CMA）
     */
    ReportVO detail(String sampleNo, Integer reportType);
}
