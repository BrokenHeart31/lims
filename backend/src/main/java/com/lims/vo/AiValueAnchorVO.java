package com.lims.vo;

import lombok.Data;

/**
 * 数值对齐卡片（feature 增量 ai_flow_assistant，T02，设计 §2.3 / §3.4 / §4.2）。
 *
 * <p><b>红线（本增量新增）</b>：数值**只能**来自系统标准库（{@code product_lib_item}）与样品快照
 * （{@code sample_item}），**绝不**取自 OCR 文本，也**绝不**由模型断言。本 VO 由
 * {@code ValueAnchorAssembler} 依「数值路径物理隔离」装配（其依赖集合不得含 {@code gb_*} / {@code JudgeEngine}）。</p>
 *
 * <p>卡片是「读 + 跳转」：只有指向系统标准库对应记录的链接（{@code jumpPath}），**无任何写入动作**。</p>
 */
@Data
public class AiValueAnchorVO {

    /** 检测项目名（如「毒死蜱」） */
    private String itemName;

    /** 单位（如 mg/kg） */
    private String unit;

    /** 系统标准值（权威：取自 product_lib_item / sample_item 快照） */
    private String stdValue;

    /** 判定类型中文名：限量比较 / 不得检出（不得使用）/ 文本或感官人工 */
    private String judgeTypeLabel;

    /** 是否参考性限量（0=否 1=是）；用包装类型以便 JSON 输出 {@code isReference} */
    private Boolean isReference;

    /** 判定依据标准号 */
    private String basisCode;

    /** 来源标准库明细ID（product_lib_item.id），跳转/核对用 */
    private Long libItemId;

    /** 跳转路径（仅当当前用户具备 {@code base:lib:list} 权限时给出；否则为 null） */
    private String jumpPath;

    /** 来源标签（固定「系统标准库（权威）」） */
    private String sourceLabel;
}
