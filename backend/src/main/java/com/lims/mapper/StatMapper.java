package com.lims.mapper;

import com.lims.vo.StatNameValueVO;
import com.lims.vo.StatOverviewVO;
import com.lims.vo.StatTrendVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 质量分析统计 Mapper（T-803，只读聚合）。
 *
 * <p><b>为什么不复用 QueryMapper</b>：QueryMapper 是「按条件分页取行」的查询语义；
 * 本 Mapper 全是 {@code GROUP BY / COUNT} 聚合，返回的是「统计结果」而非「业务行」。
 * 分开维护可避免一个文件里混两种截然不同的 SQL 风格，也让「统计口径」集中可见。</p>
 *
 * <p><b>逻辑删除提醒</b>：自定义 XML 不受 MyBatis-Plus 逻辑删除自动追加，
 * 故每条 SQL 显式 {@code deleted = 0}（与 QueryMapper.xml 同一约定）。</p>
 *
 * <p><b>⚠️ MySQL 保留字</b>：{@code generated} 是 MySQL 8 保留字，别名不可直接用，
 * 一律写成 {@code cnt_generated}（已踩坑，见 DECISIONS 2026-09-13）。</p>
 */
@Mapper
public interface StatMapper {

    /** 总览卡片（单行多列聚合，一条 SQL 取回避免多次往返） */
    StatOverviewVO selectOverview();

    /** 样品状态分布（status code → 计数） */
    List<StatNameValueVO> countByStatus();

    /** 检验类别分布（inspect_type → 计数） */
    List<StatNameValueVO> countByInspectType();

    /** 受检单位样品量 Top N */
    List<StatNameValueVO> countByClient(@Param("limit") int limit);

    /**
     * 食品大类检测量（按 sample_item 关联产品库取 category）。
     *
     * <p>口径：一个检测单项计 1。用 {@code product_lib_item → product_lib} 反查大类，
     * 因为 sample_item 本身不存大类（快照只冗余了判定所需字段）。</p>
     */
    List<StatNameValueVO> countByCategory(@Param("limit") int limit);

    /** 检验员任务量（按 sample_item.tester_no 聚合，Top N） */
    List<StatNameValueVO> countByTester(@Param("limit") int limit);

    /** 部门样品量（按 sample_info 关联用户部门，Top N） */
    List<StatNameValueVO> countByDept();

    /** 项目不合格项 Top N（按 sample_result.conclusion=2 聚合 item_name） */
    List<StatNameValueVO> countUnqualifiedItems(@Param("limit") int limit);

    /**
     * 月度趋势（近 N 个月）。
     *
     * <p>返回的月份可能不连续（无数据的月份无行），由 Service 补零月。</p>
     */
    List<StatTrendVO> selectMonthlyTrend(@Param("months") int months);
}
