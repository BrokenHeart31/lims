package com.lims.service.ai;

import com.lims.dto.AiChatContextDTO;
import com.lims.service.ai.flow.FlowGuideAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务域**确定性装配**单元测试（feature A，T3 / 设计 §9）。
 *
 * <p>固化「业务问题不调模型」：状态解释 / 下一步 / 权限全部由系统权威枚举装配，
 * 因此**离线可用且零幻觉**。</p>
 */
class BusinessRuleAssemblerTest {

    private BusinessContextReader reader;
    private FlowGuideAssembler flowGuideAssembler;
    private BusinessRuleAssembler assembler;

    @BeforeEach
    void setUp() {
        reader = mock(BusinessContextReader.class);
        flowGuideAssembler = mock(FlowGuideAssembler.class);
        assembler = new BusinessRuleAssembler(reader, flowGuideAssembler);
    }

    @Test
    @DisplayName("意图判别：下一步 / 权限 / 状态 / 其它")
    void detectIntent() {
        assertAll(
                () -> assertEquals(BusinessRuleAssembler.Intent.NEXT_STEP,
                        assembler.detectIntent("这个样品下一步做什么")),
                () -> assertEquals(BusinessRuleAssembler.Intent.PERMISSION_EXPLAIN,
                        assembler.detectIntent("我有哪些权限")),
                () -> assertEquals(BusinessRuleAssembler.Intent.STATUS_EXPLAIN,
                        assembler.detectIntent("检验中是什么意思")),
                () -> assertEquals(BusinessRuleAssembler.Intent.GENERAL,
                        assembler.detectIntent("随便问问"))
        );
    }

    @Test
    @DisplayName("★状态解释：带 status 上下文 → 命中权威枚举文案（零模型）")
    void statusExplainWithCtx() {
        AiChatContextDTO ctx = new AiChatContextDTO();
        ctx.setStatus(40); // S40 已安排

        String answer = assembler.answer(BusinessRuleAssembler.Intent.STATUS_EXPLAIN, "状态", ctx);

        assertTrue(answer.contains("已安排"));
        assertTrue(answer.contains("40"));
    }

    @Test
    @DisplayName("状态解释：无上下文 → 列出 S10→S90 全量说明")
    void statusExplainWithoutCtx() {
        String answer = assembler.answer(BusinessRuleAssembler.Intent.STATUS_EXPLAIN, "状态", null);

        assertTrue(answer.contains("S10"));
        assertTrue(answer.contains("S90"));
    }

    @Test
    @DisplayName("下一步：缺样品号/状态 → 引导补充，不臆测")
    void nextStepNeedsSampleNo() {
        String answer = assembler.answer(BusinessRuleAssembler.Intent.NEXT_STEP, "下一步", null);
        assertTrue(answer.contains("样品编号"));
    }

    @Test
    @DisplayName("权限解释：按 resource 段去重展示功能域，并给出细粒度计数")
    void permissionExplain() {
        when(reader.readRolePermissions())
                .thenReturn(List.of("ai:chat", "ai:kb:query", "sample:view"));

        String answer = assembler.answer(BusinessRuleAssembler.Intent.PERMISSION_EXPLAIN, "权限", null);

        assertTrue(answer.contains("ai"));
        assertTrue(answer.contains("sample"));
        assertTrue(answer.contains("3 项"));
    }

    @Test
    @DisplayName("权限解释：无权限（未登录）→ 诚实说明并引导")
    void permissionEmpty() {
        when(reader.readRolePermissions()).thenReturn(List.of());
        String answer = assembler.answer(BusinessRuleAssembler.Intent.PERMISSION_EXPLAIN, "权限", null);
        assertTrue(answer.contains("未读取到"));
    }

    @Test
    @DisplayName("通用业务问 → 回显问题并给出三方向引导")
    void generalEchoesQuestion() {
        String answer = assembler.answer(BusinessRuleAssembler.Intent.GENERAL, "帮我看看这个", null);
        assertTrue(answer.contains("帮我看看这个"));
    }
}
