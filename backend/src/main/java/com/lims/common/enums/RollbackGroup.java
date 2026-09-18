package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * 回退分组（`sample_rollback.edge_group`，设计 §2.8 / PRD T4）。
 *
 * <p>分级护栏的载体：
 * <ul>
 *   <li>{@link #NORMAL} 常规回退（未签发）：S20→S10 … S60→S50，相应业务角色可退，<b>原因必填</b>；</li>
 *   <li>{@link #SENSITIVE} 敏感回退（已审核）：S70→S60，需更高权限 + 强理由 + <b>二次确认</b>。</li>
 * </ul>
 * 已签发 S80→S70 与已出报告 S90→S80 <b>不属于任何分组</b>——它们是**被拒边**，
 * 由 {@link RollbackEdgePolicy} 显式拒绝（4102/4103），改走「作废 / 召回」专门动作。</p>
 */
public enum RollbackGroup {

    /** 常规回退（未签发） */
    NORMAL(1, "常规"),
    /** 敏感回退（已审核） */
    SENSITIVE(2, "敏感");

    @EnumValue
    @JsonValue
    private final int code;

    private final String label;

    RollbackGroup(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static RollbackGroup of(int code) {
        return Arrays.stream(values())
                .filter(g -> g.code == code)
                .findFirst()
                .orElse(null);
    }

    @JsonCreator
    public static RollbackGroup fromJson(Integer code) {
        return code == null ? null : of(code);
    }
}
