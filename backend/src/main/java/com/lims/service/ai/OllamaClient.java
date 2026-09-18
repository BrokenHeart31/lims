package com.lims.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 本地 Ollama 客户端（feature A，T03）—— JDK17 {@code java.net.http.HttpClient}，**零新增依赖**。
 *
 * <p><b>为什么不用 spring-ai / OkHttp</b>（设计 §2.2）：我们只用「POST 一个 JSON 拿 NDJSON 流」
 * 这一件事，{@code HttpClient} 即可完成；spring-ai 1.0.0-M6 是里程碑版本、会强引 ChatClient/Embedding
 * 自动配置（可能干扰既有 WebConfig/Security），引入即「用大炮打蚊子」。</p>
 *
 * <p><b>think=false 双保险</b>：qwen3 默认开思考模式（会输出 thinking 痕迹、且大幅拉高延迟），
 * 交互式助手必须关闭——这里在请求体里显式传 {@code "think": false}。</p>
 *
 * <p><b>错误分类</b>（不笼统 catch）：连接被拒/连接超时 → {@link ResultCode#AI_OFFLINE}；
 * 读超时 → {@link ResultCode#AI_TIMEOUT}；404 → {@link ResultCode#AI_MODEL_MISSING}；
 * 其它 IO → AI_OFFLINE。分类后由上层决定降级文案。</p>
 */
@Slf4j
@Component
public class OllamaClient {

    /** 对话消息（Ollama /api/chat 的 messages 元素）。 */
    public record ChatMessage(String role, String content) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /**
     * 生成参数。
     *
     * @param temperature 采样温度（分类/业务装配用 0.0 求确定性；问答用低温度 0.3）
     * @param json        是否要求模型输出严格 JSON（Ollama {@code format:json}）
     */
    public record GenOptions(double temperature, boolean json) {

        public static GenOptions answer() {
            return new GenOptions(0.3, false);
        }

        public static GenOptions deterministicJson() {
            return new GenOptions(0.0, true);
        }
    }

    /** 一次性对话结果。 */
    public record ChatResult(String content) {
    }

    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        // 连接超时用短超时（fail-soft）；单请求读超时在每次 HttpRequest 上按 timeout-ms 设置
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200, props.getHealthTimeoutMs())))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // =====================================================================
    // 健康检查
    // =====================================================================

    /**
     * 列出本地已就绪模型（{@code GET /api/tags}）。
     *
     * @throws BizException AI_OFFLINE（不可达）/ AI_TIMEOUT（超时）
     */
    public List<String> tags() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/api/tags"))
                .timeout(Duration.ofMillis(props.getHealthTimeoutMs()))
                .GET()
                .build();
        HttpResponse<String> resp = send(req, props.getHealthTimeoutMs());
        if (resp.statusCode() != 200) {
            throw new BizException(ResultCode.AI_OFFLINE);
        }
        try {
            JsonNode root = objectMapper.readTree(resp.body());
            List<String> names = new ArrayList<>();
            for (JsonNode m : root.path("models")) {
                String name = m.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException e) {
            throw new BizException(ResultCode.AI_OFFLINE);
        }
    }

    /** 服务是否可达（吞掉分类异常，供健康检查 fail-soft）。 */
    public boolean ping() {
        try {
            tags();
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    // =====================================================================
    // 对话（非流式 / 流式）
    // =====================================================================

    /** 非流式对话（自检 / 降级 / 单测）。 */
    public ChatResult chat(List<ChatMessage> messages, GenOptions opts) {
        String body = buildBody(messages, opts, false);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/api/chat"))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = send(req, props.getTimeoutMs());
        checkStatus(resp.statusCode());
        try {
            JsonNode root = objectMapper.readTree(resp.body());
            String content = root.path("message").path("content").asText("");
            return new ChatResult(content == null ? "" : content.trim());
        } catch (IOException e) {
            throw new BizException(ResultCode.AI_OFFLINE);
        }
    }

    /**
     * 流式对话：逐行消费 Ollama 的 NDJSON（{@code {"message":{"content":"…"},"done":false}}）。
     *
     * @param onToken 每收到一段增量文本回调一次（由上层封装为 SSE token 事件）
     */
    public void chatStream(List<ChatMessage> messages, GenOptions opts, Consumer<String> onToken) {
        String body = buildBody(messages, opts, true);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/api/chat"))
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<Stream<String>> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofLines());
        } catch (HttpConnectTimeoutException | ConnectException e) {
            throw new BizException(ResultCode.AI_OFFLINE);
        } catch (HttpTimeoutException e) {
            throw new BizException(ResultCode.AI_TIMEOUT);
        } catch (IOException e) {
            throw new BizException(ResultCode.AI_OFFLINE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.AI_OFFLINE);
        }
        checkStatus(resp.statusCode());
        try (Stream<String> lines = resp.body()) {
            lines.forEach(line -> {
                if (line == null || line.isBlank()) {
                    return;
                }
                try {
                    JsonNode node = objectMapper.readTree(line);
                    String token = node.path("message").path("content").asText("");
                    if (!token.isEmpty()) {
                        onToken.accept(token);
                    }
                } catch (IOException ignore) {
                    // 单行非 JSON（如 keep-alive 心跳）直接跳过，不中断整条流
                }
            });
        } catch (RuntimeException e) {
            // 流读取过程中连接中断
            throw new BizException(ResultCode.AI_OFFLINE);
        }
    }

    /**
     * 领域分类（护栏兜底）：要求模型输出严格 JSON，temperature=0。
     *
     * @return 原始 JSON 文本（由 DomainGuard 解析）
     */
    public String classifyJson(String systemPrompt, String question) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(question));
        return chat(messages, GenOptions.deterministicJson()).content();
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private String buildBody(List<ChatMessage> messages, GenOptions opts, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        body.put("stream", stream);
        body.put("think", props.isThink());
        if (opts.json()) {
            body.put("format", "json");
        }
        body.put("options", Map.of("temperature", opts.temperature()));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new BizException(ResultCode.ERROR);
        }
    }

    private HttpResponse<String> send(HttpRequest req, int timeoutMs) {
        try {
            return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (HttpConnectTimeoutException | ConnectException e) {
            // 连接层面：服务没起来
            throw new BizException(ResultCode.AI_OFFLINE);
        } catch (HttpTimeoutException e) {
            throw new BizException(ResultCode.AI_TIMEOUT);
        } catch (IOException e) {
            throw new BizException(ResultCode.AI_OFFLINE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.AI_OFFLINE);
        }
    }

    private void checkStatus(int status) {
        if (status == 200) {
            return;
        }
        if (status == 404) {
            // Ollama 对未知模型返回 404
            throw new BizException(ResultCode.AI_MODEL_MISSING);
        }
        log.warn("[ai] Ollama 返回非 200 状态：{}", status);
        throw new BizException(ResultCode.AI_OFFLINE);
    }
}
