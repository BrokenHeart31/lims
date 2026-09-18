package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.SampleResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 检验结果 Mapper（T-601 + feature B 增量）。
 *
 * <p><b>⚠️ feature B 硬约束（设计 §2.9）</b>：本表的「失效」置为 {@code deleted = 该行自身 id}
 * （唯一键 {@code uk_result_sample_item(sample_item_id, deleted)} 只有 0/1 两态，二次失效会撞键）。
 * 禁止 {@code baseMapper.delete(...)}，一律走下列显式 SQL。原因同 {@link SampleItemMapper}。</p>
 */
public interface SampleResultMapper extends BaseMapper<SampleResult> {

    /**
     * 失效某样品全部「有效」检验结果：{@code deleted} 置为该行自身 id。
     *
     * @return 实际受影响行数（= 失效的结果数）
     */
    @Update("UPDATE sample_result SET deleted = id, updated_by = #{operator}, updated_at = NOW() "
            + "WHERE sample_id = #{sampleId} AND deleted = 0")
    int invalidateBySampleId(@Param("sampleId") Long sampleId, @Param("operator") String operator);

    /**
     * 恢复一行被失效的检验结果：{@code deleted} 复位为 0。
     *
     * @return 实际受影响行数
     */
    @Update("UPDATE sample_result SET deleted = 0, updated_by = #{operator}, updated_at = NOW() WHERE id = #{id}")
    int restoreInvalidated(@Param("id") Long id, @Param("operator") String operator);
}
