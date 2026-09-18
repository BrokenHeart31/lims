package com.lims.service.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 AI 助手配置绑定（`lims.ai.*`，feature A，T03）。
 *
 * <p><b>为什么放配置而不是写死</b>：`base-url` / 模型名 / 上下文预算 / 护栏词表都属**部署差异**
 * （本机端口、量化版本、内存大小、业务词表随机构变化），写死进代码意味着「换台机器就要改代码重发版」。
 * 与 {@code lims.report.*} 同一处理原则。</p>
 *
 * <p><b>零外发红线</b>：{@code baseUrl} 固定回环口 127.0.0.1，Ollama 为仓库内独立进程
 * （{@code ai/runtime/}）；本类不接受任何远端地址语义。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lims.ai")
public class AiProperties {

    /** 本地 Ollama 服务地址（仅回环口，禁改外网地址） */
    private String baseUrl = "http://127.0.0.1:11434";

    /** 使用的模型（非思考变体；另以 think=false 双保险，避免 thinking 污染展示并降延迟） */
    private String model = "qwen3:4b-instruct";

    /** 是否请求模型「不思考」（qwen3 系），交互式助手必须为 true */
    private boolean think = false;

    /** 单次对话请求超时（毫秒）；CPU 推理慢，给足预算 */
    private int timeoutMs = 300_000;

    /** 健康检查超时（毫秒）：短超时 fail-soft，离线时快速返回 4201 */
    private int healthTimeoutMs = 1_500;

    /** GB 检索默认返回条数（top-N） */
    private int topN = 5;

    /** 投喂模型的标准条款正文预算（字符数，保守估算即可，不引 tokenizer） */
    private int maxContextChars = 3_000;

    /** 会话在客户端保留的最近消息条数（上下文窗口） */
    private int historyLimit = 6;

    /** GB 标准库根目录（含 inbox/ 与 parsed/ 子目录）；相对路径解析见 GbIndexServiceImpl */
    private String standardsDir = "ai/standards";

    /** 启动指引脚本（离线提示里展示给用户） */
    private String startScript = "ai/scripts/start-ollama.ps1";

    /**
     * OCR 侧车位目录（feature 增量 ai_flow_assistant）：逐页片段 + progress.json。
     * 相对路径按「进程 CWD → 逐级上溯」解析（见 ScanOcrJobStore.scanRoot）。
     */
    private String scanDir = "ai/standards/.scan";

    /** 流程引导配置（措辞层默认关闭，设计 §2.6）。 */
    private Flow flow = new Flow();

    /** 标准伴随查询配置（默认开启，可一键关并记忆于前端，设计 §2.5）。 */
    private Companion companion = new Companion();

    /**
     * 流程引导：事实层恒为确定性代码；措辞层（模型润色）**默认关闭**。
     *
     * <p>关闭时 {@code answer} 文本也由代码装配（零幻觉、离线可用）；开启时模型仅产出散文，
     * 结构化事实字段依旧原样下发（前端与单测以结构化字段为准）。</p>
     */
    @Data
    public static class Flow {
        /** 是否启用模型措辞层（默认 false）。 */
        private boolean phrasingEnabled = false;
    }

    /** 标准伴随查询开关（默认开启；前端另有会话级开关）。 */
    @Data
    public static class Companion {
        /** 是否启用上下文触发的标准伴随查询（默认 true）。 */
        private boolean enabled = true;
    }

    /**
     * 护栏词表（R5 可热配置）：{@code business}/{@code standard}/{@code chitchat} 三组，
     * 与 {@link DomainGuard} 的内置默认词表**合并**（配置为增量，不覆盖默认，避免漏配把功能拒掉）。
     */
    private Map<String, List<String>> guardLexicon = new HashMap<>();
}
