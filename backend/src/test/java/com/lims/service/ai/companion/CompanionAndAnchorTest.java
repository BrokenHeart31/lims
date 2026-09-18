package com.lims.service.ai.companion;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lims.dto.AiChatContextDTO;
import com.lims.entity.ProductLibItem;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.SampleItemFactMapper;
import com.lims.service.ai.SampleItemFact;
import com.lims.vo.AiValueAnchorVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 标准伴随触发策略 + 数值对齐装配的确定性测试（feature 增量 ai_flow_assistant，T02）。
 */
class CompanionAndAnchorTest {

    private final CompanionTriggerPolicy policy = new CompanionTriggerPolicy();

    @Test
    @DisplayName("★G1：无项目名 → 不触发（误报率=0 由构造保证）")
    void noItemNameNeverTriggers() {
        AiChatContextDTO ctx = new AiChatContextDTO();
        ctx.setSampleNo("S1");
        assertFalse(policy.shouldTrigger(ctx));
        assertFalse(policy.shouldTrigger(null));

        ctx.setItemName("毒死蜱");
        assertTrue(policy.shouldTrigger(ctx));
    }

    @Test
    @DisplayName("标准号归一化：GB2763-2021 / gb 2763-2021 → GB 2763-2021")
    void normalizeStdNo() {
        assertEquals("GB 2763-2021", policy.normalizeStdNo("GB2763-2021"));
        assertEquals("GB 2763-2021", policy.normalizeStdNo("gb 2763-2021"));
        assertEquals("NY/T 761-2008", policy.normalizeStdNo("NY/T 761-2008"));
        assertEquals("GB 2762-2022", policy.normalizeStdNo("GB 2762-2022"));
    }

    @Test
    @DisplayName("频控键格式 sampleNo|itemName|stdNo（空段留空）")
    void hintKeyFormat() {
        AiChatContextDTO ctx = new AiChatContextDTO();
        ctx.setSampleNo("S1");
        ctx.setItemName("毒死蜱");
        assertEquals("S1|毒死蜱|GB 2763-2021", policy.hintKey(ctx, "GB 2763-2021"));
        // 标准号为空 → 段留空
        assertEquals("S1|毒死蜱|", policy.hintKey(ctx, null));
    }

    @Test
    @DisplayName("数值对齐：优先取样品快照；无快照时回落系统标准库（ProductLibItemMapper）")
    void valueAnchorPrefersSnapshotThenLib() {
        // ① 快照命中
        SampleItemFactMapper factsHit = mock(SampleItemFactMapper.class);
        when(factsHit.selectFactBySampleAndItem(anyString(), anyString()))
                .thenReturn(new SampleItemFact(9L, "毒死蜱", "GB 2763-2021", "≤0.5", "mg/kg", 1, 0));
        ValueAnchorAssembler withSnapshot = new ValueAnchorAssembler(mock(ProductLibItemMapper.class), factsHit);
        Optional<AiValueAnchorVO> a = withSnapshot.anchor("毒死蜱", "GB 2763-2021", "S1", true);
        assertTrue(a.isPresent());
        assertEquals("≤0.5", a.get().getStdValue());
        assertEquals("限量比较", a.get().getJudgeTypeLabel());
        assertEquals("系统标准库（权威）", a.get().getSourceLabel());
        assertTrue(a.get().getJumpPath().startsWith("/query/lib?itemName="));

        // ② 快照为空 → 回落标准库
        SampleItemFactMapper factsEmpty = mock(SampleItemFactMapper.class);
        when(factsEmpty.selectFactBySampleAndItem(anyString(), anyString())).thenReturn(null);
        ProductLibItemMapper lib = mock(ProductLibItemMapper.class);
        when(lib.selectOne(any(Wrapper.class)))
                .thenReturn(libItem(302L, "毒死蜱", "GB 2763-2021", "0.5", "mg/kg", 1, 0));
        ValueAnchorAssembler libOnly = new ValueAnchorAssembler(lib, factsEmpty);
        Optional<AiValueAnchorVO> b = libOnly.anchor("毒死蜱", "GB 2763-2021", "S1", false);
        assertTrue(b.isPresent());
        assertEquals("0.5", b.get().getStdValue());
        assertEquals(302L, b.get().getLibItemId());
        assertNull(b.get().getJumpPath()); // 无 base:lib:list 权限 → 不给跳转（TE 收敛）

        // ③ 都查不到 → 空（绝不编造）
        ProductLibItemMapper libEmpty = mock(ProductLibItemMapper.class);
        when(libEmpty.selectOne(any(Wrapper.class))).thenReturn(null);
        ValueAnchorAssembler none = new ValueAnchorAssembler(libEmpty, factsEmpty);
        assertTrue(none.anchor("不存在项目", null, "S1", true).isEmpty());
    }

    private ProductLibItem libItem(Long id, String item, String basis, String value, String unit,
                                   int judgeType, int isRef) {
        ProductLibItem p = new ProductLibItem();
        p.setId(id);
        p.setItemName(item);
        p.setBasisCode(basis);
        p.setStdValue(value);
        p.setUnit(unit);
        p.setJudgeType(judgeType);
        p.setIsReference(isRef);
        return p;
    }
}
