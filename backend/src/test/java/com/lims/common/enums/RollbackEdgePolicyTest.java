package com.lims.common.enums;

import com.lims.common.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("★拒绝原因：S80→S70=4102、S90→S80=4103、跨级=4101；允许边为空")
    void rejectReason_codes() {
        assertAll(
                () -> assertEquals(ResultCode.ROLLBACK_SIGNED.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S80, SampleStatus.S70).orElseThrow().code()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S80, SampleStatus.S70)
                        .orElseThrow().msg().contains("作废")),
                () -> assertEquals(ResultCode.ROLLBACK_REPORTED.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S90, SampleStatus.S80).orElseThrow().code()),
                () -> assertEquals(ResultCode.ROLLBACK_ILLEGAL.getCode(),
                        RollbackEdgePolicy.rejectReason(SampleStatus.S60, SampleStatus.S40).orElseThrow().code()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S60, SampleStatus.S40)
                        .orElseThrow().msg().contains("逐级")),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S60, SampleStatus.S50).isEmpty()),
                () -> assertTrue(RollbackEdgePolicy.rejectReason(SampleStatus.S70, SampleStatus.S60).isEmpty()),
                // 业务码段登记（api-spec 0.2：回退域 4100–4199）
                () -> assertEquals(4101, ResultCode.ROLLBACK_ILLEGAL.getCode()),
                () -> assertEquals(4102, ResultCode.ROLLBACK_SIGNED.getCode()),
                () -> assertEquals(4103, ResultCode.ROLLBACK_REPORTED.getCode())
        );
    }
}
