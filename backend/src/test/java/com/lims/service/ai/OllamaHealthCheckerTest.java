package com.lims.service.ai;

import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import com.lims.mapper.GbDocumentMapper;
import com.lims.vo.AiStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 健康检查 fail-soft 单元测试（feature A，T03 / api-spec A1）。
 *
 * <p>固化：离线时 {@code check()} **永不抛异常**，返回 {@code online=false} + 启动指引，
 * 前端角标可据此显示「不可用」而**不弹全局错误**。</p>
 */
class OllamaHealthCheckerTest {

    private OllamaClient ollamaClient;
    private OllamaHealthChecker checker;

    @BeforeEach
    void setUp() {
        ollamaClient = mock(OllamaClient.class);
        GbDocumentMapper gbDocumentMapper = mock(GbDocumentMapper.class);
        // 三就绪之「标准索引就绪」探测：默认返回 0（未导入），不影响在线/离线主判定
        when(gbDocumentMapper.selectCount(any())).thenReturn(0L);
        AiProperties props = new AiProperties();
        props.setModel("qwen3:4b-instruct");
        props.setStartScript("ai/scripts/deploy-ollama.ps1");
        checker = new OllamaHealthChecker(ollamaClient, props, gbDocumentMapper);
    }

    @Test
    @DisplayName("★离线 → online=false、latency=-1、hint 含启动脚本（不抛）")
    void offlineFailSoft() {
        when(ollamaClient.tags()).thenThrow(new BizException(ResultCode.AI_OFFLINE));

        AiStatusVO vo = checker.check();

        assertFalse(vo.isOnline());
        assertFalse(vo.isModelPresent());
        assertEquals(-1L, vo.getLatencyMs());
        assertTrue(vo.getHint().contains("deploy-ollama.ps1"));
    }

    @Test
    @DisplayName("在线且模型就绪 → online=true、modelPresent=true")
    void onlineModelPresent() {
        when(ollamaClient.tags()).thenReturn(List.of("llama3:8b", "qwen3:4b-instruct"));

        AiStatusVO vo = checker.check();

        assertTrue(vo.isOnline());
        assertTrue(vo.isModelPresent());
        assertTrue(vo.getHint().contains("正常"));
    }

    @Test
    @DisplayName("在线但模型未就绪 → modelPresent=false、hint 指引拉模型")
    void onlineModelMissing() {
        when(ollamaClient.tags()).thenReturn(List.of("llama3:8b"));

        AiStatusVO vo = checker.check();

        assertTrue(vo.isOnline());
        assertFalse(vo.isModelPresent());
        assertTrue(vo.getHint().contains("未就绪"));
    }
}
