package com.lims.common.enums;

import com.lims.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回退边策略单元测试（feature B，设计 §5.1）。
 *
 * <p>本类是回退边「分组 / 权限 / 二次确认 / 失效范围 / 拒绝原因」的**唯一权威**，
 * 任何新增回退边都必须同时改本类与 {@link SampleStatusTransition#ROLLBACK} 白名单。
 * 用单测把这张「边 → 策略」映射固化下来，防止后续被某次看似无害的调整打破
 * （例如把 S70→S60 误标为常规，会绕过二次确认这一合规红线）。</p>
 */
class RollbackEdgePolicyTest {

    @Test
    @DisplayName("分组：5 条常规边 + 1 条敏感边；被拒边与跨级边不属于任何分组")
    void groupOf_classification() {
        assertAll(
                () -> assertEquals(RollbackGroup.NORMAL, RollbackEdgePolicy.groupOf(SampleStatus.S20, SampleStatus.S10)),
                () -> assertEquals(RollbackGroup.NORMAL, RollbackEdgePolicy.groupOf(SampleStatus.S30, SampleStatus.S20)),
                () -> assertEquals(RollbackGroup.NORMAL, RollbackEdgePolicy.groupOf(SampleStatus.S40, SampleStatus.S30)),
                () -> assertEquals(RollbackGroup.NORMAL, RollbackEdgePolicy.groupOf(SampleStatus.S50, SampleStatus.S40)),
                () -> assertEquals(RollbackGroup.NORMAL, RollbackEdgePolicy.groupOf(SampleStatus.S60, SampleStatus.S50)),
                // 唯一敏感边：撤销审核需更高权限 + 二次确认
                () -> assertEquals(RollbackGroup.SENSITIVE, RollbackEdgePolicy.groupOf(SampleStatus.S70, SampleStatus.S60)),
                // 被拒边（改走作废/召回）
                () -> assertNull(RollbackEdgePolicy.groupOf(SampleStatus.S80, SampleStatus.S70)),
                () -> assertNull(RollbackEdgePolicy.groupOf(SampleStatus.S90, SampleStatus.S80)),
                // 跨级边（不属任何合法回退边）
                () -> assertNull(RollbackEdgePolicy.groupOf(SampleStatus.S60, SampleStatus.S40)),
                () -> assertNull(RollbackEdgePolicy.groupOf(null, SampleStatus.S50)),
                () -> assertNull(RollbackEdgePolicy.groupOf(SampleStatus.S50, null))
        );
    }

    @Test
    @DisplayName("权限：敏感边要 rollback:sensitive，其余（含被拒边）rollback:execute")
    void requiredPermission_mapping() {
        assertAll(
                () -> assertEquals(RollbackEdgePolicy.PERM_SENSITIVE,
                        RollbackEdgePolicy.requiredPermission(SampleStatus.S70, SampleStatus.S60)),
                () -> assertEquals(RollbackEdgePolicy.PERM_EXECUTE,
                        RollbackEdgePolicy.requiredPermission(SampleStatus.S60, SampleStatus.S50)),
                () -> assertEquals("rollback:sensitive", RollbackEdgePolicy.PERM_SENSITIVE),
                () -> assertEquals("rollback:execute", RollbackEdgePolicy.PERM_EXECUTE)
        );
    }

    @Test
    @DisplayName("二次确认：仅敏感边需要")
    void needSecondConfirm_onlySensitive() {
        assertAll(
                () -> assertTrue(RollbackEdgePolicy.needSecondConfirm(SampleStatus.S70, SampleStatus.S60)),
                () -> assertFalse(RollbackEdgePolicy.needSecondConfirm(SampleStatus.S60, SampleStatus.S50)),
                () -> assertFalse(RollbackEdgePolicy.needSecondConfirm(SampleStatus.S80, SampleStatus.S70))
        );
    }

    @Test
    @DisplayName("失效范围：每条回退边对应的下游处置范围（Pit 1：S30→S20 连带失效结果）")
    void invalidationScope_mapping() {
        assertAll(
                // 退回已登记：尚未分解，无下游数据
                () -> assertEquals(Set.of(ArchiveTarget.NONE),
                        RollbackEdgePolicy.invalidationScope(SampleStatus.S20, SampleStatus.S10)),
                // 撤销分解：明细失效 + 连带失效引用明细的结果（避免孤儿引用）
                () -> assertEquals(Set.of(ArchiveTarget.ITEM, ArchiveTarget.RESULT),
                        RollbackEdgePolicy.invalidationScope(SampleStatus.S30, SampleStatus.S20)),
                // 取消安排：只清空指派字段，明细保留
                () -> assertEquals(Set.of(ArchiveTarget.ASSIGN_FIELDS),
                        RollbackEdgePolicy.invalidationScope(SampleStatus.S40, SampleStatus.S30)),
                // 撤销首次录入：结果失效
                () -> assertEquals(Set.of(ArchiveTarget.RESULT),
                        RollbackEdgePolicy.invalidationScope(SampleStatus.S50, SampleStatus.S40)),
                // 退回录制：仅状态回退，结果保留
                () -> assertEquals(Set.of(ArchiveTarget.NONE),
                        RollbackEdgePolicy.invalidationScope(SampleStatus.S60, SampleStatus.S50)),
                // 撤销审核：清空当前有效审核字段（历史仍在 sample_audit_log）
                () -> assertEquals(Set.of(ArchiveTarget.AUDIT_FIELDS),
                        RollbackEdgePolicy.invalidationScope(SampleStatus.S70, SampleStatus.S60))
        );
    }

    @Test
    @DisplayName("★拒绝原因：S80/S90 出发一律 4102/4103；不可达（同级/逆向上行）4101；**跨级已允许**")
    void rejectReason_codes() {
        assertAll(
                () -> assertEquals(ResultCode.ROLLBACK_SIGNED.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S80, SampleStatus.S70).orElseThrow().code()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S80, SampleStatus.S70)
                        .orElseThrow().msg().contains("作废")),
                // S80 出发到任何目标都是 4102（已签发 → 只能作废/召回，不是「边不合法」）
                () -> assertEquals(ResultCode.ROLLBACK_SIGNED.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S80, SampleStatus.S10).orElseThrow().code()),
                () -> assertEquals(ResultCode.ROLLBACK_REPORTED.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S90, SampleStatus.S80).orElseThrow().code()),
                () -> assertEquals(ResultCode.ROLLBACK_REPORTED.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S90, SampleStatus.S10).orElseThrow().code()),
                // 2026-09-30 改造：跨级**不再是**拒绝理由（由链式逐级执行）
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S60, SampleStatus.S40).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S40, SampleStatus.S10).isEmpty()),
                // 同级 / 逆向上行 / null → 4101
                () -> assertEquals(ResultCode.ROLLBACK_ILLEGAL.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S50, SampleStatus.S50).orElseThrow().code()),
                () -> assertEquals(ResultCode.ROLLBACK_ILLEGAL.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S30, SampleStatus.S40).orElseThrow().code()),
                () -> assertEquals(ResultCode.ROLLBACK_ILLEGAL.getCode(),
                        RollbackEdgePolicy.rejectReason(null, SampleStatus.S50).orElseThrow().code()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S60, SampleStatus.S50).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S70, SampleStatus.S60).isEmpty()),
                // 业务码段登记（api-spec 0.2：回退域 4100–4199）
                () -> assertEquals(4101, ResultCode.ROLLBACK_ILLEGAL.getCode()),
                () -> assertEquals(4102, ResultCode.ROLLBACK_SIGNED.getCode()),
                () -> assertEquals(4103, ResultCode.ROLLBACK_REPORTED.getCode())
        );
    }

    // =========================================================================
    // 跨级链（2026-09-30 新增能力）
    // =========================================================================

    @Test
    @DisplayName("★链：跨级由既有逐级边组合而成，不给状态机新增任何直接边")
    void chain_composedFromExistingEdges() {
        assertAll(
                () -> assertEquals(List.of(SampleStatus.S30),
                        RollbackEdgePolicy.chain(SampleStatus.S40, SampleStatus.S30)),
                () -> assertEquals(List.of(SampleStatus.S30, SampleStatus.S20),
                        RollbackEdgePolicy.chain(SampleStatus.S40, SampleStatus.S20)),
                () -> assertEquals(List.of(SampleStatus.S30, SampleStatus.S20, SampleStatus.S10),
                        RollbackEdgePolicy.chain(SampleStatus.S40, SampleStatus.S10)),
                // S70 起点的 3 级链：链上含敏感级 S70→S60
                () -> assertEquals(List.of(SampleStatus.S60, SampleStatus.S50, SampleStatus.S40),
                        RollbackEdgePolicy.chain(SampleStatus.S70, SampleStatus.S40)),
                // 同级 / 逆向上行 / 起点为 S80 / S90 / null → 空链
                () -> assertTrue(RollbackEdgePolicy.chain(SampleStatus.S50, SampleStatus.S50).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.chain(SampleStatus.S30, SampleStatus.S40).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.chain(SampleStatus.S80, SampleStatus.S10).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.chain(SampleStatus.S90, SampleStatus.S10).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.chain(null, SampleStatus.S10).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.chain(SampleStatus.S40, null).isEmpty()),
                // 链的每一级都必须是既有白名单边（**「不新增直接边」的可执行断言**）
                () -> assertTrue(everyStepIsWhitelistedEdge(SampleStatus.S70, SampleStatus.S10))
        );
    }

    /** 逐级校验：链上每一跳都能被 {@code SampleStatusTransition.ROLLBACK} 白名单单步放行 */
    private static boolean everyStepIsWhitelistedEdge(SampleStatus from, SampleStatus to) {
        SampleStatus prev = from;
        for (SampleStatus step : RollbackEdgePolicy.chain(from, to)) {
            if (!SampleStatusTransition.canRollback(prev, step)) {
                return false;
            }
            prev = step;
        }
        return prev == to;
    }

    @Test
    @DisplayName("★可达目标步：S70 可退 6 步、S20 只能退 1 步、S80/S90/S10 无目标")
    void reachableTargets_enumeration() {
        assertAll(
                () -> assertEquals(6, RollbackEdgePolicy.reachableTargets(SampleStatus.S70).size()),
                () -> assertEquals(List.of(SampleStatus.S60, SampleStatus.S50, SampleStatus.S40,
                                SampleStatus.S30, SampleStatus.S20, SampleStatus.S10),
                        RollbackEdgePolicy.reachableTargets(SampleStatus.S70)),
                () -> assertEquals(List.of(SampleStatus.S10),
                        RollbackEdgePolicy.reachableTargets(SampleStatus.S20)),
                () -> assertTrue(RollbackEdgePolicy.reachableTargets(SampleStatus.S10).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.reachableTargets(SampleStatus.S80).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.reachableTargets(SampleStatus.S90).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.reachableTargets(null).isEmpty())
        );
    }

    @Test
    @DisplayName("★链的分组：链上任一级敏感即整链敏感（S70→S50 必须二次确认）")
    void chainGroup_anySensitiveStepMakesWholeChainSensitive() {
        assertAll(
                () -> assertEquals(RollbackGroup.SENSITIVE, RollbackEdgePolicy.chainGroup(
                        SampleStatus.S70, RollbackEdgePolicy.chain(SampleStatus.S70, SampleStatus.S60))),
                () -> assertEquals(RollbackGroup.SENSITIVE, RollbackEdgePolicy.chainGroup(
                        SampleStatus.S70, RollbackEdgePolicy.chain(SampleStatus.S70, SampleStatus.S50))),
                () -> assertEquals(RollbackGroup.SENSITIVE, RollbackEdgePolicy.chainGroup(
                        SampleStatus.S70, RollbackEdgePolicy.chain(SampleStatus.S70, SampleStatus.S10))),
                () -> assertEquals(RollbackGroup.NORMAL, RollbackEdgePolicy.chainGroup(
                        SampleStatus.S60, RollbackEdgePolicy.chain(SampleStatus.S60, SampleStatus.S10))),
                () -> assertNull(RollbackEdgePolicy.chainGroup(SampleStatus.S60, List.of()))
        );
    }

    @Test
    @DisplayName("★链的失效范围：各步取并集（S40→S10 = 清指派 + 失效明细/结果）")
    void chainScope_isUnionOfSteps() {
        assertAll(
                () -> assertEquals(Set.of(ArchiveTarget.ASSIGN_FIELDS, ArchiveTarget.ITEM, ArchiveTarget.RESULT),
                        RollbackEdgePolicy.chainScope(SampleStatus.S40,
                                RollbackEdgePolicy.chain(SampleStatus.S40, SampleStatus.S10))),
                () -> assertEquals(Set.of(ArchiveTarget.ASSIGN_FIELDS),
                        RollbackEdgePolicy.chainScope(SampleStatus.S40,
                                RollbackEdgePolicy.chain(SampleStatus.S40, SampleStatus.S30))),
                () -> assertEquals(Set.of(ArchiveTarget.RESULT),
                        RollbackEdgePolicy.chainScope(SampleStatus.S60,
                                RollbackEdgePolicy.chain(SampleStatus.S60, SampleStatus.S40)))
        );
    }

    @Test
    @DisplayName("链文案与 code/label 列表一一对应")
    void chainText_and_codes() {
        List<SampleStatus> chain = RollbackEdgePolicy.chain(SampleStatus.S40, SampleStatus.S10);
        assertAll(
                () -> assertEquals(List.of(30, 20, 10), RollbackEdgePolicy.chainCodes(chain)),
                () -> assertEquals(List.of("已分解", "登记确认", "已登记"), RollbackEdgePolicy.chainLabels(chain)),
                () -> assertEquals("已安排 → 已分解 → 登记确认 → 已登记",
                        RollbackEdgePolicy.chainText(SampleStatus.S40, chain),
                        "含起点；此处起点恰为链首状态" ),
                () -> assertEquals("检验中 → 已安排 → 已分解 → 登记确认 → 已登记",
                        RollbackEdgePolicy.chainText(SampleStatus.S50,
                                RollbackEdgePolicy.chain(SampleStatus.S50, SampleStatus.S10))),
                () -> assertEquals("", RollbackEdgePolicy.chainText(SampleStatus.S40, List.of()))
        );
    }

    @Test
    @DisplayName("治理指引：仅 S80/S90 有替代动作；S10（流程起点）不产生指引")
    void noPathHint_onlySignedAndReported() {
        assertAll(
                () -> assertEquals(4102, RollbackEdgePolicy.noPathHint(SampleStatus.S80).orElseThrow().code()),
                () -> assertEquals(4103, RollbackEdgePolicy.noPathHint(SampleStatus.S90).orElseThrow().code()),
                () -> assertTrue(RollbackEdgePolicy.noPathHint(SampleStatus.S10).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.noPathHint(SampleStatus.S50).isEmpty())
        );
    }
}
