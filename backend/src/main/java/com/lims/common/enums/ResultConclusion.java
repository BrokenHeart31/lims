package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lims.common.exception.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 检验结论枚举（T-601，判定引擎唯一输出闭集）。
 *
 * <p>与 AGENTS 7.3 / 判定引擎白名单定稿（{@code docs/knowledge/2026-09-11-judge-engine-whitelist.md}）
 * 的第 2 节「输出 3 形态」严格一致：<b>合格 / 不合格 / 待判定</b>，除此之外不存在第四种结论。</p>
 *
 * <p><b>为什么必须有「待判定」</b>：判定白名单之外的一切输入（stdValue 为非闭集文本、不得检出型缺
 * 最低检出限、无法解析的检验值……）都必须落到「待判定」并记日志——<b>禁止静默判合格</b>。
 * 食品检验场景下「默认合格」是最危险的失效模式。</p>
 *
 * <p>落库 {@code TINYINT}（{@code @EnumValue}），JSON 出网为数字 code（{@code @JsonValue}），
 * 前端另取 {@code conclusionLabel} 展示中文，与 {@link SampleStatus} 同一约定。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultConclusion {

    /** 合格 */
    QUALIFIED(1, "合格"),
    /** 不合格 */
    UNQUALIFIED(2, "不合格"),
    /** 待判定：白名单外输入 / 依据不足 / 需人工确认，禁止默认判合格 */
    PENDING(3, "待判定");

    /** 结论码，落库 TINYINT 值（MP 以本字段读写；JSON 亦以本值出网） */
    @EnumValue
    @JsonValue
    private final int code;

    /** 结论中文名（与 AGENTS 7.3 表述一致） */
    private final String label;

    /**
     * 按 code 反查枚举；非法 code 抛业务异常（防止数据库脏值静默通过）。
     */
    public static ResultConclusion of(int code) {
        return Arrays.stream(values())
                .filter(c -> c.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException("非法的检验结论编码: " + code));
    }

    /**
     * 按 code 反查枚举，查不到返回 {@code null}（宽松场景：解析外部输入）。
     */
    public static ResultConclusion ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(c -> c.code == code)
                .findFirst()
                .orElse(null);
    }

    /** JSON 反序列化入口：接受数字 code（未知值返回 null，由业务层校验） */
    @JsonCreator
    public static ResultConclusion fromJson(Integer code) {
        return ofNullable(code);
    }
}
