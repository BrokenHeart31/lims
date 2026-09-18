package com.lims.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * AI 助手领域判定（护栏输出，feature A，PRD T1 / A-03）。
 *
 * <p>三分域 + 一个内部态：</p>
 * <ul>
 *   <li>{@link #BUSINESS} —— 业务域（样品状态/下一步/权限）。由 {@code BusinessRuleAssembler}
 *       **代码装配**答案，**不调模型**，保证零幻觉；</li>
 *   <li>{@link #STANDARD} —— 标准域（GB 限量/方法/条款）。走 ngram 检索 + 命中条款投喂模型，
 *       答案**必带 citations**；</li>
 *   <li>{@link #OTHER} —— 越界/闲聊。**100% 拒答**（同事口吻 + 2~3 条替代建议）；</li>
 *   <li>{@link #UNKNOWN} —— **内部态**：规则词表无法判定，交模型分类（temperature=0，严格 JSON）。
 *       它**永不落库**（{@code ai_message.domain} 只写 business/standard/other）。</li>
 * </ul>
 *
 * <p>落库字段 {@code ai_message.domain VARCHAR(16)} 存的是 {@link #getCode()}（英文小写），
 * 便于事后按域复盘护栏准确率（R5）。</p>
 */
@Getter
@AllArgsConstructor
public enum AiDomain {

    BUSINESS("business", "业务"),
    STANDARD("standard", "标准"),
    OTHER("other", "其他"),
    /** 内部态：尚未判定（不落库） */
    UNKNOWN("unknown", "未判定");

    private final String code;
    private final String label;

    /** 按 code 反查；查不到返回 null（供宽松场景使用） */
    public static AiDomain ofNullable(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(d -> d.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 是否可落库（UNKNOWN 不落库，落库前必须已解析为具体域） */
    public boolean persistable() {
        return this != UNKNOWN;
    }
}
