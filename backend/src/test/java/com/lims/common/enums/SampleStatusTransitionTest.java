package com.lims.common.enums;

import com.lims.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 样品状态机白名单单元测试（AGENTS 4.3：核心业务规则必须写单测）。
 *
 * <p>覆盖：白名单每个 entry 一条正向断言 + 关键非法流转断言 + 终态无出边 +
 * code 映射与非法 code 处理。</p>
 */
class SampleStatusTransitionTest {

    @Test
    @DisplayName("白名单：S10→S90 每个合法流转均通过")
    void shouldAllowAllWhitelistedTransitions() {
        assertAll(
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S10, SampleStatus.S20)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S20, SampleStatus.S30)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S30, SampleStatus.S40)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S40, SampleStatus.S50)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S50, SampleStatus.S50)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S50, SampleStatus.S60)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S60, SampleStatus.S70)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S70, SampleStatus.S80)),
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(SampleStatus.S80, SampleStatus.S90))
        );
    }

    @Test
    @DisplayName("非法流转：跳态/回退一律拒绝")
    void shouldRejectIllegalTransitions() {
        assertAll(
                // 跳态：跨越 S30 直接安排
                () -> assertFalse(SampleStatusTransition.canTransition(SampleStatus.S20, SampleStatus.S40)),
                // 回退：已确认不得回到已登记（T-301 登记确认不可逆）
                () -> assertFalse(SampleStatusTransition.canTransition(SampleStatus.S20, SampleStatus.S10)),
                // 倒流：检验完成不得回到检验中
                () -> assertFalse(SampleStatusTransition.canTransition(SampleStatus.S60, SampleStatus.S50)),
                // 未登记确认不得直接分解
                () -> assertFalse(SampleStatusTransition.canTransition(SampleStatus.S10, SampleStatus.S30)),
                // S90 终态
                () -> assertFalse(SampleStatusTransition.canTransition(SampleStatus.S90, SampleStatus.S10))
        );
    }

    @Test
    @DisplayName("非法流转抛业务异常且业务码为 400")
    void shouldThrowBizExceptionWithCode400() {
        BizException ex = assertThrows(BizException.class,
                () -> SampleStatusTransition.assertTransition(SampleStatus.S10, SampleStatus.S90));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不允许"));
    }

    @Test
    @DisplayName("T-301 核心：登记确认仅允许 S10→S20")
    void shouldAllowOnlyS10ToS20ForRegistrationConfirm() {
        assertTrue(SampleStatusTransition.canTransition(SampleStatus.S10, SampleStatus.S20));
        assertEquals(1, SampleStatusTransition.nextAllowed(SampleStatus.S10).size());
        assertTrue(SampleStatusTransition.nextAllowed(SampleStatus.S10).contains(SampleStatus.S20));
    }

    @Test
    @DisplayName("终态 S90 无任何出边；null 输入安全返回 false")
    void shouldHaveNoOutgoingEdgeAtTerminalState() {
        assertTrue(SampleStatusTransition.nextAllowed(SampleStatus.S90).isEmpty());
        assertFalse(SampleStatusTransition.canTransition(null, SampleStatus.S20));
        assertFalse(SampleStatusTransition.canTransition(SampleStatus.S10, null));
    }

    @Test
    @DisplayName("code 与 DB TINYINT 一一对应，非法 code 抛异常")
    void shouldMapCodeToEnumStrictly() {
        assertAll(
                () -> assertEquals(SampleStatus.S10, SampleStatus.of(10)),
                () -> assertEquals(SampleStatus.S20, SampleStatus.of(20)),
                () -> assertEquals(SampleStatus.S90, SampleStatus.of(90)),
                () -> assertEquals(10, SampleStatus.S10.getCode()),
                () -> assertEquals("已登记", SampleStatus.S10.getLabel()),
                () -> assertEquals("登记确认", SampleStatus.S20.getLabel()),
                () -> assertEquals(SampleStatus.S20, SampleStatus.ofNullable(20)),
                () -> assertNull(SampleStatus.ofNullable(999))
        );
        assertThrows(BizException.class, () -> SampleStatus.of(999));
    }

    @Test
    @DisplayName("按 code 断言流转（S10=10 → S20=20 通过；10 → 90 拒绝）")
    void shouldAssertTransitionByCode() {
        assertDoesNotThrow(() -> SampleStatusTransition.assertTransition(10, 20));
        assertThrows(BizException.class, () -> SampleStatusTransition.assertTransition(10, 90));
    }

    // =========================================================================
    // 退回白名单（T-701：独立于正向表，不污染正向语义）
    // =========================================================================

    @Test
    @DisplayName("★退回：S60→S50 退回合法，且**正向表不含该边**（两张表互不干扰）")
    void shouldAllowReturnFromS60ToS50ButNotInForwardTable() {
        assertAll(
                () -> assertDoesNotThrow(() -> SampleStatusTransition.assertReturn(SampleStatus.S60, SampleStatus.S50)),
                () -> assertTrue(SampleStatusTransition.canReturn(SampleStatus.S60, SampleStatus.S50)),
                // 关键不变式：退回不是正向流转，正向断言必须拒绝
                () -> assertFalse(SampleStatusTransition.canTransition(SampleStatus.S60, SampleStatus.S50)),
                () -> assertThrows(BizException.class,
                        () -> SampleStatusTransition.assertTransition(SampleStatus.S60, SampleStatus.S50)),
                // 正向断言也不承认退回（assertTransition 与 assertReturn 不可互相替代）
                () -> assertThrows(BizException.class,
                        () -> SampleStatusTransition.assertReturn(SampleStatus.S50, SampleStatus.S60))
        );
    }

    @Test
    @DisplayName("退回：仅 S60 有退回出边，其他状态一律不可退回")
    void shouldOnlyAllowReturnFromS60() {
        assertAll(
                () -> assertEquals(1, SampleStatusTransition.returnAllowed(SampleStatus.S60).size()),
                () -> assertTrue(SampleStatusTransition.returnAllowed(SampleStatus.S60).contains(SampleStatus.S50)),
                // S70（已审核）尚无退回路径——签发环节若需退回，须先改 AGENTS 7.2 再动本类
                () -> assertTrue(SampleStatusTransition.returnAllowed(SampleStatus.S70).isEmpty()),
                () -> assertTrue(SampleStatusTransition.returnAllowed(SampleStatus.S90).isEmpty()),
                () -> assertFalse(SampleStatusTransition.canReturn(SampleStatus.S50, SampleStatus.S40)),
                () -> assertFalse(SampleStatusTransition.canReturn(null, SampleStatus.S50)),
                () -> assertFalse(SampleStatusTransition.canReturn(SampleStatus.S60, null))
        );
    }

    @Test
    @DisplayName("退回：非法退回抛业务异常，业务码 400 且消息带「退回」字样")
    void shouldThrowBizExceptionOnIllegalReturn() {
        BizException ex = assertThrows(BizException.class,
                () -> SampleStatusTransition.assertReturn(SampleStatus.S70, SampleStatus.S50));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("退回"), ex.getMessage());

        assertDoesNotThrow(() -> SampleStatusTransition.assertReturn(60, 50));
        assertThrows(BizException.class, () -> SampleStatusTransition.assertReturn(70, 50));
    }
}
