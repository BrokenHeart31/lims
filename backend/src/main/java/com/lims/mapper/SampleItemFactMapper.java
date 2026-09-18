package com.lims.mapper;

import com.lims.service.ai.SampleItemFact;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 样品检验单项**专用只读投影** Mapper（feature 增量 ai_flow_assistant，T02，设计 §5.1）。
 *
 * <p><b>为什么单独抽一个 Mapper 而不复用 {@code SampleItemMapper}</b>：
 * {@code SampleItemMapper} 含 {@code invalidateBySampleId} / {@code restoreInvalidated} 等
 * **回滚失效写方法**；AI 是旁路能力，必须确保「AI 依赖的 Mapper 集合里只有只读」。
 * 本接口**不继承 {@code BaseMapper}**（不带来 save/update/delete），只暴露 {@code @Select} 只读方法——
 * 由 {@code AiDomainIsolationTest} 反射断言「仅含 @Select 方法」。</p>
 *
 * <p>查询一律 {@code deleted = 0}（失效标记语义：0=有效 / 非 0=行自身 id，设计 §2.9）。
 * 投影**不含任何判定结论字段**（{@code conclusion} 等）。</p>
 */
public interface SampleItemFactMapper {

    /**
     * 取某样品全部有效检测明细的只读投影（按项次排序）。
     *
     * @param sampleNo 样品编号
     */
    @Select("SELECT id AS id, item_name AS itemName, basis_code AS basisCode, std_value AS stdValue, "
            + "unit AS unit, judge_type AS judgeType, is_reference AS isReference "
            + "FROM sample_item WHERE sample_no = #{sampleNo} AND deleted = 0 ORDER BY item_order ASC")
    List<SampleItemFact> selectFactsBySampleNo(@Param("sampleNo") String sampleNo);

    /**
     * 取某样品中指定检测项目的一条投影（用于数值对齐卡片）。
     *
     * @param sampleNo 样品编号
     * @param itemName 检测项目名
     */
    @Select("SELECT id AS id, item_name AS itemName, basis_code AS basisCode, std_value AS stdValue, "
            + "unit AS unit, judge_type AS judgeType, is_reference AS isReference "
            + "FROM sample_item WHERE sample_no = #{sampleNo} AND item_name = #{itemName} "
            + "AND deleted = 0 ORDER BY item_order ASC LIMIT 1")
    SampleItemFact selectFactBySampleAndItem(@Param("sampleNo") String sampleNo,
                                             @Param("itemName") String itemName);
}
