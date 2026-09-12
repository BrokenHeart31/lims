package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lims.common.exception.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 单项结论来源枚举（T-601）。
 *
 * <p>AGENTS 7.3 规则 6：单项结论**由引擎自动生成，不可手改**——唯一例外是规则 3
 * 「文本描述型（感官项目）由检验员选合格/不合格」。故结论必须记录<b>是谁产生的</b>，
 * 否则报告上无法区分「系统按限量算出来的」与「人看性状定的」。</p>
 */
@Getter
@AllArgsConstructor
public enum ConclusionSource {

    /** 判定引擎按白名单表达式自动生成（judge_type=1/2） */
    ENGINE(1, "自动判定"),
    /** 检验员人工判定（judge_type=3 文本/感官型） */
    MANUAL(2, "人工判定");

    @EnumValue
    @JsonValue
    private final int code;

    private final String label;

    public static ConclusionSource of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException("非法的结论来源编码: " + code));
    }

    public static ConclusionSource ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElse(null);
    }

    @JsonCreator
    public static ConclusionSource fromJson(Integer code) {
        return ofNullable(code);
    }
}
