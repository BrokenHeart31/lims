package com.lims.service.ai.flow;

import com.lims.common.enums.SampleStatus;
import com.lims.vo.FlowGuideVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程引导确定性测试（feature 增量 ai_flow_assistant，T02）。
 *
 * <p>事实层 100% 由代码装配（不调模型）→ 覆盖 {SampleStatus} 全状态、权限收敛、可重复一致性（G3）。</p>
 */
class FlowGuideDeterministicTest {

    private final FlowGuideAssembler assembler = new FlowGuideAssembler();

    @Test
    @DisplayName("BusinessFlowMap 覆盖 S10→S90 全状态（无遗漏）")
    void flowMapCoversAllStatuses() {
        for (SampleStatus s : SampleStatus.values()) {
            assertNotNull(BusinessFlowMap.of(s), "缺少状态事实：" + s);
        }
        assertEquals(SampleStatus.values().length, BusinessFlowMap.all().size());
    }

    @Test
    @DisplayName("前进/终态：S50 的下一阶段为 S60；S90 无下一阶段")
    void nextStageResolves() {
        assertEquals(SampleStatus.S60, BusinessFlowMap.next(SampleStatus.S50).orElseThrow().status());
        assertTrue(BusinessFlowMap.next(SampleStatus.S90).isEmpty());
    }

    @Test
    @DisplayName("当前步（S40）标 isCurrent，下一步动作=录入检验结果；越权步不给跳转")
    void currentStepAndPermissionGating() {
        // 检验员只有 result:entry
        FlowGuideVO vo = assembler.guide(40, "JK(2023)-SA-001", List.of("result:entry"));
        assertEquals(40, vo.getCurrentStatus());
        assertNotNull(vo.getNextStep());
        assertEquals(40, vo.getNextStep().getStatus());
        assertEquals("录入检验结果", vo.getNextStep().getNextAction());
        assertTrue(vo.getNextStep().isHasPermission());
        assertTrue(vo.getNextStep().isCurrent());
        // 越权步（审核 report:audit）不给跳转
        FlowGuideVO.Step audit = vo.getSteps().stream()
                .filter(s -> "report:audit".equals(s.getRequiredPermission())).findFirst().orElseThrow();
        assertFalse(audit.isHasPermission());
        // 步骤数 = 状态数（9）
        assertEquals(SampleStatus.values().length, vo.getSteps().size());
    }

    @Test
    @DisplayName("总览（无状态）：nextStep=null，stageIndex=0，步骤仍全量")
    void overviewWithoutStatus() {
        FlowGuideVO vo = assembler.guide(null, null, List.of());
        assertNull(vo.getNextStep());
        assertEquals(0, vo.getStageIndex());
        assertEquals(SampleStatus.values().length, vo.getSteps().size());
    }

    @Test
    @DisplayName("★G3：重复 10 次事实字段 100% 一致（模型不参与事实）")
    void factsAreStableAcrossRepeats() {
        String first = signature(assembler.guide(60, "S1", List.of("report:audit")));
        for (int i = 0; i < 10; i++) {
            assertEquals(first, signature(assembler.guide(60, "S1", List.of("report:audit"))));
        }
    }

    private String signature(FlowGuideVO vo) {
        StringBuilder sb = new StringBuilder();
        sb.append(vo.getCurrentStatus()).append('|').append(vo.getStageIndex()).append('|');
        if (vo.getNextStep() != null) {
            sb.append(vo.getNextStep().getNextAction()).append('|')
                    .append(vo.getNextStep().getEntryPath()).append('|')
                    .append(vo.getNextStep().getRequiredPermission());
        }
        for (FlowGuideVO.Step s : vo.getSteps()) {
            sb.append('#').append(s.getStatus()).append(':').append(s.getNextAction())
                    .append(':').append(s.getEntryPath()).append(':').append(s.isHasPermission());
        }
        return sb.toString();
    }
}
