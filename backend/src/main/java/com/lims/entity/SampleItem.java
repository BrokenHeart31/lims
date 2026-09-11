package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 样品检验单项（sample_item，T-401 项目分解结果）。
 *
 * <p>承载「样品 × 检测单项」的实例数据：由 T-401 按项目标准库（product_lib_item）自动套用生成初稿，
 * 经人工增删调整后确认保存（业务说明书「五、检验业务流程之二」）。</p>
 *
 * <p><b>快照语义</b>：标准库字段（unit/basis_code/methods/std_value/judge_type/is_reference/
 * lower_limit/method_note）在套库时**复制下沉**到本表，不随标准库后续变更而变。
 * 原因：① 国标会更新，报告须固化当时的判定依据；② 人工调整后的值必须独立于标准库。</p>
 *
 * <p>消费方：T-501 任务安排、T-601 判定引擎均以本表为输入；
 * <b>判定引擎只读 sample_item，不回溯 product_lib_item</b>（AGENTS 7.3 / 白名单定稿 D5）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_item")
public class SampleItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID（sample_info.id） */
    private Long sampleId;

    /** 样品编号（冗余，便于查询与报告打印） */
    private String sampleNo;

    /** 项次（样品内排序，从 1 起，样品内唯一） */
    private Integer itemOrder;

    /** 检验项目/检测单项名称 */
    private String itemName;

    /** 来源标准库明细ID（product_lib_item.id）；人工新增项为 null */
    private Long libItemId;

    /** 单位 */
    private String unit;

    /** 判定依据标准号 */
    private String basisCode;

    /** 检验方法（多个以 # 分隔） */
    private String methods;

    /** 标准值（限量值文本，白名单 5 形态） */
    private String stdValue;

    /** 判定类型：1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
    private Integer judgeType;

    /** 是否参考性限量（带 * 的参考项）：0=否 1=是 */
    private Integer isReference;

    /** 最低检出限 */
    private String lowerLimit;

    /** 方法备注 */
    private String methodNote;

    /** 来源：1=标准库自动套用 2=人工新增 */
    private Integer sourceType;

    /** 备注（调整原因等） */
    private String remark;
}
