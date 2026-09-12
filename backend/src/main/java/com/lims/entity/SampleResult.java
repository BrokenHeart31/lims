package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 检验结果（sample_result，T-601）。
 *
 * <p>一行 = 一个检测单项的录入结果，与 {@link SampleItem} **一对一**
 * （唯一键 {@code (sample_item_id, deleted)}；重复保存走覆盖式更新，由审计字段留痕）。</p>
 *
 * <p><b>分层语义（ALCOA+ 落地，依据 docs/knowledge/2026-09-12-judge-engine-research.md 第 3 节）</b>：</p>
 * <ul>
 *   <li>{@code testValue}：检验员录入的**原始值**（数值 或 未检出），是唯一的人为输入；</li>
 *   <li>{@code conclusion} / {@code conclusionSource}：**派生值**，由判定引擎按白名单矩阵算出
 *       （jt1/jt2），或由检验员人工选择（jt3 感官项）；</li>
 *   <li>{@code judgeBasis}：判定依据说明，人可读，用于人工复核与审计追溯。</li>
 * </ul>
 *
 * <p><b>判定依据参数不冗余存放</b>：{@code std_value} / {@code judge_type} / {@code lower_limit} /
 * {@code is_reference} 一律取自 {@link SampleItem}（T-401 快照下沉，检验时点固化）。
 * 引擎只读 {@code sample_item}，禁止回溯 {@code product_lib_item}（AGENTS 7.3 / 白名单定稿 D5）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_result")
public class SampleResult extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID（sample_info.id） */
    private Long sampleId;

    /** 检测单项ID（sample_item.id），与之一对一 */
    private Long sampleItemId;

    /** 样品编号（冗余，便于查询/报告打印） */
    private String sampleNo;

    /** 项次（冗余，报告排序用） */
    private Integer itemOrder;

    /** 检验项目名称（冗余，报告打印用） */
    private String itemName;

    /** 检验结果原始值（白名单 2 形态：数值 / 未检出） */
    private String testValue;

    /** 单项结论（1=合格 2=不合格 3=待判定） */
    private ResultConclusion conclusion;

    /** 结论来源（1=引擎自动判定 2=检验员人工判定） */
    private ConclusionSource conclusionSource;

    /** 判定依据说明（人可读，审计追溯用） */
    private String judgeBasis;

    /** 录入人工号 */
    private String enteredBy;

    /** 录入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enteredAt;

    /** 备注 */
    private String remark;

    /** 结论中文名（非持久化，出网供前端展示） */
    public String getConclusionLabel() {
        return conclusion == null ? null : conclusion.getLabel();
    }

    /** 结论来源中文名（非持久化，出网供前端展示） */
    public String getConclusionSourceLabel() {
        return conclusionSource == null ? null : conclusionSource.getLabel();
    }
}
