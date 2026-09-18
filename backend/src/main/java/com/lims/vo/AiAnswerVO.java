package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 回答（结构化对象，feature A，T03 / api-spec A2）。
 *
 * <p><b>为什么是结构化对象而不是 Markdown 字符串</b>（设计 §2.6）：① 前端零 Markdown 依赖
 * （少 ~30~90KB 主包体积 + 免引 sanitizer，消 XSS 面）；② 「引用卡片 + 仅供参考标注」成为
 * **结构性必然**；③ {@code refused} / {@code domain} 是机器可读字段，供审计与前端分支渲染。</p>
 *
 * <p><b>拒答不是错误</b>：越界时 {@code refused=true} 但 {@code code=0}（正常业务结果）。</p>
 */
@Data
public class AiAnswerVO {

    /** 会话ID（新会话时后端已建并回填） */
    private Long conversationId;

    /** 助手消息ID */
    private Long messageId;

    /** 回答正文（纯文本，按 \n 分段；前端自行拆段渲染，不含 Markdown） */
    private String answer;

    /** 是否越界拒答 */
    private boolean refused;

    /** 领域：business / standard / other */
    private String domain;

    /** 置信度：high / medium / low（由命中数与域推导，供前端弱提示） */
    private String confidence;

    /** 模型名（业务域确定性答案时为 null，表示未调用模型） */
    private String model;

    /** 本次耗时（毫秒） */
    private long elapsedMs;

    /** 引用清单（业务域可为空；标准域无命中时为空且 answer 明说未找到） */
    private List<AiCitationVO> citations = new ArrayList<>();

    /** 拒答时的替代建议（2~3 条，同事口吻引导回业务） */
    private List<Suggestion> suggestions = new ArrayList<>();

    /** 业务域流程引导（feature 增量：NEXT_STEP / GUIDE 时装配；事实层确定性，不调模型） */
    private FlowGuideVO flowGuide;

    /** 数值对齐卡片（feature 增量：标准域装配；数值来自系统标准库，模型不得断言数值） */
    private List<AiValueAnchorVO> valueAnchors = new ArrayList<>();

    /** 替代建议条目：点击即以 {@code query} 再问一次 */
    @Data
    public static class Suggestion {

        /** 展示文案 */
        private String text;

        /** 点击后作为问题重发 */
        private String query;

        public Suggestion() {
        }

        public Suggestion(String text, String query) {
            this.text = text;
            this.query = query;
        }
    }
}
