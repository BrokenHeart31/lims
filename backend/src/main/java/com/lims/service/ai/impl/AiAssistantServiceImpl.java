package com.lims.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.PageResult;
import com.lims.common.ResultCode;
import com.lims.common.enums.AiDomain;
import com.lims.common.exception.BizException;
import com.lims.dto.AiChatDTO;
import com.lims.dto.AiChatContextDTO;
import com.lims.entity.AiConversation;
import com.lims.entity.AiMessage;
import com.lims.mapper.AiConversationMapper;
import com.lims.mapper.AiMessageMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.ai.AiAssistantService;
import com.lims.service.ai.AiProperties;
import com.lims.service.ai.BusinessContextReader;
import com.lims.service.ai.BusinessRuleAssembler;
import com.lims.service.ai.DomainGuard;
import com.lims.service.ai.GbRetriever;
import com.lims.service.ai.OllamaClient;
import com.lims.service.ai.OllamaHealthChecker;
import com.lims.service.ai.companion.ValueAnchorAssembler;
import com.lims.service.ai.flow.FlowGuideAssembler;
import com.lims.vo.AiAnswerVO;
import com.lims.vo.AiCitationVO;
import com.lims.vo.AiConversationVO;
import com.lims.vo.AiMessageVO;
import com.lims.vo.AiStatusVO;
import com.lims.vo.AiValueAnchorVO;
import com.lims.vo.FlowGuideVO;
import com.lims.vo.GbSearchHitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 助手编排实现（feature A，T03，设计 §6.1）。
 *
 * <p><b>编排主线</b>：护栏判域 → 业务域代码装配（不调模型）/ 标准域检索+投喂模型 / 越界拒答
 * → 落 {@code ai_message} 留痕（引用 + 拒答 + 耗时）。</p>
 *
 * <p><b>AI 旁路三红线</b>：① 只写 {@code ai_*}/{@code gb_*}，业务只读走 {@link BusinessContextReader}；
 * ② 无命中**不编造**（标准域 0 命中直接回「未找到」且不调模型）；③ Ollama 不可用 → 4201 降级，
 * 业务接口毫不受影响。</p>
 */
@Slf4j
@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    /** 拒答话术（同事口吻，设计 §4.1 / PRD T1）。 */
    private static final String REFUSAL = "这个问题超出本系统的业务范围，我主要负责检验业务、样品流程和标准查询"
            + "——要不要我帮你查一下相关标准？";

    /** 标准域无命中话术（诚实兜底，**绝不编造标准号**）。 */
    private static final String NO_CITATION = "我未在本地标准库找到与该问题相关的标准条款。"
            + "请补充更具体的检测项目/标准号（如「GB 2762 铅限量」），或联系管理员导入对应标准文件。";

    /** 引用片段截断长度。 */
    private static final int SNIPPET_MAX = 220;

    private final OllamaHealthChecker healthChecker;
    private final OllamaClient ollamaClient;
    private final DomainGuard domainGuard;
    private final BusinessRuleAssembler ruleAssembler;
    private final GbRetriever gbRetriever;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiProperties props;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor aiTaskExecutor;
    private final FlowGuideAssembler flowGuideAssembler;
    private final ValueAnchorAssembler valueAnchorAssembler;
    private final BusinessContextReader contextReader;

    public AiAssistantServiceImpl(OllamaHealthChecker healthChecker,
                                  OllamaClient ollamaClient,
                                  DomainGuard domainGuard,
                                  BusinessRuleAssembler ruleAssembler,
                                  GbRetriever gbRetriever,
                                  AiConversationMapper conversationMapper,
                                  AiMessageMapper messageMapper,
                                  AiProperties props,
                                  ObjectMapper objectMapper,
                                  @Qualifier("aiTaskExecutor") ThreadPoolTaskExecutor aiTaskExecutor,
                                  FlowGuideAssembler flowGuideAssembler,
                                  ValueAnchorAssembler valueAnchorAssembler,
                                  BusinessContextReader contextReader) {
        this.healthChecker = healthChecker;
        this.ollamaClient = ollamaClient;
        this.domainGuard = domainGuard;
        this.ruleAssembler = ruleAssembler;
        this.gbRetriever = gbRetriever;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.props = props;
        this.objectMapper = objectMapper;
        this.aiTaskExecutor = aiTaskExecutor;
        this.flowGuideAssembler = flowGuideAssembler;
        this.valueAnchorAssembler = valueAnchorAssembler;
        this.contextReader = contextReader;
    }

    // =====================================================================
    // A1 状态
    // =====================================================================

    @Override
    public AiStatusVO status() {
        return healthChecker.check();
    }

    // =====================================================================
    // A2 非流式
    // =====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAnswerVO chat(AiChatDTO dto) {
        String username = username();
        long start = System.currentTimeMillis();
        Prepared p = prepare(dto, username);

        String model = null;
        String answer;
        if (p.deterministicAnswer() != null) {
            answer = p.deterministicAnswer();
        } else {
            model = props.getModel();
            answer = ollamaClient.chat(buildMessages(p), OllamaClient.GenOptions.answer()).content();
        }
        long elapsed = System.currentTimeMillis() - start;
        AiMessage saved = saveAssistantMessage(p, answer, model, elapsed);
        return buildAnswerVO(p, saved.getId(), answer, model, elapsed);
    }

    // =====================================================================
    // A3 流式（SSE）
    // =====================================================================

    @Override
    public SseEmitter chatStream(AiChatDTO dto) {
        SseEmitter emitter = new SseEmitter((long) props.getTimeoutMs());
        // 请求线程上先取登录人（异步线程无 SecurityContext，必须在此捕获）
        String username = username();
        aiTaskExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                Prepared p = prepare(dto, username);
                // ① 先给引用，前端先渲染卡片
                send(emitter, "refs", refsPayload(p));

                String model = null;
                String answer;
                if (p.deterministicAnswer() != null) {
                    answer = p.deterministicAnswer();
                    send(emitter, "token", Map.of("t", answer));
                } else {
                    model = props.getModel();
                    StringBuilder acc = new StringBuilder();
                    ollamaClient.chatStream(buildMessages(p), OllamaClient.GenOptions.answer(), token -> {
                        acc.append(token);
                        send(emitter, "token", Map.of("t", token));
                    });
                    answer = acc.toString();
                }

                long elapsed = System.currentTimeMillis() - start;
                AiMessage saved = saveAssistantMessage(p, answer, model, elapsed);

                // ② done
                Map<String, Object> done = new LinkedHashMap<>();
                done.put("messageId", saved.getId());
                done.put("conversationId", p.conversation().getId());
                done.put("elapsedMs", elapsed);
                done.put("confidence", confidence(p));
                done.put("refused", p.refused());
                if (!p.suggestions().isEmpty()) {
                    done.put("suggestions", p.suggestions());
                }
                send(emitter, "done", done);
                emitter.complete();
            } catch (BizException e) {
                // 明确降级（如 4201 AI_OFFLINE / 4203 超时）：前端展示提示，业务不受影响
                send(emitter, "error", Map.of("code", e.getCode(), "msg", e.getMessage()));
                emitter.complete();
            } catch (Exception e) {
                log.error("[ai] 流式对话异常", e);
                send(emitter, "error", Map.of("code", ResultCode.ERROR.getCode(),
                        "msg", "AI 处理失败，请稍后重试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    // =====================================================================
    // A4 / A5 审计
    // =====================================================================

    @Override
    public PageResult<AiConversationVO> pageConversations(long current, long size) {
        Page<AiConversation> page = conversationMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<AiConversation>().orderByDesc(AiConversation::getId));
        return PageResult.of(page, this::toConversationVO);
    }

    @Override
    public List<AiMessageVO> messages(Long conversationId) {
        if (conversationId == null) {
            throw new BizException(400, "会话ID不能为空");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getSeq))
                .stream().map(this::toMessageVO).toList();
    }

    // =====================================================================
    // 编排核心
    // =====================================================================

    /** 一次提问的预处理结果（判域 + 检索 + 装配，供流式/非流式共用）。 */
    private record Prepared(
            AiConversation conversation,
            int userSeq,
            AiDomain domain,
            String guardReason,
            List<AiCitationVO> citations,
            String deterministicAnswer,
            boolean refused,
            List<AiAnswerVO.Suggestion> suggestions,
            FlowGuideVO flowGuide,
            List<AiValueAnchorVO> valueAnchors) {
    }

    private Prepared prepare(AiChatDTO dto, String username) {
        String question = dto.getQuestion().trim();
        AiChatContextDTO ctx = dto.getContext();
        var guard = domainGuard.classify(question, ctx);
        AiDomain domain = guard.domain();
        AiConversation conversation = resolveConversation(dto, username, question);
        int userSeq = nextSeq(conversation.getId());
        saveUserMessage(conversation, userSeq, question, domain, username);

        switch (domain) {
            case BUSINESS -> {
                var intent = ruleAssembler.detectIntent(question);
                String answer = ruleAssembler.answer(intent, question, ctx);
                // 流程引导：仅 NEXT_STEP / GUIDE / FLOW_OVERVIEW 装配结构化事实（不调模型）
                FlowGuideVO flowGuide = switch (intent) {
                    case NEXT_STEP, GUIDE, FLOW_OVERVIEW -> flowGuideAssembler.guide(
                            ctx == null ? null : ctx.getStatus(),
                            ctx == null ? null : ctx.getSampleNo(),
                            contextReader.readRolePermissions());
                    default -> null;
                };
                return new Prepared(conversation, userSeq, domain, guard.reason(),
                        List.of(), answer, false, List.of(), flowGuide, List.of());
            }
            case OTHER -> {
                return new Prepared(conversation, userSeq, domain, guard.reason(),
                        List.of(), REFUSAL, true, defaultSuggestions(), null, List.of());
            }
            default -> {
                // STANDARD：只查索引，绝不读文件
                // 数值对齐卡片（数值只来自系统标准库；模型不得断言数值）
                List<AiValueAnchorVO> anchors = valueAnchors(ctx);

                // ① 用户点名了标准号 → 先判「库内是否收录」（2026-09-18 修复）
                //    点名了未收录的标准却拿别的标准条款凑答案，是会误导检验人员的错误行为
                //    （实测：问「GB 2762 铅的限量」，旧实现返回 5 条 GB 2763 的无关条款）。
                //    这里走**确定性作答**（不调模型）——宁可直说没收录，也不给错误出处。
                List<String> mentioned = GbRetriever.mentionedStdNos(question);
                String stdNoFilter = ctx == null ? null : ctx.getStdNo();
                if (!mentioned.isEmpty()) {
                    List<String> indexedRaw = gbRetriever.indexedStdNos();
                    List<String> hitRaw = new ArrayList<>();
                    for (String m : mentioned) {
                        for (String raw : indexedRaw) {
                            if (GbRetriever.baseKey(raw).equals(m)) {
                                hitRaw.add(raw);
                                break;
                            }
                        }
                    }
                    if (hitRaw.isEmpty()) {
                        return new Prepared(conversation, userSeq, domain, guard.reason(),
                                List.of(), missingStandardAnswer(mentioned, indexedRaw),
                                false, List.of(), null, anchors);
                    }
                    // 点名了已收录标准 → 只在该标准内检索，避免跨标准串味
                    stdNoFilter = hitRaw.get(0);
                }

                List<AiCitationVO> citations = searchCitations(dto, question, stdNoFilter);
                if (citations.isEmpty()) {
                    // 无命中 → 不调模型（不编造），直接诚实兜底
                    return new Prepared(conversation, userSeq, domain, guard.reason(),
                            List.of(), NO_CITATION, false, List.of(), null, anchors);
                }
                return new Prepared(conversation, userSeq, domain, guard.reason(),
                        citations, null, false, List.of(), null, anchors);
            }
        }
    }

    /** 装配数值对齐卡片（有明确项目名时；jumpPath 依 base:lib:list 权限收敛）。 */
    private List<AiValueAnchorVO> valueAnchors(AiChatContextDTO ctx) {
        if (ctx == null || !StringUtils.hasText(ctx.getItemName())) {
            return List.of();
        }
        boolean canJump = contextReader.readRolePermissions().contains("base:lib:list");
        return valueAnchorAssembler
                .anchor(ctx.getItemName(), ctx.getStdNo(), ctx.getSampleNo(), canJump)
                .map(a -> List.<AiValueAnchorVO>of(a))
                .orElseGet(List::of);
    }

    /**
     * 「点名的标准未收录」的确定性作答（不调模型）。
     *
     * <p>为什么必须是确定性的：这条回答的语义是「无出处可得」，交给 4B 模型会被它编出一段
     * 看似合理的标准号与限值——**编造的出处比没有出处危险得多**。同时明确告知**已收录清单**，
     * 让用户能自己判断下一步该做什么（导入该标准 / 改用已收录标准提问）。</p>
     */
    private String missingStandardAnswer(List<String> missing, List<String> indexedRaw) {
        String list = indexedRaw.isEmpty() ? "（暂无）" : String.join("、", indexedRaw);
        return "关于 " + String.join("、", missing) + "：本系统标准知识库**当前未收录**该标准，"
                + "因此无法引用它的条款。为避免误导，这里不套用其他标准的条款来作答。\n"
                + "当前已收录：" + list + "。\n"
                + "如需按该标准判定，请把标准文件（PDF / TXT / HTML）放入 `ai/standards/inbox/` 后执行导入，"
                + "或由管理员在「AI 知识库」页面完成导入；导入完成后即可按其条款检索与引用。\n"
                + "另请留意：**判定结论以系统判定引擎为准**，本助手只提供条款引用与定位，不给出合格判定。";
    }

    private List<AiCitationVO> searchCitations(AiChatDTO dto, String question, String stdNoOverride) {
        String stdNo = stdNoOverride != null
                ? stdNoOverride
                : (dto.getContext() == null ? null : dto.getContext().getStdNo());
        List<GbSearchHitVO> hits = gbRetriever.search(question, props.getTopN(), stdNo);
        List<AiCitationVO> citations = new ArrayList<>(hits.size());
        for (GbSearchHitVO h : hits) {
            AiCitationVO c = new AiCitationVO();
            c.setStdNo(h.getStdNo());
            c.setClauseNo(h.getClauseNo());
            c.setClauseTitle(h.getClauseTitle());
            c.setSnippet(snippet(h.getSnippet()));
            c.setDocId(h.getDocId());
            c.setSourceFile(h.getSourceFile());
            c.setScore(h.getScore());
            c.setSourceType(h.getSourceType());
            c.setSourceTypeLabel(sourceTypeLabel(h.getSourceType()));
            c.setOcrDerived(h.isOcrDerived());
            citations.add(c);
        }
        return citations;
    }

    /** 来源类型中文名（文本版 / 扫描件OCR）。 */
    private String sourceTypeLabel(Integer sourceType) {
        if (sourceType == null) {
            return null;
        }
        return switch (sourceType) {
            case 1 -> "TXT";
            case 2 -> "HTML";
            case 3 -> "MD";
            case 4 -> "CSV";
            case 5 -> "扫描件OCR";
            default -> "未知";
        };
    }

    /** 组装投喂模型的消息：系统提示（含仅命中的条款）+ 历史 + 当前问题。 */
    private List<OllamaClient.ChatMessage> buildMessages(Prepared p) {
        List<OllamaClient.ChatMessage> messages = new ArrayList<>();
        messages.add(OllamaClient.ChatMessage.system(systemPrompt(p.citations())));
        // 历史（排除当前这轮用户消息，避免重复）
        List<AiMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, p.conversation().getId())
                .lt(AiMessage::getSeq, p.userSeq())
                .orderByDesc(AiMessage::getSeq)
                .last("LIMIT " + Math.max(0, props.getHistoryLimit())));
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessage m = history.get(i);
            String role = Integer.valueOf(AiMessage.ROLE_USER).equals(m.getRole()) ? "user" : "assistant";
            if (StringUtils.hasText(m.getContent())) {
                messages.add(new OllamaClient.ChatMessage(role, m.getContent()));
            }
        }
        // 当前问题
        messages.add(OllamaClient.ChatMessage.user(currentUserQuestion(p)));
        return messages;
    }

    /** 取当前轮用户消息正文（按 conversation + seq 定位）。 */
    private String currentUserQuestion(Prepared p) {
        AiMessage m = messageMapper.selectOne(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, p.conversation().getId())
                .eq(AiMessage::getSeq, p.userSeq())
                .last("LIMIT 1"));
        return m == null ? "" : m.getContent();
    }

    private String systemPrompt(List<AiCitationVO> citations) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是食品/农产品检验实验室(LIMS)的标准助手。\n")
                .append("【回答方式】\n")
                // 防「元话语外泄」：4B 级模型会把提示词里的规则当作内容复述出来
                // （实测曾输出「⚠️ 但请注意：…不得凭记忆或从条款片段中摘取数字…✅ 正确回答应为：…」），
                // 故显式要求只给最终答复、并点名禁止这些句式。
                .append("1) 只输出给用户的**最终答复**。不要复述这些规则，不要输出分析过程或自我检查，"
                        + "不要出现「根据要求」「正确回答应为」「但请注意」这类元话语。\n")
                .append("2) 只依据下面给出的【标准条款】回答，必须写明标准号与条款号。\n")
                // 区分「条款真的不相关」与「因不能写数值而不敢答」——否则 4B 模型会一律回「未覆盖」（实测踩到）
                .append("3) 只有当条款**主题**与问题无关（既没提到该项目、也没提到相关食品类别）时，"
                        + "才回答「标准条款未覆盖」；**不要**因为「不能写数值」就说未覆盖。\n")
                .append("【数值规则】\n")
                .append("4) 答复中**不要写出任何具体限值数字**（如「0.05 mg/kg」），即使条款片段里出现也一律不写。\n")
                .append("5) 涉及数值时，照下面这个**结构**作答（照结构写，但不要输出「模板」二字）：\n")
                .append("   依据 <标准号> <条款号>，<项目名> 的最大残留限量应符合表<表号>的规定；"
                        + "具体限量请以系统标准库为准（可查看「数值对齐」卡片）。\n")
                .append("【标准条款】\n");
        int budget = props.getMaxContextChars();
        int used = 0;
        for (AiCitationVO c : citations) {
            String block = "【" + c.getStdNo() + " " + (c.getClauseNo() == null ? "" : c.getClauseNo())
                    + "】" + (c.getSnippet() == null ? "" : c.getSnippet()) + "\n";
            if (used + block.length() > budget) {
                break;
            }
            sb.append(block);
            used += block.length();
        }
        return sb.toString();
    }

    // =====================================================================
    // 持久化
    // =====================================================================

    private AiConversation resolveConversation(AiChatDTO dto, String username, String question) {
        if (dto.getConversationId() != null) {
            AiConversation c = conversationMapper.selectById(dto.getConversationId());
            if (c == null) {
                throw new BizException(400, "会话不存在: id=" + dto.getConversationId());
            }
            return c;
        }
        AiConversation c = new AiConversation();
        c.setTitle(truncate(question, 50));
        c.setUserNo(username);
        c.setModel(props.getModel());
        // 审计人显式承接（异步线程无 SecurityContext）
        c.setCreatedBy(username);
        c.setUpdatedBy(username);
        conversationMapper.insert(c);
        return c;
    }

    private int nextSeq(Long conversationId) {
        Long n = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
        return (n == null ? 0 : n.intValue()) + 1;
    }

    private void saveUserMessage(AiConversation c, int seq, String question, AiDomain domain, String username) {
        AiMessage m = new AiMessage();
        m.setConversationId(c.getId());
        m.setSeq(seq);
        m.setRole(AiMessage.ROLE_USER);
        m.setContent(question);
        m.setDomain(domain.persistable() ? domain.getCode() : AiDomain.OTHER.getCode());
        m.setRefused(0);
        m.setRetrievedCount(0);
        m.setElapsedMs(0);
        m.setCreatedBy(username);
        m.setUpdatedBy(username);
        messageMapper.insert(m);
    }

    private AiMessage saveAssistantMessage(Prepared p, String answer, String model, long elapsedMs) {
        AiMessage m = new AiMessage();
        m.setConversationId(p.conversation().getId());
        m.setSeq(p.userSeq() + 1);
        m.setRole(AiMessage.ROLE_ASSISTANT);
        m.setContent(answer);
        m.setDomain(p.domain().persistable() ? p.domain().getCode() : AiDomain.OTHER.getCode());
        m.setRefused(p.refused() ? 1 : 0);
        m.setCitationsJson(toJson(p.citations()));
        m.setRetrievedCount(p.citations().size());
        m.setModel(model);
        m.setElapsedMs((int) Math.min(Integer.MAX_VALUE, elapsedMs));
        String username = p.conversation().getUserNo();
        m.setCreatedBy(username);
        m.setUpdatedBy(username);
        messageMapper.insert(m);
        return m;
    }

    // =====================================================================
    // VO 装配
    // =====================================================================

    private AiAnswerVO buildAnswerVO(Prepared p, Long messageId, String answer, String model, long elapsedMs) {
        AiAnswerVO vo = new AiAnswerVO();
        vo.setConversationId(p.conversation().getId());
        vo.setMessageId(messageId);
        vo.setAnswer(answer);
        vo.setRefused(p.refused());
        vo.setDomain(p.domain().getCode());
        vo.setConfidence(confidence(p));
        vo.setModel(model);
        vo.setElapsedMs(elapsedMs);
        vo.setCitations(p.citations());
        vo.setSuggestions(p.suggestions());
        vo.setFlowGuide(p.flowGuide());
        vo.setValueAnchors(p.valueAnchors());
        return vo;
    }

    private Map<String, Object> refsPayload(Prepared p) {
        Map<String, Object> refs = new LinkedHashMap<>();
        refs.put("citations", p.citations());
        refs.put("domain", p.domain().getCode());
        refs.put("refused", p.refused());
        refs.put("flowGuide", p.flowGuide());
        refs.put("valueAnchors", p.valueAnchors());
        return refs;
    }

    /** 置信度：业务域/拒答=high（确定性）；标准域按命中数。 */
    private String confidence(Prepared p) {
        if (p.domain() == AiDomain.BUSINESS || p.refused()) {
            return "high";
        }
        int n = p.citations().size();
        if (n >= 2) {
            return "high";
        }
        return n == 1 ? "medium" : "low";
    }

    private List<AiAnswerVO.Suggestion> defaultSuggestions() {
        List<AiAnswerVO.Suggestion> s = new ArrayList<>(3);
        s.add(new AiAnswerVO.Suggestion("查 GB 2762 铅限量", "GB 2762 铅限量是多少"));
        s.add(new AiAnswerVO.Suggestion("解释「检验中」状态", "样品状态 检验中 是什么意思"));
        s.add(new AiAnswerVO.Suggestion("我有哪些权限", "我有哪些权限"));
        return s;
    }

    private AiConversationVO toConversationVO(AiConversation c) {
        AiConversationVO vo = new AiConversationVO();
        vo.setId(c.getId());
        vo.setTitle(c.getTitle());
        vo.setUserNo(c.getUserNo());
        vo.setModel(c.getModel());
        vo.setCreatedAt(c.getCreatedAt());
        Long n = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, c.getId()));
        vo.setMessageCount(n == null ? 0 : n.intValue());
        return vo;
    }

    private AiMessageVO toMessageVO(AiMessage m) {
        AiMessageVO vo = new AiMessageVO();
        vo.setId(m.getId());
        vo.setConversationId(m.getConversationId());
        vo.setSeq(m.getSeq());
        vo.setRole(m.getRole());
        vo.setRoleLabel(m.getRoleLabel());
        vo.setContent(m.getContent());
        vo.setDomain(m.getDomain());
        vo.setRefused(m.getRefused());
        vo.setCitations(parseCitations(m.getCitationsJson()));
        vo.setRetrievedCount(m.getRetrievedCount());
        vo.setModel(m.getModel());
        vo.setElapsedMs(m.getElapsedMs());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    // =====================================================================
    // 工具
    // =====================================================================

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开/连接已关闭：忽略，不打断后续清理（emitter 由容器完成）
            log.debug("[ai] SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }

    private String snippet(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= SNIPPET_MAX ? t : t.substring(0, SNIPPET_MAX) + "…";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[ai] citations 序列化失败：{}", e.getMessage());
            return "[]";
        }
    }

    private List<AiCitationVO> parseCitations(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AiCitationVO.class));
        } catch (Exception e) {
            log.warn("[ai] citations 反序列化失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String username() {
        return SecurityUtils.getUsername().orElse("anonymous");
    }
}
