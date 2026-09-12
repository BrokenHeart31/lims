package com.lims.service.judge;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判定引擎单元测试（T-601）。
 *
 * <p><b>测试基线（白名单定稿第 4 节 / DECISIONS T-901 裁定）</b>：用**构造数据**直接调用引擎
 * 覆盖 jt1 / jt2 / jt3 与 `--` 的全部矩阵分支，不走标准库匹配。
 * 「当前生产数据触发不到 jt2/jt3」≠ 可删分支——它们是标准库扩充后的既定语义。</p>
 *
 * <p><b>日志断言</b>：所有「待判定」分支必须落 WARN 日志（禁止静默判合格），
 * 本测试通过 logback {@link ListAppender} 捕获并校验。</p>
 */
class JudgeEngineTest {

    private final JudgeEngine engine = new JudgeEngine();

    private Logger engineLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        engineLogger = (Logger) LoggerFactory.getLogger(JudgeEngine.class);
        appender = new ListAppender<>();
        appender.start();
        engineLogger.addAppender(appender);
        engineLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        engineLogger.detachAppender(appender);
    }

    // ------------------------------------------------------------------ 工具

    private JudgeOutcome judge(int judgeType, String stdValue, String lowerLimit, String testValue) {
        return engine.judge(new JudgeInput(judgeType, stdValue, lowerLimit, testValue, null));
    }

    private JudgeOutcome judgeManual(int judgeType, String stdValue, String testValue, Integer manual) {
        return engine.judge(new JudgeInput(judgeType, stdValue, null, testValue, manual));
    }

    /** 捕获到的 WARN 日志正文 */
    private List<String> warnMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertWarned(String expectedFragment) {
        List<String> warns = warnMessages();
        assertFalse(warns.isEmpty(), "待判定分支必须落 WARN 日志（禁止静默判合格）");
        assertTrue(warns.stream().anyMatch(m -> m.contains(expectedFragment)),
                "WARN 日志应包含「" + expectedFragment + "」，实际：" + warns);
    }

    // =========================================================================
    // jt1 限量比较（D1）
    // =========================================================================

    @Nested
    @DisplayName("jt1 限量比较")
    class LimitCompare {

        @Test
        @DisplayName("数值 ≤ 限量 → 合格（自动判定来源）")
        void withinLimit() {
            JudgeOutcome out = judge(1, "0.5", "0.02", "0.30");

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertEquals(ConclusionSource.ENGINE, out.source()),
                    () -> assertFalse(out.requiresAttention()),
                    () -> assertTrue(out.basis().contains("≤ 限量"), "依据应说明比对关系：" + out.basis())
            );
            assertTrue(warnMessages().isEmpty(), "合格判定不应产生 WARN");
        }

        @Test
        @DisplayName("数值 > 限量 → 不合格")
        void exceedsLimit() {
            JudgeOutcome out = judge(1, "0.5", "0.02", "0.51");

            assertAll(
                    () -> assertEquals(ResultConclusion.UNQUALIFIED, out.conclusion()),
                    () -> assertTrue(out.basis().contains("> 限量"), out.basis())
            );
        }

        @Test
        @DisplayName("数值 = 限量（边界）→ 合格（≤ 为闭区间）")
        void equalsLimit() {
            assertEquals(ResultConclusion.QUALIFIED, judge(1, "0.5", null, "0.5").conclusion());
        }

        @Test
        @DisplayName("≤X 上限写法 → 与纯数值同一语义")
        void lePrefixForm() {
            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, judge(1, "≤0.25", null, "0.25").conclusion()),
                    () -> assertEquals(ResultConclusion.UNQUALIFIED, judge(1, "≤0.25", null, "0.26").conclusion()),
                    () -> assertEquals(ResultConclusion.QUALIFIED, judge(1, "<=0.25", null, "0.10").conclusion())
            );
        }

        @Test
        @DisplayName("未检出 → 合格")
        void notDetected() {
            JudgeOutcome out = judge(1, "0.5", "0.02", "未检出");

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertTrue(out.basis().contains("未检出"), out.basis())
            );
        }

        @Test
        @DisplayName("★D1：数值 < 最低检出限 → 视同未检出 → 合格")
        void belowLowerLimit() {
            JudgeOutcome out = judge(1, "0.5", "0.02", "0.01");

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertTrue(out.basis().contains("视同未检出"), out.basis())
            );
        }

        @Test
        @DisplayName("★浮点：小数位不同（0.5 vs 0.50）仍判相等 → 合格（走 compareTo 而非 equals）")
        void scaleInsensitiveCompare() {
            // BigDecimal.equals("0.5","0.50") == false，若误用 equals 会判成「不等于」而出错
            JudgeOutcome out = judge(1, "0.50", null, "0.5");

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertTrue(out.basis().contains("≤ 限量"), out.basis())
            );
        }

        @Test
        @DisplayName("检出限为 NULL 时不做「视同未检出」，按数值直接比对")
        void nullLowerLimitFallsThrough() {
            assertEquals(ResultConclusion.UNQUALIFIED, judge(1, "0.5", null, "0.6").conclusion());
            assertEquals(ResultConclusion.QUALIFIED, judge(1, "0.5", null, "0.4").conclusion());
        }
    }

    // =========================================================================
    // jt2 不得检出 / 不得使用（D2）
    // =========================================================================

    @Nested
    @DisplayName("jt2 不得检出/不得使用")
    class NotDetected {

        @Test
        @DisplayName("未检出 → 合格")
        void notDetected() {
            JudgeOutcome out = judge(2, "不得检出", "0.02", "未检出");

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertFalse(out.requiresAttention())
            );
        }

        @Test
        @DisplayName("★D2：数值 ≥ 检出限 → 检出 → 不合格")
        void detectedAtLimit() {
            JudgeOutcome out = judge(2, "不得检出", "0.02", "0.02");

            assertAll(
                    () -> assertEquals(ResultConclusion.UNQUALIFIED, out.conclusion()),
                    () -> assertTrue(out.basis().contains("判定为检出"), out.basis())
            );
        }

        @Test
        @DisplayName("★D2：数值 < 检出限 → 视同未检出 → 合格")
        void belowLimit() {
            JudgeOutcome out = judge(2, "不得检出", "0.02", "0.01");

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertTrue(out.basis().contains("视同未检出"), out.basis())
            );
        }

        @Test
        @DisplayName("★D2：数值 且 检出限为 NULL → 待判定 + WARN（禁止默判合格）")
        void numericWithNullLowerLimit() {
            JudgeOutcome out = judge(2, "不得检出", null, "0.01");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.requiresAttention()),
                    () -> assertTrue(out.basis().contains("未维护最低检出限"), out.basis())
            );
            assertWarned("未维护最低检出限");
        }

        @Test
        @DisplayName("不得使用 型同矩阵：数值 ≥ 检出限 → 不合格")
        void notAllowedUse() {
            assertEquals(ResultConclusion.UNQUALIFIED, judge(2, "不得使用", "0.05", "0.10").conclusion());
            assertEquals(ResultConclusion.QUALIFIED, judge(2, "不得使用", "0.05", "未检出").conclusion());
        }
    }

    // =========================================================================
    // 标准值 -- 无判定依据（D4）
    // =========================================================================

    @Nested
    @DisplayName("标准值 --（无判定依据，D4 矩阵）")
    class NoBasis {

        @Test
        @DisplayName("未检出 → 合格")
        void notDetected() {
            assertEquals(ResultConclusion.QUALIFIED, judge(1, "--", "0.02", "未检出").conclusion());
        }

        @Test
        @DisplayName("数值 < 检出限 → 合格（视同未检出）")
        void belowLimit() {
            assertEquals(ResultConclusion.QUALIFIED, judge(1, "--", "0.02", "0.01").conclusion());
        }

        @Test
        @DisplayName("★D4：数值 ≥ 检出限 → 待判定 + WARN")
        void atOrAboveLimit() {
            JudgeOutcome out = judge(1, "--", "0.02", "0.02");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.requiresAttention()),
                    () -> assertTrue(out.basis().contains("无判定依据"), out.basis())
            );
            assertWarned("无判定依据");
        }

        @Test
        @DisplayName("★D4：数值 且 检出限为 NULL → 待判定 + WARN")
        void numericWithNullLowerLimit() {
            JudgeOutcome out = judge(1, "--", null, "0.5");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.requiresAttention())
            );
            assertWarned("无法自动判定");
        }

        @Test
        @DisplayName("jt2 遇 -- 同样走 D4 矩阵（未检出 → 合格）")
        void jt2WithDash() {
            assertEquals(ResultConclusion.QUALIFIED, judge(2, "--", "0.02", "未检出").conclusion());
        }

        @Test
        @DisplayName("全角破折号 —— 与迁移残留的单个 - 均识别为「无依据」")
        void dashVariants() {
            assertEquals(JudgeEngine.StdValueForm.NONE, JudgeEngine.parseStdValue("——"));
            assertEquals(JudgeEngine.StdValueForm.NONE, JudgeEngine.parseStdValue("-"));
            assertEquals(JudgeEngine.StdValueForm.NONE, JudgeEngine.parseStdValue("--"));
        }
    }

    // =========================================================================
    // jt3 文本/感官人工（AGENTS 7.3 规则 3）
    // =========================================================================

    @Nested
    @DisplayName("jt3 文本/感官人工")
    class Manual {

        @Test
        @DisplayName("检验员选「合格」→ 合格，来源=人工判定")
        void manualQualified() {
            JudgeOutcome out = judgeManual(3, "符合要求", "色泽正常", 1);

            assertAll(
                    () -> assertEquals(ResultConclusion.QUALIFIED, out.conclusion()),
                    () -> assertEquals(ConclusionSource.MANUAL, out.source()),
                    () -> assertTrue(out.basis().contains("人工判定"), out.basis())
            );
            assertTrue(warnMessages().isEmpty(), "人工判定成功不应 WARN");
        }

        @Test
        @DisplayName("检验员选「不合格」→ 不合格，来源=人工判定")
        void manualUnqualified() {
            assertEquals(ResultConclusion.UNQUALIFIED, judgeManual(3, "符合要求", "有异味", 2).conclusion());
        }

        @Test
        @DisplayName("未选择 → 待判定 + WARN")
        void manualMissing() {
            JudgeOutcome out = judgeManual(3, "符合要求", "色泽正常", null);

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.requiresAttention())
            );
            assertWarned("需检验员人工选择");
        }

        @Test
        @DisplayName("人工结论传入「待判定(3)」视为未选择 → 待判定")
        void manualPendingRejected() {
            assertEquals(ResultConclusion.PENDING, judgeManual(3, "符合要求", "x", 3).conclusion());
        }
    }

    // =========================================================================
    // 白名单闭集之外：一律待判定 + WARN
    // =========================================================================

    @Nested
    @DisplayName("白名单外输入（fail-loud）")
    class OutOfWhitelist {

        @Test
        @DisplayName("jt1 标准值为文本 → 待判定 + WARN")
        void textStdValue() {
            JudgeOutcome out = judge(1, "符合要求", null, "0.1");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.requiresAttention()),
                    () -> assertTrue(out.basis().contains("不在判定白名单闭集内"), out.basis())
            );
            assertWarned("不在判定白名单闭集内");
        }

        @Test
        @DisplayName("检验值为非数值文本 → 待判定 + WARN")
        void textTestValue() {
            JudgeOutcome out = judge(1, "0.5", null, "淡黄色");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.basis().contains("既非数值也非"), out.basis())
            );
            assertWarned("既非数值也非");
        }

        @Test
        @DisplayName("jt2 标准值为数值 → 口径矛盾 → 待判定 + WARN")
        void jt2WithNumericStd() {
            JudgeOutcome out = judge(2, "0.5", "0.02", "0.1");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.basis().contains("口径矛盾"), out.basis())
            );
            assertWarned("口径矛盾");
        }

        @Test
        @DisplayName("jt1 标准值为「不得检出」→ 口径矛盾 → 待判定 + WARN")
        void jt1WithNotDetectedStd() {
            JudgeOutcome out = judge(1, "不得检出", "0.02", "0.1");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.basis().contains("口径矛盾"), out.basis())
            );
            assertWarned("口径矛盾");
        }

        @Test
        @DisplayName("判定类型非法（9）→ 待判定 + WARN")
        void invalidJudgeType() {
            JudgeOutcome out = judge(9, "0.5", null, "0.1");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.basis().contains("判定类型非法"), out.basis())
            );
            assertWarned("判定类型非法");
        }

        @Test
        @DisplayName("未录入检验值 → 待判定 + WARN")
        void blankTestValue() {
            JudgeOutcome out = judge(1, "0.5", null, "   ");

            assertAll(
                    () -> assertEquals(ResultConclusion.PENDING, out.conclusion()),
                    () -> assertTrue(out.basis().contains("未录入检验值"), out.basis())
            );
            assertWarned("未录入检验值");
        }

        @Test
        @DisplayName("标准值为空 → 待判定 + WARN")
        void blankStdValue() {
            JudgeOutcome out = judge(1, null, null, "0.1");
            assertEquals(ResultConclusion.PENDING, out.conclusion());
            assertWarned("缺少判定依据");
        }
    }

    // =========================================================================
    // 形态解析与浮点安全（可直接断言的静态口）
    // =========================================================================

    @Nested
    @DisplayName("形态解析 / 数值解析")
    class Parsing {

        @Test
        @DisplayName("标准值 5 形态识别完整")
        void stdValueForms() {
            assertAll(
                    () -> assertEquals(JudgeEngine.StdValueForm.NUMERIC, JudgeEngine.parseStdValue("0.5")),
                    () -> assertEquals(JudgeEngine.StdValueForm.NUMERIC, JudgeEngine.parseStdValue(" 100 ")),
                    () -> assertEquals(JudgeEngine.StdValueForm.LE_NUMERIC, JudgeEngine.parseStdValue("≤0.25")),
                    () -> assertEquals(JudgeEngine.StdValueForm.NOT_DETECTED, JudgeEngine.parseStdValue("不得检出")),
                    () -> assertEquals(JudgeEngine.StdValueForm.NOT_USED, JudgeEngine.parseStdValue("不得使用")),
                    () -> assertEquals(JudgeEngine.StdValueForm.NONE, JudgeEngine.parseStdValue("--")),
                    () -> assertEquals(JudgeEngine.StdValueForm.UNKNOWN, JudgeEngine.parseStdValue("符合要求")),
                    () -> assertEquals(JudgeEngine.StdValueForm.UNKNOWN, JudgeEngine.parseStdValue(null))
            );
        }

        @Test
        @DisplayName("迁移残留的参考项星号先剥离，不影响形态判定")
        void trailingStarStripped() {
            assertEquals(JudgeEngine.StdValueForm.NUMERIC, JudgeEngine.parseStdValue("0.5*"));
        }

        @Test
        @DisplayName("检验值 2 形态识别：数值 / 未检出（含「未检出（<0.01）」与 ND）")
        void testValueForms() {
            assertAll(
                    () -> assertEquals(JudgeEngine.TestValueForm.NUMERIC, JudgeEngine.parseTestValue("0.10")),
                    () -> assertEquals(JudgeEngine.TestValueForm.NOT_DETECTED, JudgeEngine.parseTestValue("未检出")),
                    () -> assertEquals(JudgeEngine.TestValueForm.NOT_DETECTED, JudgeEngine.parseTestValue("未检出（<0.01）")),
                    () -> assertEquals(JudgeEngine.TestValueForm.NOT_DETECTED, JudgeEngine.parseTestValue("ND")),
                    () -> assertEquals(JudgeEngine.TestValueForm.UNKNOWN, JudgeEngine.parseTestValue("淡黄色"))
            );
        }

        @Test
        @DisplayName("浮点陷阱：0.1+0.2 的 double 结果不会被当成 0.3（BigDecimal 严格比较）")
        void doublePrecision() {
            String sum = String.valueOf(0.1 + 0.2); // 0.30000000000000004
            // 实测值严格大于 0.3 → 不合格；若用 double == 会误判为「等于」
            assertEquals(ResultConclusion.UNQUALIFIED, judge(1, "0.3", null, sum).conclusion());
        }
    }
}
