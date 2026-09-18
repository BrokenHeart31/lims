package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lims.common.exception.BizException;

import java.util.Arrays;

/**
 * 样品状态流水事件类型（`sample_status_log.event_type`，设计 §3.1.1 / §9）。
 *
 * <p><b>为什么需要独立枚举</b>：状态流水承载「正向 + 逆向 + 治理」全部事件，
 * 只用 from/to 无法区分「S60→S50 是审核退回还是回退」。事件类型是区分语义的关键维度，
 * 也是前端时间线着色/图标与审计检索的依据。</p>
 *
 * <p><b>编码口径（与 TINYINT 一一对应，禁止私增/跳动）</b>：
 * <pre>
 *   1 正向推进   S10→S20、S20→S30、S30→S40、S40→S50、S50→S60、S60→S70
 *   2 审核退回   S60→S50（RETURN 白名单）
 *   3 签发       S70→S80
 *   4 回退       ROLLBACK 白名单任意边
 *   5 恢复       某次回退的原路撤销
 *   6 作废/召回  S80/S90 标记动作（不改 status）
 *   7 报告生成   S80→S90
 * </pre>
 * 落库/出网映射与 {@link SampleStatus} 一致：{@code @EnumValue} 令 MP 以 code 读写 TINYINT。</p>
 */
public enum StatusEventType {

    /** 正向推进（业务向前走） */
    FORWARD(1, "正向推进"),
    /** 审核退回（上一环节否定，打回重做） */
    RETURN(2, "审核退回"),
    /** 签发 */
    SIGN(3, "签发"),
    /** 回退（纠错：回退到上一状态） */
    ROLLBACK(4, "回退"),
    /** 恢复（撤销一次未产生新下游数据的回退） */
    RECOVER(5, "恢复"),
    /** 作废 / 召回（S80/S90 专门治理动作） */
    VOID(6, "作废/召回"),
    /** 报告生成 */
    REPORT(7, "报告生成");

    /** 落库 TINYINT 值（MP 以本字段读写；JSON 亦以本值出网） */
    @EnumValue
    @JsonValue
    private final int code;

    /** 中文名（前端展示 / 检索） */
    private final String label;

    StatusEventType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 反查枚举；非法 code 抛业务异常（防止数据库脏值静默通过）。
     */
    public static StatusEventType of(int code) {
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException("非法的状态流水事件类型: " + code));
    }

    /** 按 code 反查，查不到返回 null（宽松场景用） */
    public static StatusEventType ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElse(null);
    }

    /** JSON 反序列化入口：接受数字 code */
    @JsonCreator
    public static StatusEventType fromJson(Integer code) {
        return ofNullable(code);
    }
}
