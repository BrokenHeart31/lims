package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lims.common.exception.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 审核签发动作枚举（T-701）。
 *
 * <p>与 {@code sample_audit_log.action}（TINYINT）一一对应。
 * 流水表只追加、不改写——它是「谁在何时把样品从什么状态推到什么状态、给了什么意见」的唯一证据。</p>
 */
@Getter
@AllArgsConstructor
public enum AuditAction {

    /** 审核通过：S60（检验完成）→ S70（已审核） */
    APPROVE(1, "审核通过"),

    /** 审核退回：S60（检验完成）→ S50（检验中），打回检验员重录（AGENTS 7.2） */
    RETURN(2, "审核退回"),

    /** 签发：S70（已审核）→ S80（已签发） */
    SIGN(3, "签发");

    @EnumValue
    @JsonValue
    private final int code;

    private final String label;

    public static AuditAction of(int code) {
        return Arrays.stream(values())
                .filter(a -> a.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException("非法的审核动作编码: " + code));
    }

    public static AuditAction ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(a -> a.code == code)
                .findFirst()
                .orElse(null);
    }

    @JsonCreator
    public static AuditAction fromJson(Integer code) {
        return ofNullable(code);
    }
}
