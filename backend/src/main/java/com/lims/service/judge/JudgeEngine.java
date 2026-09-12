package com.lims.service.judge;

import com.lims.common.enums.ResultConclusion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * 检验结果自动判定引擎（T-601，AGENTS 7.3 / 白名单定稿）。
 *
 * <p><b>形态定稿（选型依据见 docs/knowledge/2026-09-12-judge-engine-research.md）</b>：
 * 判定语义是<b>代码里的有限状态矩阵</b>，{@code std_value} 只是被<b>解析</b>、从<b>不被执行</b>——
 * 故<b>不引入任何规则引擎或表达式引擎</b>（Drools/Easy Rules/LiteFlow/Aviator 全部否决）。
 * 收益：确定性（可回归测试）、可审计（产出人可读依据）、fail-loud（闭集外一律待判定 + WARN）。</p>
 *
 * <p><b>纯函数</b>：无状态、无 IO、无 Spring 依赖注入（仅注册为 Bean 供注入），
 * 输入 {@link JudgeInput} → 输出 {@link JudgeOutcome}，可被单测直接构造数据覆盖全分支。</p>
 *
 * <p><b>判定矩阵</b>（与白名单定稿 D1–D4 一一对应）：</p>
 * <table border="1">
 *   <caption>判定矩阵</caption>
 *   <tr><th>判定类型</th><th>检验值</th><th>结论</th></tr>
 *   <tr><td rowspan="3">jt1 限量比较（标准值数值/≤X）</td>
 *       <td>未检出</td><td>合格</td></tr>
 *   <tr><td>数值 &lt; 最低检出限</td><td>合格（D1 视同未检出）</td></tr>
 *   <tr><td>数值 ≤ X / &gt; X</td><td>合格 / 不合格</td></tr>
 *   <tr><td rowspan="3">jt2 不得检出 / 不得使用</td>
 *       <td>未检出</td><td>合格</td></tr>
 *   <tr><td>数值 ≥ 检出限 / &lt; 检出限</td><td>不合格 / 合格</td></tr>
 *   <tr><td>数值 且 检出限为空</td><td>待判定（禁止默判合格，D2）</td></tr>
 *   <tr><td rowspan="3">标准值 -- （无依据）</td>
 *       <td>未检出</td><td>合格</td></tr>
 *   <tr><td>数值 &lt; 检出限</td><td>合格</td></tr>
 *   <tr><td>数值（未维护检出限 或 ≥ 检出限）</td><td>待判定（D4）</td></tr>
 *   <tr><td>jt3 文本/感官</td><td>—</td><td>检验员人工选择合格/不合格（规则 3）</td></tr>
 *   <tr><td colspan="2">白名单外任意组合</td><td>待判定 + WARN 日志（禁止静默判合格）</td></tr>
 * </table>
 *
 * <p><b>浮点安全</b>：一切数值比较走 {@link BigDecimal#compareTo}，禁止 {@code ==} 与
 * {@link BigDecimal#equals}（后者比较 scale，{@code "2.00".equals("2.0")} 为 false）。</p>
 */
@Slf4j
@Component
public class JudgeEngine {

    /** 判定类型：限量比较 */
    public static final int JT_LIMIT = 1;
    /** 判定类型：不得检出 / 不得使用 */
    public static final int JT_NOT_DETECTED = 2;
    /** 判定类型：文本 / 感官人工 */
    public static final int JT_MANUAL = 3;

    private static final String NOT_DETECTED_TEXT = "未检出";
    /** 标准值形态用词（注意与检验值形态的「未检出」不同：标准值写「不得检出」） */
    private static final String NOT_DETECTED_STD_TEXT = "不得检出";
    private static final String NOT_ALLOWED_USE_TEXT = "不得使用";

    /** 标准值形态（白名单 5 形态 + 闭集外） */
    public enum StdValueForm {
        /** 纯数值限量（如 {@code 0.5}） */
        NUMERIC,
        /** 上限型（{@code ≤0.25} / {@code <=0.25} / {@code <0.25}） */
        LE_NUMERIC,
        /** 不得检出 */
        NOT_DETECTED,
        /** 不得使用 */
        NOT_USED,
        /** --（无判定依据） */
        NONE,
        /** 闭集外（文本等），一律待判定 */
        UNKNOWN
    }

    /** 检验值形态（白名单 2 形态 + 闭集外） */
    public enum TestValueForm {
        /** 数值 */
        NUMERIC,
        /** 未检出 */
        NOT_DETECTED,
        /** 闭集外，一律待判定 */
        UNKNOWN
    }

    /**
     * 执行单项判定。
     *
     * @param in 判定输入（全部取自 {@code sample_item} 快照 + 录入原始值）
     * @return 判定结果（永不为 {@code null}；无法判定时返回「待判定」并说明原因）
     */
    public JudgeOutcome judge(JudgeInput in) {
        Integer judgeType = in.judgeType();
        String stdRaw = clean(in.stdValue());
        String testRaw = clean(in.testValue());

        // ---- 规则 3：文本/感官型 → 检验员人工判定（唯一允许人工给结论的分支）----
        if (Objects.equals(judgeType, JT_MANUAL)) {
            ResultConclusion manual = ResultConclusion.ofNullable(in.manualConclusion());
            if (manual == null || manual == ResultConclusion.PENDING) {
                return pending("文本/感官项目需检验员人工选择「合格」或「不合格」，当前未选择");
            }
            return JudgeOutcome.manual(manual, "文本/感官项目，由检验员人工判定为「" + manual.getLabel() + "」");
        }

        // ---- 判定类型越界（数据异常，fail-loud）----
        if (judgeType == null || (judgeType != JT_LIMIT && judgeType != JT_NOT_DETECTED)) {
            return pending("判定类型非法（judgeType=" + judgeType + "），无法自动判定");
        }

        if (stdRaw == null) {
            return pending("标准值为空，缺少判定依据");
        }
        if (testRaw == null) {
            return pending("未录入检验值");
        }

        StdValueForm stdForm = parseStdValue(stdRaw);
        TestValueForm testForm = parseTestValue(testRaw);
        BigDecimal lower = parseNumber(in.lowerLimit());

        if (stdForm == StdValueForm.UNKNOWN) {
            return pending("标准值「" + stdRaw + "」不在判定白名单闭集内，需人工判定");
        }
        if (testForm == TestValueForm.UNKNOWN) {
            return pending("检验值「" + testRaw + "」既非数值也非「未检出」，需人工判定");
        }

        // ---- 标准值 --（无判定依据）：D4 矩阵，jt1/jt2 通用 ----
        if (stdForm == StdValueForm.NONE) {
            return judgeNoBasis(stdRaw, testRaw, testForm, lower, in.lowerLimit());
        }

        // ---- jt2：不得检出 / 不得使用（D2 矩阵）----
        if (judgeType == JT_NOT_DETECTED) {
            if (stdForm != StdValueForm.NOT_DETECTED && stdForm != StdValueForm.NOT_USED) {
                return pending("判定类型为「不得检出/不得使用」，但标准值「" + stdRaw
                        + "」为数值型，口径矛盾，需人工判定");
            }
            return judgeNotDetected(stdRaw, testRaw, testForm, lower, clean(in.lowerLimit()));
        }

        // ---- jt1：限量比较（D1 矩阵）----
        if (stdForm != StdValueForm.NUMERIC && stdForm != StdValueForm.LE_NUMERIC) {
            return pending("判定类型为「限量比较」，但标准值「" + stdRaw
                    + "」不是数值型，口径矛盾，需人工判定");
        }
        return judgeLimit(stdRaw, testRaw, testForm, lower, clean(in.lowerLimit()));
    }

    // =========================================================================
    // 判定矩阵（三个子矩阵）
    // =========================================================================

    /** jt1 限量比较（D1：数值 < 最低检出限视同未检出 → 合格） */
    private JudgeOutcome judgeLimit(String stdRaw, String testRaw, TestValueForm testForm,
                                    BigDecimal lower, String lowerRaw) {
        if (testForm == TestValueForm.NOT_DETECTED) {
            return JudgeOutcome.engine(ResultConclusion.QUALIFIED, "检验结果为未检出，判定合格");
        }
        BigDecimal limit = parseNumber(stdRaw);
        BigDecimal value = parseNumber(testRaw);
        if (limit == null || value == null) {
            // parseStdValue/parseTestValue 已保证数值可解析，此处为防御性兜底
            return pending("数值型标准值或检验值解析失败，需人工判定");
        }
        if (lower != null && value.compareTo(lower) < 0) {
            return JudgeOutcome.engine(ResultConclusion.QUALIFIED,
                    "实测 " + testRaw + " 低于最低检出限 " + lowerRaw + "，视同未检出，判定合格");
        }
        if (value.compareTo(limit) <= 0) {
            return JudgeOutcome.engine(ResultConclusion.QUALIFIED,
                    "限量值 " + stdRaw + "，实测 " + testRaw + " ≤ 限量，判定合格");
        }
        return JudgeOutcome.engine(ResultConclusion.UNQUALIFIED,
                "限量值 " + stdRaw + "，实测 " + testRaw + " > 限量，判定不合格");
    }

    /** jt2 不得检出 / 不得使用（D2：数值 ≥ 检出限才算检出） */
    private JudgeOutcome judgeNotDetected(String stdRaw, String testRaw, TestValueForm testForm,
                                          BigDecimal lower, String lowerRaw) {
        if (testForm == TestValueForm.NOT_DETECTED) {
            return JudgeOutcome.engine(ResultConclusion.QUALIFIED,
                    "标准值「" + stdRaw + "」，实测未检出，判定合格");
        }
        BigDecimal value = parseNumber(testRaw);
        if (value == null) {
            return pending("数值型检验值解析失败，需人工判定");
        }
        if (lower == null) {
            // D2：缺检出限时无法判断「是否检出」，禁止默判合格
            return pending("标准值「" + stdRaw + "」为不得检出型，但未维护最低检出限，无法判断是否检出，需人工判定");
        }
        if (value.compareTo(lower) >= 0) {
            return JudgeOutcome.engine(ResultConclusion.UNQUALIFIED,
                    "标准值「" + stdRaw + "」，实测 " + testRaw + " ≥ 检出限 " + lowerRaw + "，判定为检出 → 不合格");
        }
        return JudgeOutcome.engine(ResultConclusion.QUALIFIED,
                "标准值「" + stdRaw + "」，实测 " + testRaw + " 低于检出限 " + lowerRaw + "，视同未检出，判定合格");
    }

    /** 标准值 --（无判定依据）（D4 矩阵） */
    private JudgeOutcome judgeNoBasis(String stdRaw, String testRaw, TestValueForm testForm,
                                      BigDecimal lower, String lowerRaw) {
        if (testForm == TestValueForm.NOT_DETECTED) {
            return JudgeOutcome.engine(ResultConclusion.QUALIFIED,
                    "标准值为「" + stdRaw + "」（无判定依据），实测未检出，判定合格");
        }
        BigDecimal value = parseNumber(testRaw);
        if (value == null) {
            return pending("数值型检验值解析失败，需人工判定");
        }
        if (lower != null && value.compareTo(lower) < 0) {
            return JudgeOutcome.engine(ResultConclusion.QUALIFIED,
                    "标准值为「" + stdRaw + "」（无判定依据），实测 " + testRaw + " 低于检出限 "
                            + lowerRaw + "，视同未检出，判定合格");
        }
        return pending("标准值为「" + stdRaw + "」（无判定依据），实测 " + testRaw + "，无法自动判定，需人工确认");
    }

    // =========================================================================
    // 形态解析（白名单闭集）
    // =========================================================================

    /**
     * 解析标准值形态（白名单 5 形态）。
     *
     * <p>顺序敏感：「不得检出/不得使用」先于数值判定；`≤`/`<` 前缀先于纯数值判定。
     * 迁移残留的参考项星号（{@code *}）先剥离（参考性由 {@code is_reference} 列承载）。</p>
     */
    public static StdValueForm parseStdValue(String raw) {
        String s = clean(raw);
        if (s == null) {
            return StdValueForm.UNKNOWN;
        }
        s = s.replace("*", "").replace("＊", "").trim();
        if (s.isEmpty()) {
            return StdValueForm.UNKNOWN;
        }
        // 全角破折号归一（V1 迁移曾做 '—'→'-'，此处兼容两种写法）
        String dash = s.replace('—', '-').replace('－', '-');
        if (dash.equals("--") || dash.equals("-") || dash.equals("——")) {
            return StdValueForm.NONE;
        }
        if (s.contains(NOT_DETECTED_STD_TEXT)) {
            return StdValueForm.NOT_DETECTED;
        }
        if (s.contains(NOT_ALLOWED_USE_TEXT)) {
            return StdValueForm.NOT_USED;
        }
        if (s.startsWith("≤") || s.startsWith("<=") || s.startsWith("≦") || s.startsWith("<")) {
            String numPart = s.replaceFirst("^(≤|<=|≦|<)\\s*", "");
            return parseNumber(numPart) == null ? StdValueForm.UNKNOWN : StdValueForm.LE_NUMERIC;
        }
        return parseNumber(s) == null ? StdValueForm.UNKNOWN : StdValueForm.NUMERIC;
    }

    /**
     * 解析检验值形态（白名单 2 形态）。
     *
     * <p>「未检出」形态容错：含「未检出」字样（如「未检出（&lt;0.01）」）或英文 ND/N.D. 均视为未检出。</p>
     */
    public static TestValueForm parseTestValue(String raw) {
        String s = clean(raw);
        if (s == null) {
            return TestValueForm.UNKNOWN;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.contains(NOT_DETECTED_TEXT) || lower.equals("nd") || lower.equals("n.d.")) {
            return TestValueForm.NOT_DETECTED;
        }
        return parseNumber(s) == null ? TestValueForm.UNKNOWN : TestValueForm.NUMERIC;
    }

    /**
     * 解析数值（{@link BigDecimal}），失败返回 {@code null}。
     *
     * <p>禁止 {@code new BigDecimal(double)}——那会把 double 的二进制误差原样带进比较。
     * 允许剥离 {@code ≤}/{@code <=} 前缀与千分位逗号，字号统一为普通空格。</p>
     */
    public static BigDecimal parseNumber(String raw) {
        String s = clean(raw);
        if (s == null) {
            return null;
        }
        s = s.replace("≤", "").replace("≦", "").replace("<=", "").replace("<", "").trim();
        s = s.replace(",", "").replace("，", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 空白归一：全角空格/不间断空格视为空白，trim 后空串返回 null */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replace('\u00A0', ' ').replace("　", " ").trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 待判定统一出口：记 WARN 日志（「禁止静默判合格」的可观测抓手，单测对其做日志断言）。
     *
     * <p>{@code basis} 已内联具体的标准值/检验值/检出限，故日志无需再拼输入上下文。</p>
     */
    private JudgeOutcome pending(String basis) {
        log.warn("[判定引擎] 无法自动判定 → 落「待判定」（禁止静默判合格）：{}", basis);
        return JudgeOutcome.pending(basis);
    }
}
