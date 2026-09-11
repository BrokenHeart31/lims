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
}
