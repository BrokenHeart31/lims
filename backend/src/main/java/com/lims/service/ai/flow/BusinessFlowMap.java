package com.lims.service.ai.flow;

import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 业务全流程引导的**唯一权威映射**（feature 增量 ai_flow_assistant，T02，设计 §2.6 / §7）。
 *
 * <p><b>状态机 S10→S90 是唯一权威</b>（{@link SampleStatus} + {@link SampleStatusTransition}）；
 * 本类把每个状态映射到 5 个确定性事实字段：{@code { 下一步动作, 入口路由, 所需权限, 负责人, 字段指引 }}。</p>
 *
 * <p><b>任何新增/变更状态或入口，必须先改本类 + 单测</b>（对齐「增删状态的唯一入口」约定，
 * 与 {@link SampleStatusTransition} 并列，设计 §7）。</p>
 *
 * <p>本类是**纯静态数据 + 纯函数**，不引任何 Mapper、不调模型、不做 IO——
 * 事实层 100% 由代码产出（TF）。</p>
 */
public final class BusinessFlowMap {

    /** 一个流程阶段的确定性事实。 */
    public record StageFact(
            SampleStatus status,
            String stageLabel,
            String nextAction,
            String entryPath,
            String requiredPermission,
            String actorRole,
            String fieldHint) {
    }

    /** 状态 → 事实（EnumMap O(1)）。 */
    private static final Map<SampleStatus, StageFact> FACTS = new EnumMap<>(SampleStatus.class);

    /**
     * 逆向：审核退回（S60→S50）的事实。
     *
     * <p>不是「推进」而是「否定」，独立于 {@link StageFact} 主线（对应 {@link SampleStatusTransition#RETURN}）。</p>
     */
    public static final StageFact RETURN_FACT = new StageFact(
            SampleStatus.S60, "检验完成", "退回检验员重录",
            "/report/audit", "report:audit", "R100",
            "打开报告 →「退回」→ 样品回 S50 并通知检验员");

    /**
     * 逆向：报告作废 / 召回（S80/S90 治理动作，不改 status）的事实。
     */
    public static final StageFact VOID_FACT = new StageFact(
            SampleStatus.S90, "已出报告", "作废 / 召回",
            "/report/generate", "report:void", "R2/R100",
            "报告生成页 →「作废/召回」（强理由 + 二次确认；不改 status）");

    static {
        FACTS.put(SampleStatus.S10, new StageFact(SampleStatus.S10, "已登记", "登记确认",
                "/sample", "sample:confirm", "R1",
                "选中样品 → 点「登记确认」；如需改信息先「编辑」"));
        FACTS.put(SampleStatus.S20, new StageFact(SampleStatus.S20, "登记确认", "项目分解",
                "/item/decompose", "item:decompose", "R2",
                "打开样品 →「套库预览」→ 调整项次/增删 → 「确认保存」"));
        FACTS.put(SampleStatus.S30, new StageFact(SampleStatus.S30, "已分解", "任务安排",
                "/assign/index", "assign:confirm", "R2",
                "「自动分配」→ 必要时「人工改派」→ 「确认安排」"));
        FACTS.put(SampleStatus.S40, new StageFact(SampleStatus.S40, "已安排", "录入检验结果",
                "/result/entry", "result:entry", "R3",
                "打开样品 → 逐项填「检验结果」→ 「保存录入」（首次保存 S40→S50）"));
        FACTS.put(SampleStatus.S50, new StageFact(SampleStatus.S50, "检验中", "继续录入 / 提交",
                "/result/entry", "result:entry", "R3",
                "录齐全部项 → 「提交」（S50→S60）"));
        FACTS.put(SampleStatus.S60, new StageFact(SampleStatus.S60, "检验完成", "审核",
                "/report/audit", "report:audit", "R100",
                "打开报告 → 「审核通过」（S60→S70）或「退回」（回 S50）"));
        FACTS.put(SampleStatus.S70, new StageFact(SampleStatus.S70, "已审核", "签发",
                "/report/audit", "report:sign", "R100",
                "打开 → 「签发」（S70→S80）"));
        FACTS.put(SampleStatus.S80, new StageFact(SampleStatus.S80, "已签发", "生成报告",
                "/report/generate", "report:generate", "R100",
                "「生成报告」（S80→S90）→ 「打印」"));
        FACTS.put(SampleStatus.S90, new StageFact(SampleStatus.S90, "已出报告", "上报导出 / 归档",
                "/export/province", "export:province", "R100",
                "「导出省平台 Excel」；纠错只能走「作废/召回」"));
    }

    private BusinessFlowMap() {
    }

    /** 按状态取阶段事实；未知状态返回 {@code null}。 */
    public static StageFact of(SampleStatus status) {
        return status == null ? null : FACTS.get(status);
    }

    /** 全部阶段事实（按 S10→S90 顺序，只读）。 */
    public static List<StageFact> all() {
        List<StageFact> list = new ArrayList<>(FACTS.size());
        for (SampleStatus s : SampleStatus.values()) {
            StageFact f = FACTS.get(s);
            if (f != null) {
                list.add(f);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 正向流转的下一阶段事实（依据 {@link SampleStatusTransition#nextAllowed}）。
     *
     * @return 无正向下一状态（终态）时返回 {@link Optional#empty()}
     */
    public static Optional<StageFact> next(SampleStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return SampleStatusTransition.nextAllowed(status).stream()
                .filter(s -> s != status) // 排除自环（S50→S50 续录）
                .findFirst()
                .map(FACTS::get);
    }
}
