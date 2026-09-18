package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.SampleItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 样品检验单项 Mapper（T-401 + feature B 增量）。
 *
 * <p><b>⚠️ feature B 硬约束（设计 §2.9）</b>：本表的「失效」不再是 0/1 两态，而是
 * 「deleted = 该行自身 id」。因此**禁止用 {@code baseMapper.delete(...)}**（MP 会写死 deleted=1，
 * 覆盖式重建二次失效时会撞唯一键 1062）；一律走下列显式 SQL（由
 * {@code service/rollback/SampleDataDisposer} 调用）。</p>
 *
 * <p>为什么用原生 {@code @Update} 而不是 MP 的 LambdaUpdateWrapper：① MP 的 {@code @TableLogic}
 * 只支持固定字面量、无法表达「行自身 id」；② 恢复时需把 deleted 从非 0 复位为 0，
 * 而 MP 会给 wrapper 型 update 自动追加 {@code AND deleted = 0}，会**匹配不到已失效行**。
 * 原生 SQL 不受该注入影响，语义唯一、可读。</p>
 */
public interface SampleItemMapper extends BaseMapper<SampleItem> {

    /**
     * 失效某样品全部「有效」检测明细：{@code deleted} 置为该行自身 id（设计 §2.9）。
     *
     * @return 实际受影响行数（= 失效的明细数）
     */
    @Update("UPDATE sample_item SET deleted = id, updated_by = #{operator}, updated_at = NOW() "
            + "WHERE sample_id = #{sampleId} AND deleted = 0")
    int invalidateBySampleId(@Param("sampleId") Long sampleId, @Param("operator") String operator);

    /**
     * 清空某样品全部「有效」检测明细的指派字段（回退至 S30「已分解」时调用）。
     *
     * @return 实际受影响行数
     */
    @Update("UPDATE sample_item SET assign_status = 0, assign_type = 0, tester_no = NULL, tester_name = NULL, "
            + "assigned_at = NULL, assigned_by = NULL, updated_by = #{operator}, updated_at = NOW() "
            + "WHERE sample_id = #{sampleId} AND deleted = 0")
    int resetAssignBySampleId(@Param("sampleId") Long sampleId, @Param("operator") String operator);

    /**
     * 恢复一行被失效/改写的检测明细：{@code deleted} 复位为 0，并回填指派字段（取自留档快照）。
     *
     * <p>回退期间除 {@code deleted} 与 {@code assign_*} 外未改动其它列，故只恢复这两组。</p>
     *
     * @return 实际受影响行数
     */
    @Update("UPDATE sample_item SET deleted = 0, assign_status = #{item.assignStatus}, assign_type = #{item.assignType}, "
            + "tester_no = #{item.testerNo}, tester_name = #{item.testerName}, assigned_at = #{item.assignedAt}, "
            + "assigned_by = #{item.assignedBy}, updated_by = #{operator}, updated_at = NOW() "
            + "WHERE id = #{item.id}")
    int restoreInvalidated(@Param("item") SampleItem item, @Param("operator") String operator);
}
