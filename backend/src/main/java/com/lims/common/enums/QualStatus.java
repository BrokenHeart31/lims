package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/**
 * 方法-检验员「资质状态」（`tester_method.qual_status`，T-105）。
 *
 * <p>落库与出网一律用 code（1/0），中文标签由 {@link #getLabel()} 派生，
 * 与 {@link SampleStatus} 同一处理原则：禁魔法数字、禁前端自行映射中文。</p>
 *
 * <p>T-501 自动分配只取 {@link #VALID} 的行（见 AssignServiceImpl），
 * 「失效」用于人员离岗/资质过期的历史留痕——**不物理删除**，否则无法解释
 * 「为什么这个项目当时分给了他」。</p>
 */
@Getter
public enum QualStatus {

    /** 有效：参与自动分配 */
    VALID(1, "有效"),

    /** 失效：不参与自动分配，但保留历史行 */
    INVALID(0, "失效");

    @EnumValue
    @JsonValue
    private final int code;

    private final String label;

    QualStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按 code 反查；未知 code 返回 null（调用方须 fail-loud，禁止默认有效） */
    public static QualStatus ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElse(null);
    }
}
