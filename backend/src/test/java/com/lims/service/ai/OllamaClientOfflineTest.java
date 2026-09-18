package com.lims.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OllamaClient 离线路径**实测**（feature A，T03 验收标准「Ollama 不可用时系统行为正确」）。
 *
 * <p>把 base-url 指向一个**确定无人监听**的回环端口（{@code 127.0.0.1:1}），触发真实
 * {@code ConnectException}，断言错误被**精确分类**为 {@link ResultCode#AI_OFFLINE}(4201)，
 * 而不是笼统的 IOException——这正是「AI 不可用」路径的第一手证据（不依赖 mock）。</p>
 */
class OllamaClientOfflineTest {

    /** 指向死端口的客户端（回环 1 号端口必拒连）。 */
    private OllamaClient offlineClient() {
        AiProperties props = new AiProperties();
        props.setBaseUrl("http://127.0.0.1:1");
        props.setHealthTimeoutMs(300);
        props.setTimeoutMs(300);
        return new OllamaClient(props, new ObjectMapper());
    }

    @Test
    @DisplayName("★tags：服务不可达 → BizException(AI_OFFLINE 4201)")
    void tagsOffline() {
        BizException e = assertThrows(BizException.class, () -> offlineClient().tags());
        assertEquals(ResultCode.AI_OFFLINE.getCode(), e.getCode());
    }

    @Test
    @DisplayName("★chat：服务不可达 → BizException(AI_OFFLINE 4201)，不上抛原始 IOException")
    void chatOffline() {
        BizException e = assertThrows(BizException.class, () ->
                offlineClient().chat(List.of(OllamaClient.ChatMessage.user("你好")),
                        OllamaClient.GenOptions.answer()));
        assertEquals(ResultCode.AI_OFFLINE.getCode(), e.getCode());
    }

    @Test
    @DisplayName("★chatStream：服务不可达 → BizException(AI_OFFLINE 4201)")
    void chatStreamOffline() {
        BizException e = assertThrows(BizException.class, () ->
                offlineClient().chatStream(List.of(OllamaClient.ChatMessage.user("你好")),
                        OllamaClient.GenOptions.answer(), t -> {
                        }));
        assertEquals(ResultCode.AI_OFFLINE.getCode(), e.getCode());
    }

    @Test
    @DisplayName("ping：不可达 → false（fail-soft，绝不抛）")
    void pingOffline() {
        assertFalse(offlineClient().ping());
    }
}
