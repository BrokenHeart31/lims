package com.lims.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.enums.AiDomain;
import com.lims.dto.AiChatContextDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 领域护栏（feature A，PRD T1 / A-03）。
 *
 * <p><b>只答本项目相关问题</b>：越界 **100% 拒答**，且拒答话术是「同事口吻 + 2~3 条替代建议」，
 * 不是系统报错（话术装配在 {@code AiAssistantServiceImpl}，本类只负责**判定 + 给理由**，
 * 理由写进 {@code ai_message.domain} 供事后复盘护栏准确率 R5）。</p>
 *
 * <p><b>判定顺序（本地规则优先，可审计）</b>：</p>
 * <ol>
 *   <li>命中闲聊/越界黑名单 → {@link AiDomain#OTHER}（强信号，先判）；</li>
 *   <li>只命中业务词 → BUSINESS；只命中标准词 → STANDARD；</li>
 *   <li>两者都命中：问题里出现标准号 → STANDARD；否则有样品上下文 → BUSINESS；再否则 BUSINESS；</li>
 *   <li>都不命中 → 交模型分类（temperature=0，严格 JSON）；模型不可用则默认 STANDARD
 *       （助手的主职是查标准；若此时离线，上层会据域返回 4201——这正是「判定不了又无模型」的正确降级）。</li>
 * </ol>
 *
 * <p>词表 = 内置默认 + {@code lims.ai.guard-lexicon.*} 增量（配置为**追加**，避免漏配把功能拒掉）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainGuard {

    /** 护栏判定结果：域 + 理由（理由用于审计，不展示给终端用户）。 */
    public record GuardResult(AiDomain domain, String reason) {
    }

    private static final List<String> DEFAULT_BUSINESS = List.of(
            "状态", "流程", "下一步", "怎么走", "怎么办", "权限", "角色", "岗位", "待办", "任务",
            "样品", "登记", "确认", "分解", "安排", "指派", "改派", "录入", "提交", "判定", "审核",
            "退回", "签发", "报告", "回退", "恢复", "作废", "召回", "能做什么", "允许", "为什么",
            "卡在", "谁");

    private static final List<String> DEFAULT_STANDARD = List.of(
            "gb", "ny", "sn", "hj", "sb", "标准", "限量", "检出", "最低检出", "判定依据", "依据",
            "方法", "条款", "指标", "允许量", "残留", "不得检出", "不得使用", "mg/kg", "μg/kg",
            "ug/kg", "mg/l", "%含量", "国标", "行标", "附录");

    private static final List<String> DEFAULT_CHITCHAT = List.of(
            "天气", "股票", "基金", "彩票", "汇率", "星座", "恋爱", "旅游", "电影", "音乐", "游戏",
            "笑话", "讲故事", "唱歌", "写代码", "编程", "写个脚本", "翻译", "新闻", "政治", "历史",
            "数学题", "帮我写", "你是谁", "你叫什么", "你会什么", "你觉得", "闲聊");

    /** 问题中出现标准号（GB 2762 / NY/T …）→ 强标准信号。 */
    private static final Pattern STD_NO_IN_QUESTION = Pattern.compile(
            "(?i)(GB|NY|SN|SB|HJ|DB)\\s*/?\\s*[TZ]?\\s*[0-9]{2,}");

    private static final String CLASSIFY_SYSTEM = """
            你是实验室信息管理系统(LIMS)的领域分类器。只输出 JSON，不要任何多余文字：
            {"domain":"business|standard|other","reason":"不超过20字的理由"}
            - business：本系统的业务流程、样品状态含义、操作步骤、用户权限。
            - standard：食品/农产品检验的标准、限量、检出限、检测方法、标准条款。
            - other：以上都不是（闲聊、常识、编程、其它行业等）。
            """;

    private final AiProperties props;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    /**
     * 判定问题所属领域。
     *
     * @param question 用户问题
     * @param ctx      可选上下文（标准号/样品号会影响歧义时的取舍）
     */
    public GuardResult classify(String question, AiChatContextDTO ctx) {
        String q = normalize(question);
        if (q.isEmpty()) {
            return new GuardResult(AiDomain.OTHER, "空问题");
        }
        // ① 越界黑名单优先（强信号）
        String chitchat = firstHit(q, mergeOrDefault("chitchat", DEFAULT_CHITCHAT));
        if (chitchat != null) {
            return new GuardResult(AiDomain.OTHER, "命中越界词：" + chitchat);
        }
        boolean biz = firstHit(q, mergeOrDefault("business", DEFAULT_BUSINESS)) != null;
        boolean std = firstHit(q, mergeOrDefault("standard", DEFAULT_STANDARD)) != null
                || STD_NO_IN_QUESTION.matcher(question).find();

        if (biz && !std) {
            return new GuardResult(AiDomain.BUSINESS, "命中业务词");
        }
        if (std && !biz) {
            return new GuardResult(AiDomain.STANDARD, "命中标准词");
        }
        if (biz) {
            // 歧义：问题含标准号 → 标准域；否则有样品上下文 → 业务域；再否则业务域（更安全）
            if (STD_NO_IN_QUESTION.matcher(question).find()) {
                return new GuardResult(AiDomain.STANDARD, "业务+标准词，含标准号 → 标准");
            }
            if (ctx != null && StringUtils.hasText(ctx.getSampleNo())) {
                return new GuardResult(AiDomain.BUSINESS, "业务+标准词，含样品上下文 → 业务");
            }
            return new GuardResult(AiDomain.BUSINESS, "业务+标准词 → 业务");
        }
        // ② 规则无法判定 → 交模型（温度 0，严格 JSON）
        return modelClassify(question);
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private GuardResult modelClassify(String question) {
        try {
            String raw = ollamaClient.classifyJson(CLASSIFY_SYSTEM, question);
            JsonNode root = objectMapper.readTree(raw);
            AiDomain domain = AiDomain.ofNullable(root.path("domain").asText(null));
            String reason = root.path("reason").asText("模型判定");
            if (domain != null && domain.persistable()) {
                return new GuardResult(domain, "模型判定：" + reason);
            }
            return new GuardResult(AiDomain.STANDARD, "模型判定无效，默认标准域");
        } catch (Exception e) {
            // 模型不可用/解析失败 —— 不抛给用户，默认标准域（上层据域决定是否 4201 降级）
            log.debug("[ai] 护栏模型分类不可用：{}", e.getMessage());
            return new GuardResult(AiDomain.STANDARD, "规则未判定且模型不可用，默认标准域");
        }
    }

    private List<String> mergeOrDefault(String key, List<String> defaults) {
        List<String> extra = props.getGuardLexicon() == null ? null : props.getGuardLexicon().get(key);
        if (extra == null || extra.isEmpty()) {
            return defaults;
        }
        List<String> merged = new ArrayList<>(defaults);
        merged.addAll(extra);
        return merged;
    }

    private String firstHit(String q, List<String> words) {
        for (String w : words) {
            if (w == null || w.isBlank()) {
                continue;
            }
            if (q.contains(w.toLowerCase())) {
                return w;
            }
        }
        return null;
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }
}
