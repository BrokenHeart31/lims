package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lims.common.exception.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 样品状态机枚举（AGENTS 7.2 唯一标准，S10→S90）。
 *
 * <p>code 与 {@code sample.status}（TINYINT）一一对应：S10=10 … S90=90。
 * 状态流转一律经 {@link SampleStatusTransition#assertTransition}，禁止私增状态、禁止跳态。</p>
 *
 * <p>落库/出网映射（本项目落地补充，见 docs/knowledge 状态机侦察记录「落地补充」）：
 * {@code @EnumValue} 令 MyBatis-Plus 以 code 读写 TINYINT；{@code @JsonValue}/{@code @JsonCreator}
 * 令 JSON 中的 status 以数字 code 出现（前端另取 {@code statusLabel} 展示中文）。</p>
 *
 * <p>选型依据：docs/knowledge/2026-09-11-sample-statemachine-research.md
 * （确定性线性主线 + 少量分支 → 枚举 + EnumMap 白名单，不引 Spring StateMachine）。</p>
 */
@Getter
@AllArgsConstructor
public enum SampleStatus {

    /** 已登记：采样单导入成功 */
    S10(10, "已登记"),
    /** 登记确认：登记员确认 */
    S20(20, "登记确认"),
    /** 已分解：项目分解确认保存 */
    S30(30, "已分解"),
    /** 已安排：任务安排确认保存 */
    S40(40, "已安排"),
    /** 检验中：检验员首次录入 */
    S50(50, "检验中"),
    /** 检验完成：全部项目录齐 */
    S60(60, "检验完成"),
    /** 已审核：领导审核通过 */
    S70(70, "已审核"),
    /** 已签发：领导签发 */
    S80(80, "已签发"),
    /** 已出报告：报告生成完成 */
    S90(90, "已出报告");

    /** 状态码，落库 TINYINT 值（MP 以本字段读写；JSON 亦以本值出网） */
    @EnumValue
    @JsonValue
    private final int code;

    /** 状态中文名（与 AGENTS 7.2 表述一致，供提示/前端展示） */
    private final String label;

    /**
     * 按 code 反查枚举；非法 code 抛业务异常（防止数据库脏值静默通过）。
     */
    public static SampleStatus of(int code) {
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException("非法的样品状态编码: " + code));
    }

    /**
     * 按 code 反查枚举，查不到返回 {@code null}（供查询条件等宽松场景使用）。
     */
    public static SampleStatus ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElse(null);
    }

    /** JSON 反序列化入口：接受数字 code（未知值返回 null，由 JSR-303/业务层校验） */
    @JsonCreator
    public static SampleStatus fromJson(Integer code) {
        return ofNullable(code);
    }
}
