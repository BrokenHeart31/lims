package com.lims.service.ai.flow;

import com.lims.common.enums.SampleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.lims.vo.FlowGuideVO;

/**
 * 流程引导装配器（feature 增量 ai_flow_assistant，T02，设计 §2.6 / §2.7 / §5.1）。
 *
 * <p><b>事实层（100% 由代码装配，不调模型）</b>：以 {@link BusinessFlowMap} 为唯一权威，
 * 把「当前状态 → 下一步动作 / 入口 / 所需权限 / 是否具备 / 字段指引」装配成 {@link FlowGuideVO}。</p>
 *
 * <p><b>权限收敛（TE）</b>：逐步计算 {@code hasPermission}；不具备的步骤**不给跳转**，
 * 前端灰显并提示「需 X 权限，联系 R100/R2 开通」，**不诱导越权**。</p>
 *
 * <p>本类只读，无任何写方法、无 Mapper 依赖。</p>
 */
@Component
@RequiredArgsConstructor
public class FlowGuideAssembler {

    /**
     * 装配流程引导。
     *
     * @param statusCode  当前样品状态 code（可空：空则返回总览，不标当前步）
     * @param sampleNo    样品编号（可空）
     * @param permissions 当前登录用户权限标识清单（只读，来自认证上下文）
     */
    public FlowGuideVO guide(Integer statusCode, String sampleNo, List<String> permissions) {
        Set<String> perms = permissions == null ? Set.of() : new HashSet<>(permissions);
        SampleStatus current = SampleStatus.ofNullable(statusCode);

        FlowGuideVO vo = new FlowGuideVO();
        vo.setSampleNo(sampleNo);
        vo.setCurrentStatus(statusCode);
        vo.setCurrentStatusLabel(current == null ? null : current.getLabel());

        List<BusinessFlowMap.StageFact> facts = BusinessFlowMap.all();
        List<FlowGuideVO.Step> steps = new ArrayList<>(facts.size());
        FlowGuideVO.Step currentStep = null;
        int stageIndex = 0;

        for (int i = 0; i < facts.size(); i++) {
            BusinessFlowMap.StageFact f = facts.get(i);
            FlowGuideVO.Step step = toStep(f, perms);
            boolean isCurrent = current != null && f.status() == current;
            step.setCurrent(isCurrent);
            if (isCurrent) {
                stageIndex = i + 1;
                currentStep = step;
            }
            steps.add(step);
        }

        vo.setSteps(steps);
        vo.setStageIndex(stageIndex);
        // 下一步 = 当前状态对应的动作事实（「你现在处于 Sxx，下一步做 nextAction」）
        vo.setNextStep(currentStep);
        return vo;
    }

    /**
     * 装配「该角色的分步 checklist」纯文本（对话式引导用；事实字段仍以结构化 VO 为准）。
     *
     * <p>越权步骤**不列入口**，只标注所需权限与「找谁开」——与 TE 一致。</p>
     */
    public String checklistText(FlowGuideVO vo) {
        if (vo == null || vo.getSteps().isEmpty()) {
            return "系统暂未提供流程引导。";
        }
        StringBuilder sb = new StringBuilder("按当前角色，完整链路分以下步骤（每步标注入口与权限）：\n");
        int n = 1;
        for (FlowGuideVO.Step s : vo.getSteps()) {
            sb.append(n++).append(". 【").append(s.getStageLabel()).append("】").append(s.getNextAction());
            if (s.isHasPermission()) {
                sb.append("  —— 入口：").append(s.getEntryPath());
            } else {
                sb.append("  —— 需 ").append(s.getRequiredPermission())
                        .append(" 权限，当前角色不具备；请联系 ").append(s.getActorRole()).append(" 开通");
            }
            sb.append('\n');
        }
        if (vo.getNextStep() != null) {
            sb.append("你现在应做：").append(vo.getNextStep().getNextAction())
                    .append("（").append(vo.getNextStep().getFieldHint()).append("）");
        }
        return sb.toString().trim();
    }

    private FlowGuideVO.Step toStep(BusinessFlowMap.StageFact f, Set<String> perms) {
        FlowGuideVO.Step step = new FlowGuideVO.Step();
        step.setStatus(f.status().getCode());
        step.setStageLabel(f.stageLabel());
        step.setNextAction(f.nextAction());
        step.setEntryPath(f.entryPath());
        step.setRequiredPermission(f.requiredPermission());
        step.setHasPermission(perms.contains(f.requiredPermission()));
        step.setFieldHint(f.fieldHint());
        step.setActorRole(f.actorRole());
        step.setCurrent(false);
        return step;
    }
}
