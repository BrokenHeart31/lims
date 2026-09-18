package com.lims.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.ResultCode;
import com.lims.common.enums.AiDomain;
import com.lims.common.exception.BizException;
import com.lims.dto.AiChatContextDTO;
import com.lims.dto.AiChatDTO;
import com.lims.entity.AiConversation;
import com.lims.mapper.AiConversationMapper;
import com.lims.mapper.AiMessageMapper;
import com.lims.service.ai.companion.ValueAnchorAssembler;
import com.lims.service.ai.flow.FlowGuideAssembler;
import com.lims.service.ai.impl.AiAssistantServiceImpl;
import com.lims.vo.AiAnswerVO;
import com.lims.vo.AiStatusVO;
import com.lims.vo.GbSearchHitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 助手「Ollama 不可用」降级矩阵单元测试（feature A，T03 验收标准）。
 *
 * <p>固化四象限行为，证明 AI 是**旁路**、离线时业务零影响：</p>
 * <ol>
 *   <li>{@code /ai/status} 离线 → {@code online=false}，不抛；</li>
 *   <li>业务域 → **代码装配**确定性答案，离线照常作答（不调模型）；</li>
 *   <li>越界域 → 拒答（{@code refused=true}），不调模型；</li>
 *   <li>标准域有命中但模型离线 → 上抛 {@link ResultCode#AI_OFFLINE}(4201)（fail-loud，前端降级）；</li>
 *   <li>标准域**无命中** → 诚实兜底「未找到」且**不调模型**（绝不编造）。</li>
 * </ol>
 */
class AiAssistantServiceOfflineTest {

    private OllamaHealthChecker healthChecker;
    private OllamaClient ollamaClient;
    private DomainGuard domainGuard;
    private BusinessRuleAssembler ruleAssembler;
    private GbRetriever gbRetriever;
    private AiAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        healthChecker = mock(OllamaHealthChecker.class);
        ollamaClient = mock(OllamaClient.class);
        domainGuard = mock(DomainGuard.class);
        ruleAssembler = mock(BusinessRuleAssembler.class);
        gbRetriever = mock(GbRetriever.class);
        AiConversationMapper conversationMapper = mock(AiConversationMapper.class);
        AiMessageMapper messageMapper = mock(AiMessageMapper.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        FlowGuideAssembler flowGuideAssembler = mock(FlowGuideAssembler.class);
        ValueAnchorAssembler valueAnchorAssembler = mock(ValueAnchorAssembler.class);
        BusinessContextReader contextReader = mock(BusinessContextReader.class);
        AiProperties props = new AiProperties();

        // 会话落库回填 id（真实由自增回填）
        when(conversationMapper.insert(any(AiConversation.class))).thenAnswer(inv -> {
            AiConversation c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        // 流式在测试线程同步执行
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        service = new AiAssistantServiceImpl(healthChecker, ollamaClient, domainGuard, ruleAssembler,
                gbRetriever, conversationMapper, messageMapper, props, new ObjectMapper(), executor,
                flowGuideAssembler, valueAnchorAssembler, contextReader);
    }

    private static AiChatDTO ask(String question) {
        AiChatDTO dto = new AiChatDTO();
        dto.setQuestion(question);
        return dto;
    }

    private static GbSearchHitVO hit() {
        GbSearchHitVO h = new GbSearchHitVO();
        h.setStdNo("GB 2762-2022");
        h.setClauseNo("4.2");
        h.setSnippet("铅的限量为 0.5 mg/kg");
        h.setDocId(9L);
        h.setSourceFile("GB 2762-2022.txt");
        return h;
    }

    @Test
    @DisplayName("★状态：离线 → online=false（不抛）")
    void statusOffline() {
        AiStatusVO offline = new AiStatusVO();
        offline.setOnline(false);
        when(healthChecker.check()).thenReturn(offline);

        assertFalse(service.status().isOnline());
    }

    @Test
    @DisplayName("★业务域离线：代码装配确定性作答，绝不调模型（业务零影响）")
    void businessOfflineStillAnswers() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.BUSINESS, "业务词"));
        when(ruleAssembler.detectIntent(anyString())).thenReturn(BusinessRuleAssembler.Intent.STATUS_EXPLAIN);
        when(ruleAssembler.answer(any(), anyString(), any())).thenReturn("样品当前处于「检验中」。");

        AiAnswerVO vo = service.chat(ask("检验中状态是什么意思"));

        assertNotNull(vo.getAnswer());
        assertTrue(vo.getAnswer().contains("检验中"));
        assertFalse(vo.isRefused());
        verify(ollamaClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("★越界域：同事口吻拒答 + 建议，绝不调模型")
    void otherOfflineRefused() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.OTHER, "命中越界词"));

        AiAnswerVO vo = service.chat(ask("今天天气怎么样"));

        assertTrue(vo.isRefused());
        assertFalse(vo.getSuggestions().isEmpty());
        verify(ollamaClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("★标准域有命中但模型离线 → BizException(AI_OFFLINE 4201)")
    void standardWithHitOffline4201() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.STANDARD, "标准词"));
        when(gbRetriever.search(anyString(), any(Integer.class), any())).thenReturn(List.of(hit()));
        when(ollamaClient.chat(any(), any())).thenThrow(new BizException(ResultCode.AI_OFFLINE));

        BizException e = assertThrows(BizException.class, () -> service.chat(ask("铅的限量是多少")));
        assertEquals(ResultCode.AI_OFFLINE.getCode(), e.getCode());
    }

    @Test
    @DisplayName("★标准域无命中 → 诚实兜底「未找到」且不调模型（绝不编造标准号）")
    void standardNoHitNoModel() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.STANDARD, "标准词"));
        when(gbRetriever.search(anyString(), any(Integer.class), any())).thenReturn(List.of());

        AiAnswerVO vo = service.chat(ask("某不存在标准的限量"));

        assertFalse(vo.isRefused());
        assertTrue(vo.getAnswer().contains("未在本地标准库找到"));
        assertTrue(vo.getCitations().isEmpty());
        verify(ollamaClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("★流式：业务域离线 → chatStream 正常返回 emitter（同步执行不抛）")
    void chatStreamBusinessOffline() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.BUSINESS, "业务词"));
        when(ruleAssembler.detectIntent(anyString())).thenReturn(BusinessRuleAssembler.Intent.GENERAL);
        when(ruleAssembler.answer(any(), anyString(), any())).thenReturn("通用引导");

        assertNotNull(service.chatStream(ask("我有哪些权限")));
        verify(ollamaClient, never()).chatStream(any(), any(), any());
    }

    @Test
    @DisplayName("★标准未收录（点名 GB 2762，库内只有 GB 2763）→ 确定性诚实说明，不拿别的标准凑数、不调模型")
    void missingStandardFailsLoud() {
        // 实测背景（2026-09-18）：问「GB 2762 铅的限量是多少」，旧实现返回了 5 条 GB 2763 的
        // 无关条款，等于给了错误出处——比「没有出处」更危险，必须诚实说明未收录。
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.STANDARD, "标准词"));
        when(gbRetriever.indexedStdNos()).thenReturn(List.of("GB 2763-2021"));

        AiAnswerVO vo = service.chat(ask("GB 2762 铅的限量是多少？"));

        assertTrue(vo.getAnswer().contains("GB 2762"));
        assertTrue(vo.getAnswer().contains("未收录"));
        assertTrue(vo.getAnswer().contains("GB 2763-2021"));   // 明确告知已收录清单
        assertTrue(vo.getCitations().isEmpty());               // 绝不套用其他标准的条款
        verify(gbRetriever, never()).search(anyString(), any(Integer.class), any());
        verify(ollamaClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("★点名了已收录标准 → 检索按该标准限定（跨标准不串味）")
    void mentionedIndexedStandardFilters() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.STANDARD, "标准词"));
        when(gbRetriever.indexedStdNos()).thenReturn(List.of("GB 2763-2021"));
        when(gbRetriever.search(anyString(), any(Integer.class), any())).thenReturn(List.of(hit()));
        when(ollamaClient.chat(any(), any())).thenReturn(new OllamaClient.ChatResult("毒死蜱限量见 4.121。"));

        service.chat(ask("GB 2763-2021 里毒死蜱的限量"));

        verify(gbRetriever).search(anyString(), any(Integer.class),
                org.mockito.ArgumentMatchers.eq("GB 2763-2021"));
    }

    @Test
    @DisplayName("上下文：标准号透传给检索器（限定范围）")
    void stdNoPassedToRetriever() {
        when(domainGuard.classify(any(), any())).thenReturn(new DomainGuard.GuardResult(AiDomain.STANDARD, "标准词"));
        when(gbRetriever.search(anyString(), any(Integer.class), any())).thenReturn(List.of());
        AiChatContextDTO ctx = new AiChatContextDTO();
        ctx.setStdNo("GB 2762-2022");
        AiChatDTO dto = ask("限量是多少");
        dto.setContext(ctx);

        service.chat(dto);

        verify(gbRetriever).search(anyString(), any(Integer.class), org.mockito.ArgumentMatchers.eq("GB 2762-2022"));
    }
}
