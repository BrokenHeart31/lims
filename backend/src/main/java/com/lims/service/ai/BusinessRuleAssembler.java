package com.lims.service.ai;

import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.dto.AiChatContextDTO;
import com.lims.service.ai.flow.BusinessFlowMap;
import com.lims.service.ai.flow.FlowGuideAssembler;
import com.lims.vo.FlowGuideVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务域答案的**确定性装配**（feature A，T3 / 增量 ai_flow_assistant，设计 §2.6 / §7）。
 *
 * <p><b>业务问题不调模型</b>：状态解释、下一步、权限解释、流程引导/总览的数据源都是**系统权威枚举**
 * （{@link SampleStatus} + {@link SampleStatusTransition} + {@link BusinessFlowMap} + 当前用户权限），
 * 由代码装配。这样「状态解释」「流程引导」天然**零幻觉**，且**离线也可用**（不依赖 Ollama）。</p>
 *
 * <p>事实层 / 措辞层分离：结构化事实字段由 {@link FlowGuideAssembler} 装配下发；
 * 本类的 {@code GUIDE}/{@code FLOW_OVERVIEW} 分支产出的是**代码装配**的 checklist 文案，不含模型生成。</p>
 */
@Component
@RequiredArgsConstructor
public class BusinessRuleAssembler {

    /** 业务意图。 */
    public enum Intent {
        /** 解释某状态含义 */
        STATUS_EXPLAIN,
        /** 当前状态下一步能做什么 */
        NEXT_STEP,
        /** 我有哪些权限 */
        PERMISSION_EXPLAIN,
        /** 「我要做 X / 怎么登记」——分步引导 checklist */
        GUIDE,
        /** 「完整链路 / 分几步 / 各由谁负责」——流程总览 */
        FLOW_OVERVIEW,
        /** 其它业务泛问 */
        GENERAL
    }

    private final BusinessContextReader contextReader;
    private final FlowGuideAssembler flowGuideAssembler;

    /** S10→S90 的「一句话含义 + 谁来做」说明（装配文案用，取自 AGENTS 7.2 口径）。 */
    private static final Map<SampleStatus, String> STATUS_DESC = new LinkedHashMap<>();

    static {
        STATUS_DESC.put(SampleStatus.S10, "采样单已导入，等待登记员「登记确认」。");
        STATUS_DESC.put(SampleStatus.S20, "登记已确认，等待任务管理员做「项目分解」（自动套用标准库后确认保存）。");
        STATUS_DESC.put(SampleStatus.S30, "项目分解已确认，等待「任务安排」（按方法资质指派检验员）。");
        STATUS_DESC.put(SampleStatus.S40, "任务已安排，等待检验员首次录入结果。");
        STATUS_DESC.put(SampleStatus.S50, "检验员正在录入/续录结果，全部录齐后提交。");
        STATUS_DESC.put(SampleStatus.S60, "结果已录齐，等待审核（通过→已审核；退回→回到检验中重录）。");
        STATUS_DESC.put(SampleStatus.S70, "审核已通过，等待签发。");
        STATUS_DESC.put(SampleStatus.S80, "已签发，等待生成报告。");
        STATUS_DESC.put(SampleStatus.S90, "报告已生成、数据已上报；如需纠错只能作废/召回并新增更正记录。");
    }

    /** 判别业务意图（关键词，零模型）。 */
    public Intent detectIntent(String question) {
        String q = question == null ? "" : question;
        // 流程总览优先于分步引导（「完整链路分几步」更像总览）
        if (containsAny(q, "完整链路", "分几步", "各由谁", "整个流程", "全流程", "业务流程", "链路是什么", "流程总览")) {
            return Intent.FLOW_OVERVIEW;
        }
        if (containsAny(q, "我要做", "我要登记", "怎么做", "怎么走", "怎么登记", "怎么录入", "怎么分解",
                "怎么安排", "怎么审核", "怎么签发", "怎么生成报告", "怎么回退", "走一遍", "带我",
                "教我", "如何操作", "怎么操作", "步骤是什么")) {
            return Intent.GUIDE;
        }
        if (containsAny(q, "下一步", "接下来", "怎么走", "接下来做什么", "还能做什么", "下一步做什么")) {
            return Intent.NEXT_STEP;
        }
        if (containsAny(q, "权限", "能做什么", "有什么权", "角色", "岗位", "我能做")) {
            return Intent.PERMISSION_EXPLAIN;
        }
        if (containsAny(q, "状态", "是什么意思", "啥意思", "解释", "含义")) {
            return Intent.STATUS_EXPLAIN;
        }
        return Intent.GENERAL;
    }

    /**
     * 装配确定性答案。
     *
     * @param intent   意图
     * @param question 原始问题（GENERAL 时回显引导）
     * @param ctx      上下文（样品号 / 状态 / 项目名）
     */
    public String answer(Intent intent, String question, AiChatContextDTO ctx) {
        return switch (intent) {
            case STATUS_EXPLAIN -> statusExplain(ctx);
            case NEXT_STEP -> nextStep(ctx);
            case PERMISSION_EXPLAIN -> permissionExplain();
            case GUIDE -> guide(ctx);
            case FLOW_OVERVIEW -> flowOverview(ctx);
            case GENERAL -> general(question);
        };
    }

    // =====================================================================
    // 意图实现
    // =====================================================================

    private String statusExplain(AiChatContextDTO ctx) {
        SampleStatus current = resolveStatus(ctx);
        if (current != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("样品当前处于「").append(current.getLabel())
                    .append("」（状态码 ").append(current.getCode()).append("）。")
                    .append(STATUS_DESC.getOrDefault(current, ""))
                    .append('\n');
            appendNext(sb, current);
            return sb.toString().trim();
        }
        StringBuilder sb = new StringBuilder("本系统样品状态按 S10→S90 顺序流转：\n");
        for (SampleStatus s : SampleStatus.values()) {
            sb.append("· ").append(s.getCode()).append(" ").append(s.getLabel())
                    .append("：").append(STATUS_DESC.getOrDefault(s, "")).append('\n');
        }
        sb.append("（想了解某个样品当前步骤，可在问题里带上样品编号。）");
        return sb.toString().trim();
    }

    private String nextStep(AiChatContextDTO ctx) {
        SampleStatus current = resolveStatus(ctx);
        if (current == null) {
            return "请告诉我样品编号（或从样品页面打开助手），我就能告诉你它当前处于哪一步、下一步该做什么。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("样品当前处于「").append(current.getLabel()).append("」。");
        BusinessFlowMap.StageFact fact = BusinessFlowMap.of(current);
        if (fact != null) {
            sb.append("下一步：").append(fact.nextAction()).append("（入口：")
                    .append(fact.entryPath()).append("；").append(fact.fieldHint()).append("）。");
        } else {
            appendNext(sb, current);
        }
        return sb.toString().trim();
    }

    /** 分步引导：以 FlowGuideAssembler 的事实层为主，输出该角色的 checklist 文案。 */
    private String guide(AiChatContextDTO ctx) {
        FlowGuideVO vo = flowGuideAssembler.guide(
                ctx == null ? null : ctx.getStatus(),
                ctx == null ? null : ctx.getSampleNo(),
                contextReader.readRolePermissions());
        return flowGuideAssembler.checklistText(vo);
    }

    /** 流程总览：从登记到出报告的完整链路（各步负责人）。 */
    private String flowOverview(AiChatContextDTO ctx) {
        List<BusinessFlowMap.StageFact> facts = BusinessFlowMap.all();
        StringBuilder sb = new StringBuilder("从采样到出报告，本系统样品流程共 ")
                .append(facts.size()).append(" 步（各步负责人见括号）：\n");
        int i = 1;
        for (BusinessFlowMap.StageFact f : facts) {
            sb.append(i++).append(". 【").append(f.stageLabel()).append("】")
                    .append(f.nextAction()).append("（").append(f.actorRole()).append("）\n");
        }
        SampleStatus current = resolveStatus(ctx);
        if (current != null) {
            sb.append("当前样品处于「").append(current.getLabel()).append("」，");
            BusinessFlowMap.StageFact f = BusinessFlowMap.of(current);
            if (f != null) {
                sb.append("下一步：").append(f.nextAction()).append("。");
            }
        } else {
            sb.append("（带上样品编号，我可以标出你当前在哪一步。）");
        }
        return sb.toString().trim();
    }

    private void appendNext(StringBuilder sb, SampleStatus current) {
        var forward = SampleStatusTransition.nextAllowed(current);
        var rollback = SampleStatusTransition.rollbackAllowed(current);
        if (forward.isEmpty()) {
            sb.append("这是终态，无需继续推进。");
        } else {
            sb.append("下一步：").append(forward.stream()
                    .map(SampleStatus::getLabel).collect(Collectors.joining(" / ")))
                    .append("。");
        }
        if (!rollback.isEmpty()) {
            sb.append("如需纠错，可回退至：").append(rollback.stream()
                            .map(SampleStatus::getLabel).collect(Collectors.joining(" / ")))
                    .append("（回退会在「流程回溯」登记并留档）。");
        }
    }

    private String permissionExplain() {
        List<String> perms = contextReader.readRolePermissions();
        if (perms.isEmpty()) {
            return "未读取到你的权限信息（可能尚未登录）。请在系统内查看左侧菜单了解可访问的功能。";
        }
        // 只展示「模块级」权限（resource:action 里去掉按钮细粒度，避免刷屏）：取 resource 段去重
        List<String> modules = new ArrayList<>(perms.stream()
                .map(p -> p.contains(":") ? p.substring(0, p.indexOf(':')) : p)
                .distinct()
                .sorted()
                .toList());
        return "你当前拥有的功能域权限：" + String.join("、", modules)
                + "。\n（细粒度按钮权限共 " + perms.size() + " 项，可在「系统管理」查看角色配置。）";
    }

    private String general(String question) {
        return "我可以帮你：① 解释样品状态与下一步（带上样品编号更准）；② 查检验标准/限量/检测方法；"
                + "③ 解释你有哪些功能权限；④ 走一遍某业务的操作步骤（如「我要登记新样品」）。\n你问的是：「"
                + (question == null ? "" : question.trim()) + "」——需要我从哪个方面帮你？";
    }

    /** 解析当前状态：优先取上下文里的 code，其次按样品号查库（只读）。 */
    private SampleStatus resolveStatus(AiChatContextDTO ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getStatus() != null) {
            return SampleStatus.ofNullable(ctx.getStatus());
        }
        if (StringUtils.hasText(ctx.getSampleNo())) {
            return contextReader.readStatus(ctx.getSampleNo())
                    .map(v -> SampleStatus.ofNullable(v.status()))
                    .orElse(null);
        }
        return null;
    }

    private boolean containsAny(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
