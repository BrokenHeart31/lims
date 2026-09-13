package com.lims.service;

import com.lims.vo.StatNameValueVO;
import com.lims.vo.StatOverviewVO;
import com.lims.vo.StatTrendVO;

import java.util.List;

/**
 * 质量分析统计服务（T-803，api-spec 第 14 章）。
 *
 * <p><b>禁 mock 假数据</b>（DECISIONS 2026-09-13）：本域每个数值都必须来自真实聚合查询；
 * 无数据时返回 0 或空列表，<b>不得</b>返回编造的演示数据。空数据的正确表达是
 * 「0 / 空图表 + 空状态提示」，而不是「看起来很像真的假数据」——后者会让使用单位
 * 误判系统已在工作。</p>
 *
 * <p><b>统计口径固定在此层</b>：中文标签（状态、结论）由本层用枚举翻译，
 * 百分比由本层算一次（前端不重算），保证卡片、饼图、导出三处口径一致。</p>
 */
public interface StatService {

    /** 总览卡片 */
    StatOverviewVO overview();

    /** 样品状态分布（中文标签已翻译） */
    List<StatNameValueVO> sampleStatusDistribution();

    /** 检验类别分布 */
    List<StatNameValueVO> inspectTypeDistribution();

    /** 受检单位样品量 Top N */
    List<StatNameValueVO> topClients(int limit);

    /** 食品大类检测量 Top N */
    List<StatNameValueVO> categoryDistribution(int limit);

    /** 检验员任务量 Top N（工号 → 姓名已翻译） */
    List<StatNameValueVO> testerWorkload(int limit);

    /** 部门样品量分布 */
    List<StatNameValueVO> deptDistribution();

    /** 不合格项目 Top N */
    List<StatNameValueVO> topUnqualifiedItems(int limit);

    /**
     * 月度趋势（近 N 个月，含本月；**已补零月**）。
     *
     * @param months 月数（1..24）
     */
    List<StatTrendVO> monthlyTrend(int months);
}
