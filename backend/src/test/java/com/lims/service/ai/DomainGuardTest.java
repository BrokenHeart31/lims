package com.lims.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.enums.AiDomain;
import com.lims.common.exception.BizException;
import com.lims.dto.AiChatContextDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 领域护栏单元测试（feature A，T03 / PRD T1）。
 *
 * <p>固化：越界 100% 拒答（OTHER）；业务词→BUSINESS；标准词/标准号→STANDARD；
 * 规则不确定才交模型；模型不可用则默认 STANDARD（不抛给用户）。</p>
 */
class DomainGuardTest {

    private OllamaClient ollamaClient;
    private DomainGuard guard;

    @BeforeEach
    void setUp() {
        ollamaClient = mock(OllamaClient.class);
        guard = new DomainGuard(new AiProperties(), ollamaClient, new ObjectMapper());
    }

    @Test
    @DisplayName("★越界闲聊 → OTHER（命中黑名单，不调模型）")
    void chitchat() {
        var r = guard.classify("今天天气怎么样", null);
        assertEquals(AiDomain.OTHER, r.domain());
        verify(ollamaClient, never()).classifyJson(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("业务词 → BUSINESS（状态解释）")
    void business() {
        assertEquals(AiDomain.BUSINESS, guard.classify("检验中状态是什么意思", null).domain());
        assertEquals(AiDomain.BUSINESS, guard.classify("样品下一步是什么", null).domain());
        assertEquals(AiDomain.BUSINESS, guard.classify("我有哪些权限", null).domain());
    }

    @Test
    @DisplayName("标准词/标准号 → STANDARD")
    void standard() {
        assertEquals(AiDomain.STANDARD, guard.classify("铅的限量是多少", null).domain());
        assertEquals(AiDomain.STANDARD, guard.classify("GB 2762 规定了什么", null).domain());
    }

    @Test
    @DisplayName("★歧义（业务词+标准词）：问题含标准号 → STANDARD；否则含样品上下文 → BUSINESS")
    void ambiguous() {
        AiChatContextDTO ctx = new AiChatContextDTO();
        ctx.setSampleNo("JK(2023)-SA-001");
        assertAll(
                // 业务词(样品)+标准词(限量)，且问题里出现标准号 → 标准域
                () -> assertEquals(AiDomain.STANDARD,
                        guard.classify("样品按 GB 2762 的铅限量怎么判", null).domain()),
                // 业务词(样品)+标准词(限量)，无标准号 + 有样品上下文 → 业务域
                () -> assertEquals(AiDomain.BUSINESS,
                        guard.classify("这个样品的限量怎么处理", ctx).domain()),
                // 同样问题但无样品上下文 → 仍退化为业务域（更安全）
                () -> assertEquals(AiDomain.BUSINESS,
                        guard.classify("这个样品的限量怎么处理", null).domain())
        );
    }

    @Test
    @DisplayName("规则不确定 → 交模型分类（温度 0，严格 JSON）")
    void modelClassify() {
        when(ollamaClient.classifyJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"domain\":\"other\",\"reason\":\"与检验无关\"}");
        var r = guard.classify("苹果好吃吗", null);
        assertEquals(AiDomain.OTHER, r.domain());
        assertTrue(r.reason().startsWith("模型判定"));
    }

    @Test
    @DisplayName("★规则不确定 + 模型不可用 → 默认 STANDARD（不抛给用户，由上层据域降级）")
    void modelUnavailableFallsBackToStandard() {
        when(ollamaClient.classifyJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new BizException(4201, "offline"));
        var r = guard.classify("苹果好吃吗", null);
        assertEquals(AiDomain.STANDARD, r.domain());
    }

    @Test
    @DisplayName("空问题 → OTHER")
    void blank() {
        assertEquals(AiDomain.OTHER, guard.classify("   ", null).domain());
        assertEquals(AiDomain.OTHER, guard.classify(null, null).domain());
    }
}
