package com.lims.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.lims.common.exception.BizException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 检验报告类型枚举（T-702）。
 *
 * <p>与 {@code sample_info.report_type}（TINYINT）一一对应：1=CMA、2=CMA-CATL。
 * 「CMA」为检验检测机构资质认定报告；「CMA-CATL」在 CMA 基础上追加农产品质量安全
 * 检测机构考核合格（CATL）资质——两者**版式相同、封面资质行数不同**（CMA 1 行、
 * CMA-CATL 2 行），因此报告合成阶段必须知道类型才能决定封面渲染。</p>
 *
 * <p>落库/出网映射照项目既有约定（参考 {@link AuditAction}）：
 * {@code @EnumValue} 令 MyBatis-Plus 以 code 读写 TINYINT；{@code @JsonValue}/{@code @JsonCreator}
 * 令 JSON 中的 reportType 以数字 code 出现（响应 VO 中另给 {@code reportTypeLabel} 中文）。</p>
 *
 * <p><b>为什么额外提供 {@link #parse(String)}</b>：生成接口的请求体按业务说明书以
 * <b>名称字符串</b>（{@code "CMA"} / {@code "CMA_CATL"}）传递类型，与 {@code @JsonValue}
 * 的数字出网口径不同；解析入口集中在此，避免在 Service 里散落字符串魔法值。</p>
 */
@Getter
@AllArgsConstructor
public enum ReportType {

    /** CMA 检验报告（检验检测机构资质认定） */
    CMA(1, "CMA检验报告"),

    /** CMA-CATL 检验报告（CMA + 农产品质量安全检测机构考核合格） */
    CMA_CATL(2, "CMA-CATL检验报告");

    /** 类型码，落库 TINYINT 值（MP 以本字段读写；JSON 亦以本值出网） */
    @EnumValue
    @JsonValue
    private final int code;

    /** 类型中文名（封面/列表展示用） */
    private final String label;

    /**
     * 按 code 反查枚举；非法 code 抛业务异常(400)。
     *
     * <p>报告类型的取值来自前端（用户选择），非法值属<b>客户端参数错误</b>，
     * 故用 400 而非兜底的 500——避免把「用户传错」误报成「系统异常」。</p>
     */
    public static ReportType of(int code) {
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElseThrow(() -> new BizException(400, "非法的报告类型编码: " + code));
    }

    /**
     * 按 code 反查枚举，查不到返回 {@code null}（宽松场景：解析外部输入/DB 可空列）。
     */
    public static ReportType ofNullable(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.code == code)
                .findFirst()
                .orElse(null);
    }

    /** JSON 反序列化入口：接受数字 code（未知值返回 null，由业务层校验） */
    @JsonCreator
    public static ReportType fromJson(Integer code) {
        return ofNullable(code);
    }

    /**
     * 解析请求参数中的报告类型：既接受名称（{@code CMA} / {@code CMA_CATL}，忽略大小写），
     * 也兼容数字串（{@code "1"} / {@code "2"}）。
     *
     * @param raw 原始值；空白返回 {@code null}（交由调用方决定默认值）
     * @throws BizException 非空但无法识别时抛 400，避免静默落到错误类型
     */
    public static ReportType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        try {
            return of(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            throw new BizException(400, "非法的报告类型: " + raw);
        }
    }
}
