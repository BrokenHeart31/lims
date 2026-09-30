package com.lims.service.rollback;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.entity.SampleRollback;
import com.lims.entity.SampleStatusLog;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleRollbackMapper;
import com.lims.service.SampleStatusLogService;
import com.lims.vo.RollbackActionResultVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单样品回退执行器单元测试（feature B，2026-09-30 改造后）。
 *
 * <p><b>本类固化「跨级链式回退」下的四条不变式</b>：
 * <ol>
 *   <li>状态变更用**乐观条件 UPDATE**（{@code WHERE id=? AND status=旧值}），{@code updated==0} → 4108；</li>
 *   <li>下游**失效处置在状态 UPDATE 之后**（InOrder 断言顺序），逐级成立；</li>
 *   <li>**每级恰好 1 条** {@code event_type=4} 流水、**同批次号**；整批只在
 *       {@code sample_rollback} 落 **1 行**（from=起点、to=最终目标步、step_count=级数）；</li>
 *   <li>不物理删除任何业务历史——本类只断言「Disposer 被调用且时序正确」，
 *       物理删除路径的断言固化在 {@link SampleDataDisposerTest}。</li>
 * </ol>
 * 另覆盖：敏感链路（含 S70→S60 任一级）三重护栏、跨级可达性（4101）、S80/S90 专门码（4102/4103）。</p>
 */
class RollbackExecutorTest {

    private static final Long SAMPLE_ID = 10L;

    private SampleMapper sampleMapper;
    private SampleRollbackMapper rollbackMapper;
    private SampleDataDisposer disposer;
    private SampleStatusLogService statusLogService;

    private RollbackExecutor executor;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, SampleItem.class);
        TableInfoHelper.initTableInfo(assistant, SampleResult.class);
        TableInfoHelper.initTableInfo(assistant, SampleRollback.class);
        TableInfoHelper.initTableInfo(assistant, SampleStatusLog.class);
    }

    @BeforeEach
    void setUp() {
        sampleMapper = mock(SampleMapper.class);
        rollbackMapper = mock(SampleRollbackMapper.class);
        disposer = mock(SampleDataDisposer.class);
        statusLogService = mock(SampleStatusLogService.class);
        // SampleFieldSnapshot 用真实实例（快照序列化是纯函数，仅依赖 ObjectMapper）
        SampleFieldSnapshot snapshot = new SampleFieldSnapshot(sampleMapper, new ObjectMapper());
        executor = new RollbackExecutor(sampleMapper, rollbackMapper, disposer, statusLogService, snapshot);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ 工具

    private Sample sample(SampleStatus status) {
        Sample s = new Sample();
        s.setId(SAMPLE_ID);
        s.setSampleNo("JK(2023)-SA-001");
        s.setStatus(status);
        return s;
    }

    private void stubInsertReturnsId(long id) {
        when(rollbackMapper.insert(any(SampleRollback.class))).thenAnswer(inv -> {
            inv.getArgument(0, SampleRollback.class).setId(id);
            return 1;
        });
    }

    private void loginWith(String... authorities) {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        for (String a : authorities) {
            auths.add(new SimpleGrantedAuthority(a));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nj003", null, auths));
    }

    // =========================================================================
    // 单级回退（既有行为回归）
    // =========================================================================

    @Test
    @DisplayName("★单级 S20→S10：1 次状态 UPDATE / 0 次失效处置 / 1 条流水（带批次号）/ 整批 1 行 rollback")
    void executeOne_singleLevel_noDownstream() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        stubInsertReturnsId(99L);
        when(sampleMapper.update(any(), any())).thenReturn(1);

        RollbackActionResultVO vo = executor.executeOne(SAMPLE_ID, SampleStatus.S10, "登记确认点错了", null);

        assertAll(
                () -> assertEquals(10, vo.getStatus()),
                () -> assertEquals("已登记", vo.getStatusLabel()),
                () -> assertEquals(99L, vo.getRollbackId()),
                () -> assertNotNull(vo.getBatchNo()),
                () -> assertEquals(1, vo.getStepCount()),
                () -> assertEquals("登记确认 → 已登记", vo.getChainText()),
                () -> assertTrue(vo.getCanRecover()),
                () -> assertEquals(0, vo.getAffectedItemCount()),
                () -> assertEquals(0, vo.getAffectedResultCount())
        );
        verify(sampleMapper, times(1)).update(any(), any());
        verify(disposer, never()).invalidateItems(any(), anyLong());
        verify(disposer, never()).invalidateResults(any(), anyLong());
        verify(disposer, never()).resetAssignFields(any(), anyLong());
        // 整批 1 行 sample_rollback
        ArgumentCaptor<SampleRollback> inserted = ArgumentCaptor.forClass(SampleRollback.class);
        verify(rollbackMapper, times(1)).insert(inserted.capture());
        assertAll(
                () -> assertEquals(SampleStatus.S20, inserted.getValue().getFromStatus()),
                () -> assertEquals(SampleStatus.S10, inserted.getValue().getToStatus()),
                () -> assertEquals(1, inserted.getValue().getStepCount()),
                () -> assertEquals(vo.getBatchNo(), inserted.getValue().getBatchNo()),
                () -> assertEquals(1, inserted.getValue().getCanRecover())
        );
        // 每级恰好 1 条 event_type=4，from/to/rollbackId/batchNo 与 rollback 行一致
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.ROLLBACK),
                eq(SampleStatus.S20), eq(SampleStatus.S10), eq("回退至已登记"), eq("登记确认点错了"),
                eq(RollbackExecutor.SOURCE_ROLLBACK), eq(99L), eq(vo.getBatchNo()), any());
    }

    @Test
    @DisplayName("★单级 S30→S20：先状态 UPDATE 再失效明细/结果（InOrder），计数回写 rollback 行")
    void executeOne_invalidatesDownstream_afterStatusUpdate() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S30));
        stubInsertReturnsId(55L);
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(disposer.invalidateItems(SAMPLE_ID, 55L))
                .thenReturn(new SampleDataDisposer.DispositionResult(3, 0, "失效 3 项检测明细"));
        when(disposer.invalidateResults(SAMPLE_ID, 55L))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 2, "失效 2 项检验结果"));

        RollbackActionResultVO vo = executor.executeOne(SAMPLE_ID, SampleStatus.S20, "分解错了要重做", null);

        assertAll(
                () -> assertEquals(20, vo.getStatus()),
                () -> assertEquals(3, vo.getAffectedItemCount()),
                () -> assertEquals(2, vo.getAffectedResultCount()),
                () -> assertTrue(vo.getInvalidatedSummary().contains("失效 3 项检测明细")),
                () -> assertTrue(vo.getInvalidatedSummary().contains("失效 2 项检验结果"))
        );
        InOrder inOrder = inOrder(sampleMapper, disposer);
        inOrder.verify(sampleMapper).update(any(), any());
        inOrder.verify(disposer).invalidateItems(SAMPLE_ID, 55L);
        inOrder.verify(disposer).invalidateResults(SAMPLE_ID, 55L);
        ArgumentCaptor<SampleRollback> patch = ArgumentCaptor.forClass(SampleRollback.class);
        verify(rollbackMapper, times(1)).updateById(patch.capture());
        assertAll(
                () -> assertEquals(55L, patch.getValue().getId()),
                () -> assertEquals(3, patch.getValue().getAffectedItemCount()),
                () -> assertEquals(2, patch.getValue().getAffectedResultCount())
        );
    }

    // =========================================================================
    // 跨级链式（本次改造核心）
    // =========================================================================

    @Test
    @DisplayName("★★跨级 S40→S10：3 级各 1 次乐观 UPDATE + 各 1 条同批次流水；整批仍只落 1 行 rollback")
    void executeOne_crossLevel_chainPerLevel() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        stubInsertReturnsId(77L);
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(disposer.resetAssignFields(SAMPLE_ID, 77L))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 0, "清空 2 项任务指派"));
        when(disposer.invalidateItems(SAMPLE_ID, 77L))
                .thenReturn(new SampleDataDisposer.DispositionResult(5, 0, "失效 5 项检测明细"));
        when(disposer.invalidateResults(SAMPLE_ID, 77L))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 5, "失效 5 项检验结果"));

        RollbackActionResultVO vo = executor.executeOne(SAMPLE_ID, SampleStatus.S10, "整批做错了，退回登记", null);

        assertAll(
                () -> assertEquals(10, vo.getStatus()),
                () -> assertEquals(3, vo.getStepCount()),
                () -> assertEquals("已安排 → 已分解 → 登记确认 → 已登记", vo.getChainText()),
                () -> assertEquals(5, vo.getAffectedItemCount()),
                () -> assertEquals(5, vo.getAffectedResultCount())
        );

        // 不变式①：3 级 = 3 次乐观条件 UPDATE（每级一次，逐级锁定状态）
        verify(sampleMapper, times(3)).update(any(), any());
        // 不变式③：3 条 event_type=4，from→to 依次为 40→30、30→20、20→10，批次号一致
        ArgumentCaptor<SampleStatus> fromCaptor = ArgumentCaptor.forClass(SampleStatus.class);
        ArgumentCaptor<SampleStatus> toCaptor = ArgumentCaptor.forClass(SampleStatus.class);
        ArgumentCaptor<String> batchCaptor = ArgumentCaptor.forClass(String.class);
        verify(statusLogService, times(3)).append(any(Sample.class), eq(StatusEventType.ROLLBACK),
                fromCaptor.capture(), toCaptor.capture(), anyString(), eq("整批做错了，退回登记"),
                eq(RollbackExecutor.SOURCE_ROLLBACK), eq(77L), batchCaptor.capture(), any());
        assertAll(
                () -> assertEquals(List.of(SampleStatus.S40, SampleStatus.S30, SampleStatus.S20), fromCaptor.getAllValues()),
                () -> assertEquals(List.of(SampleStatus.S30, SampleStatus.S20, SampleStatus.S10), toCaptor.getAllValues()),
                () -> assertEquals(1, batchCaptor.getAllValues().stream().distinct().count(),
                        "同一批次的各级流水必须共用同一 batchNo"),
                () -> assertEquals(vo.getBatchNo(), batchCaptor.getAllValues().get(0))
        );
        // 整批只有 1 行 sample_rollback（记录起点 → 最终目标步）
        ArgumentCaptor<SampleRollback> inserted = ArgumentCaptor.forClass(SampleRollback.class);
        verify(rollbackMapper, times(1)).insert(inserted.capture());
        assertAll(
                () -> assertEquals(SampleStatus.S40, inserted.getValue().getFromStatus()),
                () -> assertEquals(SampleStatus.S10, inserted.getValue().getToStatus()),
                () -> assertEquals(3, inserted.getValue().getStepCount())
        );
    }

    @Test
    @DisplayName("★★跨级 S40→S10：失效处置逐级穿插在对应级的 UPDATE 之后（严格时序）")
    void executeOne_crossLevel_dispositionOrderPerLevel() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        stubInsertReturnsId(78L);
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(disposer.resetAssignFields(anyLong(), anyLong()))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 0, "清空指派"));
        when(disposer.invalidateItems(anyLong(), anyLong()))
                .thenReturn(new SampleDataDisposer.DispositionResult(1, 0, "失效明细"));
        when(disposer.invalidateResults(anyLong(), anyLong()))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 1, "失效结果"));

        executor.executeOne(SAMPLE_ID, SampleStatus.S10, "整批做错了，退回登记", null);

        // 期望时序：UPDATE(40→30) → 清指派 → UPDATE(30→20) → 失效明细 → 失效结果 → UPDATE(20→10)
        InOrder inOrder = inOrder(sampleMapper, disposer);
        inOrder.verify(sampleMapper).update(any(), any());
        inOrder.verify(disposer).resetAssignFields(SAMPLE_ID, 78L);
        inOrder.verify(sampleMapper).update(any(), any());
        inOrder.verify(disposer).invalidateItems(SAMPLE_ID, 78L);
        inOrder.verify(disposer).invalidateResults(SAMPLE_ID, 78L);
        inOrder.verify(sampleMapper).update(any(), any());
        // 每一级都不得重复处置（S40→S10 链上清指派只发生一次）
        verify(disposer, times(1)).resetAssignFields(anyLong(), anyLong());
        verify(disposer, times(1)).invalidateItems(anyLong(), anyLong());
        verify(disposer, times(1)).invalidateResults(anyLong(), anyLong());
    }

    @Test
    @DisplayName("★跨级链上含敏感级：S70→S50 仍需 rollback:sensitive + 二次确认（4104/4105/4106）")
    void executeOne_sensitiveChainGuards() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S70));

        BizException forbidden = assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S50, "撤销审核并回退录入", true));
        assertEquals(4104, forbidden.getCode());

        loginWith("rollback:sensitive");
        BizException noConfirm = assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S50, "撤销审核并回退录入", false));
        assertEquals(4105, noConfirm.getCode());

        BizException noReason = assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S50, "  ", true));
        assertEquals(4106, noReason.getCode());

        verify(rollbackMapper, never()).insert(any(SampleRollback.class));
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("★S80/S90 出发：一律 4102 / 4103（不给任何目标步开口，改走作废召回）")
    void executeOne_signedOrReportedRejected() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S80));
        assertEquals(4102, assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S70, "退回去", null)).getCode());
        assertEquals(4102, assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S10, "退到底", null)).getCode());

        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S90));
        assertEquals(4103, assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S10, "退到底", null)).getCode());
        verify(rollbackMapper, never()).insert(any(SampleRollback.class));
    }

    @Test
    @DisplayName("★不可达目标步：同级 / 逆向上行 → 4101，未落任何库")
    void executeOne_unreachable() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        assertEquals(4101, assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S60, "同级", null)).getCode());
        assertEquals(4101, assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S70, "逆向上行", null)).getCode());
        verify(rollbackMapper, never()).insert(any(SampleRollback.class));
    }

    @Test
    @DisplayName("★乐观冲突：某级 UPDATE 影响 0 行 → 4108，且该级不写流水（整链回滚）")
    @SuppressWarnings("unchecked")
    void executeOne_optimisticConflict_secondLevel() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        stubInsertReturnsId(70L);
        // 第 1 级成功、第 2 级冲突
        when(sampleMapper.update(any(), any())).thenReturn(1, 0);
        when(disposer.resetAssignFields(anyLong(), anyLong()))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 0, "清空指派"));

        BizException ex = assertThrows(BizException.class,
                () -> executor.executeOne(SAMPLE_ID, SampleStatus.S10, "跨级并发", null));
        assertEquals(4108, ex.getCode());

        // 仅第 1 级写了流水（第 2 级冲突后立即抛出，不再继续）
        verify(statusLogService, times(1)).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // 不变式①加固：捕获 wrapper，断言 WHERE 为「id=? AND status=旧值」
        ArgumentCaptor<Wrapper<Sample>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sampleMapper, times(2)).update(any(Sample.class), wrapperCaptor.capture());
        LambdaUpdateWrapper<Sample> second = (LambdaUpdateWrapper<Sample>) wrapperCaptor.getAllValues().get(1);
        String where = String.valueOf(second.getSqlSegment()).toLowerCase();
        assertAll(
                () -> assertTrue(where.contains("status"), "乐观条件 UPDATE 必须带 status 条件，实际：" + where),
                () -> assertTrue(where.contains("id"), "乐观条件 UPDATE 必须带 id 条件，实际：" + where),
                () -> assertTrue(second.getParamNameValuePairs().values().contains(SAMPLE_ID)
                                || second.getParamNameValuePairs().values().contains(SAMPLE_ID.intValue()),
                        "应绑定样品 id 参数")
        );
        // 第 2 级对应的是 S30→S20（失效明细），冲突后不应执行
        verify(disposer, never()).invalidateItems(anyLong(), anyLong());
    }

    @Test
    @DisplayName("回退到 S10 时清空 confirmed_*（跨级链的末级也会触发）")
    @SuppressWarnings("unchecked")
    void executeOne_clearsConfirmFields_whenReachingS10() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        stubInsertReturnsId(80L);
        when(sampleMapper.update(any(), any())).thenReturn(1);

        executor.executeOne(SAMPLE_ID, SampleStatus.S10, "退回登记", null);

        ArgumentCaptor<Wrapper<Sample>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sampleMapper, times(1)).update(any(Sample.class), captor.capture());
        LambdaUpdateWrapper<Sample> wrapper = (LambdaUpdateWrapper<Sample>) captor.getValue();
        assertTrue(String.valueOf(wrapper.getSqlSet()).toLowerCase().contains("confirmed"),
                "回退到 S10 必须显式清空 confirmed_*（MP 实体式 update 忽略 null），实际 SET：" + wrapper.getSqlSet());
    }
}
