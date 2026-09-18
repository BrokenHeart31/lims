package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.PageResult;
import com.lims.common.enums.RollbackGroup;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.dto.RollbackExecuteDTO;
import com.lims.dto.RollbackHistoryQueryDTO;
import com.lims.dto.RollbackPreviewDTO;
import com.lims.dto.RollbackRecoverDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.entity.SampleRollback;
import com.lims.entity.SampleStatusLog;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.mapper.SampleRollbackMapper;
import com.lims.service.SampleStatusLogService;
import com.lims.service.rollback.RollbackPlanner;
import com.lims.service.rollback.SampleDataDisposer;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTimelineVO;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程回溯服务单元测试（feature B，T02）。
 *
 * <p><b>固化不变式①②③</b>（设计 §0 / §10-R3）<b>并指向④的独立断言</b>：
 * <ol>
 *   <li>状态变更用乐观条件 UPDATE，{@code updated==0} → 4108；</li>
 *   <li><b>失效处置在状态 UPDATE 之后</b>（用 InOrder 断言顺序）；</li>
 *   <li>一次回退后 {@code sample_status_log} <b>新增且仅新增 1 条</b> {@code event_type=4}，
 *       其 {@code (from,to,rollbackId)} 与 {@code sample_rollback} 行一致；</li>
 *   <li>不物理删除任何业务历史——<b>本类只断言到「Disposer 被调用且时序正确」，物理删除路径的
 *       断言固化在 {@code com.lims.service.rollback.SampleDataDisposerTest}</b>
 *       （穷举 Mapper 调用记录断言无 delete/remove + 反射断言 Mapper 无 {@code @Delete}）。
 *       故本类<b>不再</b>声称覆盖不变式④。</li>
 * </ol>
 * 另覆盖：敏感边的权限/二次确认/原因三护栏、被拒边专门业务码、恢复前置与时间线装配。</p>
 */
class RollbackServiceImplTest {

    private SampleMapper sampleMapper;
    private SampleItemMapper sampleItemMapper;
    private SampleResultMapper sampleResultMapper;
    private SampleRollbackMapper rollbackMapper;
    private SampleDataDisposer disposer;
    private SampleStatusLogService statusLogService;
    private RollbackPlanner rollbackPlanner;

    private RollbackServiceImpl service;

    private static final Long SAMPLE_ID = 10L;

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
        sampleItemMapper = mock(SampleItemMapper.class);
        sampleResultMapper = mock(SampleResultMapper.class);
        rollbackMapper = mock(SampleRollbackMapper.class);
        disposer = mock(SampleDataDisposer.class);
        statusLogService = mock(SampleStatusLogService.class);
        rollbackPlanner = mock(RollbackPlanner.class);
        // 真实 ObjectMapper（快照序列化是纯函数，无需 mock）
        service = new RollbackServiceImpl(sampleMapper, sampleItemMapper, sampleResultMapper, rollbackMapper,
                disposer, statusLogService, rollbackPlanner, new ObjectMapper());
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

    private RollbackExecuteDTO execDto(int target, String reason, Boolean confirmed) {
        RollbackExecuteDTO dto = new RollbackExecuteDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setTargetStatus(target);
        dto.setReason(reason);
        dto.setSecondConfirmed(confirmed);
        return dto;
    }

    private void loginWith(String... authorities) {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        for (String a : authorities) {
            auths.add(new SimpleGrantedAuthority(a));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nj003", null, auths));
    }

    // ============================================================ B2 预览
    @Test
    @DisplayName("预览：允许边 → allowed=true、分组=常规、无需二次确认、带失效清单")
    void preview_allowed() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        RollbackPreviewVO.Invalidation inv = new RollbackPreviewVO.Invalidation();
        inv.setType("sample_result");
        inv.setCount(9);
        when(rollbackPlanner.invalidationList(SAMPLE_ID, SampleStatus.S50, SampleStatus.S40))
                .thenReturn(List.of(inv));

        RollbackPreviewVO vo = service.preview(previewDto(40));

        assertAll(
                () -> assertTrue(vo.isAllowed()),
                () -> assertEquals(50, vo.getFromStatus()),
                () -> assertEquals(40, vo.getToStatus()),
                () -> assertEquals(RollbackGroup.NORMAL.getCode(), vo.getGroup()),
                () -> assertFalse(vo.isNeedSecondConfirm()),
                () -> assertFalse(vo.isNeedSensitive()),
                () -> assertEquals(1, vo.getInvalidations().size()),
                () -> assertTrue(vo.getHint().contains("失效"))
        );
    }

    @Test
    @DisplayName("★预览：被拒边 S80→S70 → allowed=false + code=4102（不抛 HTTP 错误，前端据此改走作废）")
    void preview_rejected() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S80));

        RollbackPreviewVO vo = service.preview(previewDto(70));

        assertAll(
                () -> assertFalse(vo.isAllowed()),
                () -> assertEquals(4102, vo.getCode()),
                () -> assertTrue(vo.getMsg().contains("作废")),
                () -> assertTrue(vo.isIrreversible())
        );
    }

    private RollbackPreviewDTO previewDto(int target) {
        RollbackPreviewDTO dto = new RollbackPreviewDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setTargetStatus(target);
        return dto;
    }

    // ============================================================ B3 执行回退
    @Test
    @DisplayName("★执行：S20→S10 无下游数据 → 状态乐观 UPDATE；恰好一条 event_type=4 流水，rollbackId 一致")
    void execute_noDownstream() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        stubInsertReturnsId(99L);
        when(sampleMapper.update(any(), any())).thenReturn(1);

        RollbackActionResultVO vo = service.execute(execDto(10, "登记确认点错了", null));

        assertAll(
                () -> assertEquals(10, vo.getStatus()),
                () -> assertEquals("已登记", vo.getStatusLabel()),
                () -> assertEquals(99L, vo.getRollbackId()),
                () -> assertTrue(vo.getCanRecover()),
                () -> assertEquals(0, vo.getAffectedItemCount()),
                () -> assertEquals(0, vo.getAffectedResultCount())
        );
        verify(sampleMapper, times(1)).update(any(), any());
        verify(disposer, never()).invalidateItems(any(), any());
        verify(disposer, never()).invalidateResults(any(), any());
        verify(disposer, never()).resetAssignFields(any(), any());
        // 不变式 3：恰好一条 event_type=4，from/to/rollbackId 与 sample_rollback 一致
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.ROLLBACK),
                eq(SampleStatus.S20), eq(SampleStatus.S10), eq("回退至已登记"), eq("登记确认点错了"),
                eq("ROLLBACK_PANEL"), eq(99L), any());
    }

    @Test
    @DisplayName("★执行：S30→S20 → 先状态 UPDATE 再失效明细/结果（InOrder），计数回写 sample_rollback")
    void execute_invalidatesDownstream_afterStatusUpdate() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S30));
        stubInsertReturnsId(55L);
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(disposer.invalidateItems(SAMPLE_ID, 55L))
                .thenReturn(new SampleDataDisposer.DispositionResult(3, 0, "失效 3 项检测明细"));
        when(disposer.invalidateResults(SAMPLE_ID, 55L))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 2, "失效 2 项检验结果"));

        RollbackActionResultVO vo = service.execute(execDto(20, "分解错了要重做", null));

        assertAll(
                () -> assertEquals(20, vo.getStatus()),
                () -> assertEquals(55L, vo.getRollbackId()),
                () -> assertEquals(3, vo.getAffectedItemCount()),
                () -> assertEquals(2, vo.getAffectedResultCount()),
                () -> assertTrue(vo.getInvalidatedSummary().contains("失效 3 项检测明细")),
                () -> assertTrue(vo.getInvalidatedSummary().contains("失效 2 项检验结果"))
        );
        // 不变式 2：失效处置必须在状态 UPDATE 之后
        InOrder inOrder = inOrder(sampleMapper, disposer);
        inOrder.verify(sampleMapper).update(any(), any());
        inOrder.verify(disposer).invalidateItems(SAMPLE_ID, 55L);
        inOrder.verify(disposer).invalidateResults(SAMPLE_ID, 55L);
        // 计数回写
        ArgumentCaptor<SampleRollback> patch = ArgumentCaptor.forClass(SampleRollback.class);
        verify(rollbackMapper, times(1)).updateById(patch.capture());
        assertAll(
                () -> assertEquals(55L, patch.getValue().getId()),
                () -> assertEquals(3, patch.getValue().getAffectedItemCount()),
                () -> assertEquals(2, patch.getValue().getAffectedResultCount())
        );
    }

    @Test
    @DisplayName("★执行：S40→S30 → 只清空指派字段（明细保留，不失效行）")
    void execute_resetsAssignFields() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        stubInsertReturnsId(60L);
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(disposer.resetAssignFields(SAMPLE_ID, 60L))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 0, "清空 2 项任务指派"));

        RollbackActionResultVO vo = service.execute(execDto(30, "安排错了", null));

        assertEquals(30, vo.getStatus());
        verify(disposer, times(1)).resetAssignFields(SAMPLE_ID, 60L);
        verify(disposer, never()).invalidateItems(any(), any());
        verify(disposer, never()).invalidateResults(any(), any());
    }

    @Test
    @DisplayName("★执行：敏感边 S70→S60 三重护栏——无权限 4104 / 未二次确认 4105 / 原因缺失 4106")
    void execute_sensitiveEdgeGuards() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S70));

        // ① 无 rollback:sensitive 权限
        BizException forbidden = assertThrows(BizException.class,
                () -> service.execute(execDto(60, "撤销审核", true)));
        assertEquals(4104, forbidden.getCode());

        // ② 有权限但未二次确认
        loginWith("rollback:sensitive");
        BizException noConfirm = assertThrows(BizException.class,
                () -> service.execute(execDto(60, "撤销审核", false)));
        assertEquals(4105, noConfirm.getCode());

        // ③ 有权限 + 已确认，但原因缺失
        BizException noReason = assertThrows(BizException.class,
                () -> service.execute(execDto(60, "  ", true)));
        assertEquals(4106, noReason.getCode());

        // 全程未落库
        verify(rollbackMapper, never()).insert(any(SampleRollback.class));
    }

    @Test
    @DisplayName("执行：常规边原因缺失 → 4106（原因必填对所有回退成立）")
    void execute_reasonRequiredOnNormalEdge() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));

        BizException ex = assertThrows(BizException.class, () -> service.execute(execDto(50, null, null)));
        assertEquals(4106, ex.getCode());
        verify(rollbackMapper, never()).insert(any(SampleRollback.class));
    }

    @Test
    @DisplayName("★执行：跨级 S60→S40 → 4101（白名单层面即拒）")
    void execute_crossLevelRejected() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));

        BizException ex = assertThrows(BizException.class, () -> service.execute(execDto(40, "想一次退两步", null)));
        assertEquals(4101, ex.getCode());
    }

    @Test
    @DisplayName("★执行：乐观 UPDATE 影响 0 行（并发）→ 4108；且 UPDATE 的 WHERE 条件为 id=? AND status=旧值")
    @SuppressWarnings("unchecked")
    void execute_optimisticConflict() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubInsertReturnsId(70L);
        when(sampleMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.execute(execDto(50, "并发测试", null)));
        assertEquals(4108, ex.getCode());

        // 不变式①加固：捕获 wrapper，断言 WHERE 为「id=? AND status=旧值」——乐观锁的实质，
        // 而非仅断言影响 0 行（影响 0 行可能来自任意原因）
        ArgumentCaptor<Wrapper<Sample>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sampleMapper, times(1)).update(any(Sample.class), wrapperCaptor.capture());
        LambdaUpdateWrapper<Sample> wrapper = (LambdaUpdateWrapper<Sample>) wrapperCaptor.getValue();
        String where = String.valueOf(wrapper.getSqlSegment()).toLowerCase();
        assertAll(
                () -> assertTrue(where.contains("status"),
                        "乐观条件 UPDATE 必须带 status（旧状态）条件，实际：" + where),
                () -> assertTrue(where.contains("id"),
                        "乐观条件 UPDATE 必须带 id 条件，实际：" + where),
                () -> assertTrue(wrapper.getParamNameValuePairs().values().contains(SAMPLE_ID)
                                || wrapper.getParamNameValuePairs().values().contains(SAMPLE_ID.intValue()),
                        "应绑定样品 id 参数"),
                () -> assertTrue(wrapper.getParamNameValuePairs().values().stream()
                                .anyMatch(v -> v == SampleStatus.S60 || Integer.valueOf(60).equals(v)),
                        "应把旧状态 S60(60) 绑定为乐观条件")
        );

        // 状态未推进 → 不写流水（失效处置也不执行）
        verify(statusLogService, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(disposer, never()).invalidateItems(any(), any());
    }

    // ============================================================ B4 恢复
    @Test
    @DisplayName("★恢复：未产生新下游数据 → 状态从 to 回 from，标记 recovered，追加 RECOVER 流水")
    void recover_ok() {
        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setSampleId(SAMPLE_ID);
        rb.setSampleNo("JK(2023)-SA-001");
        rb.setFromStatus(SampleStatus.S60);
        rb.setToStatus(SampleStatus.S50);
        rb.setCanRecover(1);
        rb.setRecovered(0);
        rb.setOperatedAt(LocalDateTime.now().minusMinutes(5));
        when(rollbackMapper.selectById(9L)).thenReturn(rb);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(disposer.restoreByRollback(9L))
                .thenReturn(new SampleDataDisposer.DispositionResult(0, 0, null));

        RollbackRecoverDTO dto = new RollbackRecoverDTO();
        dto.setRollbackId(9L);
        dto.setReason("复查后确认无需回退");
        RollbackActionResultVO vo = service.recover(dto);

        assertAll(
                () -> assertEquals(60, vo.getStatus()),
                () -> assertEquals(9L, vo.getRollbackId()),
                () -> assertFalse(vo.getCanRecover())
        );
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.RECOVER),
                eq(SampleStatus.S50), eq(SampleStatus.S60), eq("恢复至检验完成"), eq("复查后确认无需回退"),
                eq("ROLLBACK_PANEL"), eq(9L), any());
        ArgumentCaptor<SampleRollback> patch = ArgumentCaptor.forClass(SampleRollback.class);
        verify(rollbackMapper, times(1)).updateById(patch.capture());
        assertEquals(1, patch.getValue().getRecovered());
    }

    @Test
    @DisplayName("恢复：已被恢复过 → 4107")
    void recover_alreadyRecovered() {
        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setSampleId(SAMPLE_ID);
        rb.setCanRecover(1);
        rb.setRecovered(1);
        when(rollbackMapper.selectById(9L)).thenReturn(rb);

        RollbackRecoverDTO dto = new RollbackRecoverDTO();
        dto.setRollbackId(9L);
        BizException ex = assertThrows(BizException.class, () -> service.recover(dto));
        assertEquals(4107, ex.getCode());
    }

    @Test
    @DisplayName("恢复：样品状态已前进（≠ 回退后状态）→ 4107（不可原路恢复）")
    void recover_sampleAdvanced() {
        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setSampleId(SAMPLE_ID);
        rb.setToStatus(SampleStatus.S50);
        rb.setFromStatus(SampleStatus.S60);
        rb.setCanRecover(1);
        rb.setRecovered(0);
        when(rollbackMapper.selectById(9L)).thenReturn(rb);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));

        RollbackRecoverDTO dto = new RollbackRecoverDTO();
        dto.setRollbackId(9L);
        BizException ex = assertThrows(BizException.class, () -> service.recover(dto));
        assertEquals(4107, ex.getCode());
    }

    // ============================================================ B1 时间线
    @Test
    @DisplayName("时间线：当前可回退目标 + 被拒边参考 + 事件（回退事件补 canRecover/recovered）")
    void timeline_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));

        SampleStatusLog log = new SampleStatusLog();
        log.setId(41L);
        log.setSampleId(SAMPLE_ID);
        log.setEventType(StatusEventType.ROLLBACK);
        log.setFromStatus(SampleStatus.S60);
        log.setToStatus(SampleStatus.S50);
        log.setRollbackId(9L);
        when(statusLogService.timeline(SAMPLE_ID)).thenReturn(List.of(log));

        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setCanRecover(1);
        rb.setRecovered(0);
        when(rollbackMapper.selectList(any())).thenReturn(List.of(rb));

        RollbackTimelineVO vo = service.timeline(SAMPLE_ID);

        assertAll(
                () -> assertEquals(50, vo.getCurrentStatus()),
                () -> assertTrue(vo.getCanRollbackTo().contains(40)),
                () -> assertEquals(1, vo.getRollbackEdges().size()),
                () -> assertEquals(RollbackGroup.NORMAL.getCode(), vo.getRollbackEdges().get(0).getGroup()),
                () -> assertEquals(2, vo.getRejectedEdges().size(), "S80→S70、S90→S80 两条被拒边"),
                () -> assertEquals(1, vo.getEvents().size()),
                () -> assertEquals(4, vo.getEvents().get(0).getEventType()),
                () -> assertEquals("回退", vo.getEvents().get(0).getEventTypeLabel()),
                () -> assertTrue(vo.getEvents().get(0).getCanRecover()),
                () -> assertFalse(vo.getEvents().get(0).getRecovered())
        );
    }

    // ============================================================ B5 回退记录分页
    @Test
    @DisplayName("回退记录分页：映射 VO（含分组/状态中文名与可否再撤销）")
    void history_ok() {
        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setSampleId(SAMPLE_ID);
        rb.setSampleNo("JK(2023)-SA-001");
        rb.setFromStatus(SampleStatus.S60);
        rb.setToStatus(SampleStatus.S50);
        rb.setEdgeGroup(RollbackGroup.NORMAL);
        rb.setReason("误提交");
        rb.setAffectedItemCount(0);
        rb.setAffectedResultCount(3);
        rb.setCanRecover(1);
        rb.setRecovered(0);
        when(rollbackMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<SampleRollback> p = inv.getArgument(0);
            p.setRecords(List.of(rb));
            p.setTotal(1);
            return p;
        });

        RollbackHistoryQueryDTO query = new RollbackHistoryQueryDTO();
        PageResult<RollbackHistoryVO> page = service.history(1, 20, query);

        assertAll(
                () -> assertEquals(1, page.getTotal()),
                () -> assertEquals(1, page.getRecords().size()),
                () -> assertEquals("常规", page.getRecords().get(0).getEdgeGroupLabel()),
                () -> assertEquals("检验完成", page.getRecords().get(0).getFromStatusLabel()),
                () -> assertEquals("检验中", page.getRecords().get(0).getToStatusLabel()),
                () -> assertEquals(3, page.getRecords().get(0).getAffectedResultCount())
        );
    }
}
