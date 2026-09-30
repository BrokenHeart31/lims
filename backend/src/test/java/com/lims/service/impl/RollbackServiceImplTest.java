package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
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
import com.lims.service.rollback.RollbackExecutor;
import com.lims.service.rollback.RollbackPlanner;
import com.lims.service.rollback.SampleDataDisposer;
import com.lims.service.rollback.SampleFieldSnapshot;
import com.lims.vo.RollbackActionResultVO;
import com.lims.vo.RollbackBatchResultVO;
import com.lims.vo.RollbackHistoryVO;
import com.lims.vo.RollbackPreviewVO;
import com.lims.vo.RollbackTargetsVO;
import com.lims.vo.RollbackTimelineVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程回溯服务单元测试（feature B，2026-09-30 改造后：「环节内嵌 + 可选目标步 + 批量」）。
 *
 * <p><b>职责边界</b>：本类测「读路径 + 批量编排」——目标步推导、影响预览、批量逐条结果汇总、
 * 撤销回退前置护栏、时间线装配、历史分页映射。
 * <b>单样品回退的写路径与四条不变式在 {@link com.lims.service.rollback.RollbackExecutorTest}。</b></p>
 */
class RollbackServiceImplTest {

    private SampleMapper sampleMapper;
    private SampleItemMapper sampleItemMapper;
    private SampleResultMapper sampleResultMapper;
    private SampleRollbackMapper rollbackMapper;
    private SampleDataDisposer disposer;
    private SampleStatusLogService statusLogService;
    private RollbackPlanner rollbackPlanner;
    private RollbackExecutor rollbackExecutor;
    private SampleFieldSnapshot sampleFieldSnapshot;

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
        rollbackExecutor = mock(RollbackExecutor.class);
        sampleFieldSnapshot = new SampleFieldSnapshot(sampleMapper, new ObjectMapper());
        service = new RollbackServiceImpl(sampleMapper, sampleItemMapper, sampleResultMapper, rollbackMapper,
                disposer, statusLogService, rollbackPlanner, rollbackExecutor, sampleFieldSnapshot);
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

    private void loginWith(String... authorities) {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        for (String a : authorities) {
            auths.add(new SimpleGrantedAuthority(a));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nj003", null, auths));
    }

    private RollbackPreviewDTO previewDto(int target) {
        RollbackPreviewDTO dto = new RollbackPreviewDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setTargetStatus(target);
        return dto;
    }

    private RollbackExecuteDTO batchDto(List<Long> ids, int target, String reason, Boolean confirmed) {
        RollbackExecuteDTO dto = new RollbackExecuteDTO();
        dto.setIds(ids);
        dto.setTargetStatus(target);
        dto.setReason(reason);
        dto.setSecondConfirmed(confirmed);
        return dto;
    }

    private RollbackActionResultVO actionVO(long sampleId, SampleStatus status, String batchNo, int stepCount) {
        RollbackActionResultVO vo = new RollbackActionResultVO();
        vo.setSampleId(sampleId);
        vo.setSampleNo("JK(2023)-SA-00" + sampleId);
        vo.setStatus(status.getCode());
        vo.setStatusLabel(status.getLabel());
        vo.setRollbackId(99L);
        vo.setBatchNo(batchNo);
        vo.setStepCount(stepCount);
        vo.setCanRecover(true);
        vo.setAffectedItemCount(0);
        vo.setAffectedResultCount(0);
        return vo;
    }

    // ============================================================ B7 目标步 + 预览
    @Test
    @DisplayName("★目标步：S50 可退 4 步（40/30/20/10），每步带链路与影响摘要；S80 无目标但有治理指引")
    void targets_enumeration() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(rollbackPlanner.invalidationList(anyLong(), any(SampleStatus.class), anyList()))
                .thenReturn(List.of());

        RollbackTargetsVO vo = service.targets(SAMPLE_ID);

        assertAll(
                () -> assertEquals(50, vo.getCurrentStatus()),
                () -> assertTrue(vo.isRollbackAvailable()),
                () -> assertEquals(4, vo.getTargets().size()),
                () -> assertEquals(List.of(40, 30, 20, 10),
                        vo.getTargets().stream().map(RollbackTargetsVO.Target::getStatus).toList()),
                () -> assertEquals(List.of(1, 2, 3, 4),
                        vo.getTargets().stream().map(RollbackTargetsVO.Target::getStepCount).toList()),
                () -> assertEquals("检验中 → 已安排 → 已分解 → 登记确认 → 已登记",
                        vo.getTargets().get(3).getChainText()),
                () -> assertEquals(RollbackGroup.NORMAL.getCode(), vo.getTargets().get(0).getGroup()),
                () -> assertFalse(vo.getTargets().get(0).isNeedSensitive()),
                () -> assertTrue(vo.getRejected().isEmpty(), "S50 可回退，不应有治理指引")
        );

        // S80：无任何目标步 + 带作废/召回指引（4102）
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S80));
        RollbackTargetsVO signed = service.targets(SAMPLE_ID);
        assertAll(
                () -> assertFalse(signed.isRollbackAvailable()),
                () -> assertTrue(signed.getTargets().isEmpty()),
                () -> assertEquals(1, signed.getRejected().size()),
                () -> assertEquals(4102, signed.getRejected().get(0).getCode()),
                () -> assertTrue(signed.getRejected().get(0).getMsg().contains("作废"))
        );
    }

    @Test
    @DisplayName("★目标步：S70 的 6 步全部标为敏感（链上任一级敏感即整链敏感）")
    void targets_sensitiveChainMarked() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S70));
        when(rollbackPlanner.invalidationList(anyLong(), any(SampleStatus.class), anyList())).thenReturn(List.of());

        RollbackTargetsVO vo = service.targets(SAMPLE_ID);

        assertAll(
                () -> assertEquals(6, vo.getTargets().size()),
                () -> assertTrue(vo.getTargets().stream().allMatch(RollbackTargetsVO.Target::isNeedSensitive),
                        "S70 出发的每一步都必经 S70→S60 敏感边，故整链均需敏感鉴权"),
                () -> assertTrue(vo.getTargets().stream().allMatch(RollbackTargetsVO.Target::isNeedSecondConfirm))
        );
    }

    @Test
    @DisplayName("预览：允许边 → allowed=true、分组=常规、无需二次确认、带链路与失效清单")
    void preview_allowed() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        RollbackPreviewVO.Invalidation inv = new RollbackPreviewVO.Invalidation();
        inv.setType("sample_result");
        inv.setCount(9);
        when(rollbackPlanner.invalidationList(SAMPLE_ID, SampleStatus.S50, List.of(SampleStatus.S40)))
                .thenReturn(List.of(inv));

        RollbackPreviewVO vo = service.preview(previewDto(40));

        assertAll(
                () -> assertTrue(vo.isAllowed()),
                () -> assertEquals(50, vo.getFromStatus()),
                () -> assertEquals(40, vo.getToStatus()),
                () -> assertEquals(RollbackGroup.NORMAL.getCode(), vo.getGroup()),
                () -> assertFalse(vo.isNeedSecondConfirm()),
                () -> assertFalse(vo.isNeedSensitive()),
                () -> assertEquals(1, vo.getStepCount()),
                () -> assertEquals(List.of(40), vo.getChainCodes()),
                () -> assertEquals("检验中 → 已安排", vo.getChainText()),
                () -> assertEquals(1, vo.getInvalidations().size()),
                () -> assertTrue(vo.getHint().contains("失效"))
        );
    }

    @Test
    @DisplayName("★预览：跨级 S50→S20 → allowed=true、stepCount=2、链路含中间落点（不再是 4101）")
    void preview_crossLevelAllowed() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(rollbackPlanner.invalidationList(anyLong(), any(SampleStatus.class), anyList())).thenReturn(List.of());

        RollbackPreviewVO vo = service.preview(previewDto(20));

        assertAll(
                () -> assertTrue(vo.isAllowed()),
                () -> assertEquals(3, vo.getStepCount(), "S50→S20 需经 40/30 两级中间落点"),
                () -> assertEquals(List.of(40, 30, 20), vo.getChainCodes()),
                () -> assertEquals("检验中 → 已安排 → 已分解 → 登记确认", vo.getChainText()),
                () -> assertFalse(vo.isNeedSensitive())
        );
    }

    @Test
    @DisplayName("★预览：被拒 S80→S70 → allowed=false + code=4102（不抛 HTTP 错误，前端据此改走作废）")
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

    // ============================================================ B3 批量执行
    @Test
    @DisplayName("★批量：2 成功 + 1 失败 → 逐条明细给出失败业务码与原因，绝不静默跳过")
    void executeBatch_partialFailure() {
        when(rollbackExecutor.executeOne(11L, SampleStatus.S30, "整批做错了", null))
                .thenReturn(actionVO(11L, SampleStatus.S30, "RB-1", 1));
        when(rollbackExecutor.executeOne(12L, SampleStatus.S30, "整批做错了", null))
                .thenThrow(new BizException(4101, "样品状态不允许从「检验完成」回退至「已分解」"));
        when(rollbackExecutor.executeOne(13L, SampleStatus.S30, "整批做错了", null))
                .thenReturn(actionVO(13L, SampleStatus.S30, "RB-3", 1));

        RollbackBatchResultVO vo = service.execute(
                batchDto(List.of(11L, 12L, 13L), 30, "整批做错了", null));

        assertAll(
                () -> assertEquals(3, vo.getTotal()),
                () -> assertEquals(2, vo.getSuccessCount()),
                () -> assertEquals(1, vo.getFailCount()),
                () -> assertEquals(30, vo.getTargetStatus()),
                () -> assertEquals("已分解", vo.getTargetStatusLabel()),
                () -> assertEquals(3, vo.getItems().size()),
                () -> assertTrue(vo.getItems().get(0).isSuccess()),
                () -> assertEquals("RB-1", vo.getItems().get(0).getResult().getBatchNo()),
                () -> assertFalse(vo.getItems().get(1).isSuccess()),
                () -> assertEquals(4101, vo.getItems().get(1).getCode()),
                () -> assertTrue(vo.getItems().get(1).getReason().contains("回退")),
                () -> assertTrue(vo.getItems().get(2).isSuccess()),
                // 逐条独立调用（不是一次性批量下发）→ 单条失败不影响其他
                () -> verify(rollbackExecutor, times(3)).executeOne(anyLong(), any(), anyString(), any())
        );
    }

    @Test
    @DisplayName("★批量：非预期异常也逐条回报（避免「点了没反应」），并归入 500 段")
    void executeBatch_unexpectedErrorReported() {
        when(rollbackExecutor.executeOne(21L, SampleStatus.S40, "原因", null))
                .thenThrow(new IllegalStateException("DB 连接池耗尽"));

        RollbackBatchResultVO vo = service.execute(batchDto(List.of(21L), 40, "原因", null));

        assertAll(
                () -> assertEquals(0, vo.getSuccessCount()),
                () -> assertEquals(1, vo.getFailCount()),
                () -> assertEquals(500, vo.getItems().get(0).getCode()),
                () -> assertTrue(vo.getItems().get(0).getReason().contains("DB 连接池耗尽"))
        );
    }

    @Test
    @DisplayName("批量：重复 id 去重（避免「1 成功 + 1 冲突失败」被误读为 bug）")
    void executeBatch_dedupeIds() {
        when(rollbackExecutor.executeOne(31L, SampleStatus.S20, "去重", null))
                .thenReturn(actionVO(31L, SampleStatus.S20, "RB-9", 1));

        RollbackBatchResultVO vo = service.execute(batchDto(List.of(31L, 31L, 31L), 20, "去重", null));

        assertAll(
                () -> assertEquals(1, vo.getTotal()),
                () -> assertEquals(1, vo.getSuccessCount()),
                () -> verify(rollbackExecutor, times(1)).executeOne(anyLong(), any(), anyString(), any())
        );
    }

    @Test
    @DisplayName("批量：空 ids / 非法目标步 → 400（请求级错误，不进入逐条循环）")
    void executeBatch_requestLevelValidation() {
        assertThrows(BizException.class, () -> service.execute(batchDto(List.of(), 30, "空", null)));
        assertThrows(BizException.class, () -> service.execute(batchDto(List.of(1L), 35, "非法状态", null)));
        verify(rollbackExecutor, never()).executeOne(anyLong(), any(), anyString(), any());
    }

    // ============================================================ B4 恢复
    @Test
    @DisplayName("★恢复：未产生新下游数据 → 状态从 to 回 from，标记 recovered，追加 RECOVER 流水（同批次号）")
    void recover_ok() {
        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setSampleId(SAMPLE_ID);
        rb.setSampleNo("JK(2023)-SA-001");
        rb.setBatchNo("RB-20260930120000000ABCD");
        rb.setFromStatus(SampleStatus.S60);
        rb.setToStatus(SampleStatus.S50);
        rb.setStepCount(1);
        rb.setCanRecover(1);
        rb.setRecovered(0);
        rb.setOperatedAt(LocalDateTime.now().minusMinutes(5));
        rb.setRestoredSampleJson("{\"status\":60,\"auditBy\":\"nj003\"}");
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
                () -> assertEquals("RB-20260930120000000ABCD", vo.getBatchNo()),
                () -> assertFalse(vo.getCanRecover())
        );
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.RECOVER),
                eq(SampleStatus.S50), eq(SampleStatus.S60), eq("撤销回退至检验完成"), eq("复查后确认无需回退"),
                eq(RollbackExecutor.SOURCE_ROLLBACK), eq(9L), eq("RB-20260930120000000ABCD"), any());
        // 撤销回退要把被清空的 sample_info 字段原样放回
        verify(sampleMapper, times(1)).update(isNull(), any());
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
    @DisplayName("时间线：可达目标步（含跨级）+ 事件装配（回退事件补 canRecover/recovered）")
    void timeline_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));

        SampleStatusLog log = new SampleStatusLog();
        log.setId(41L);
        log.setSampleId(SAMPLE_ID);
        log.setEventType(StatusEventType.ROLLBACK);
        log.setFromStatus(SampleStatus.S60);
        log.setToStatus(SampleStatus.S50);
        log.setRollbackId(9L);
        log.setBatchNo("RB-1");
        when(statusLogService.timeline(SAMPLE_ID)).thenReturn(List.of(log));

        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setCanRecover(1);
        rb.setRecovered(0);
        when(rollbackMapper.selectList(any())).thenReturn(List.of(rb));

        RollbackTimelineVO vo = service.timeline(SAMPLE_ID);

        assertAll(
                () -> assertEquals(50, vo.getCurrentStatus()),
                () -> assertEquals(List.of(10, 20, 30, 40), vo.getCanRollbackTo()),
                () -> assertEquals(4, vo.getRollbackEdges().size()),
                () -> assertEquals(1, vo.getRollbackEdges().get(0).getStepCount()),
                () -> assertEquals(4, vo.getRollbackEdges().get(3).getStepCount()),
                () -> assertEquals(RollbackGroup.NORMAL.getCode(), vo.getRollbackEdges().get(0).getGroup()),
                () -> assertTrue(vo.getRejectedEdges().isEmpty(), "S50 可回退，不应有治理指引"),
                () -> assertEquals(1, vo.getEvents().size()),
                () -> assertEquals(4, vo.getEvents().get(0).getEventType()),
                () -> assertEquals("回退", vo.getEvents().get(0).getEventTypeLabel()),
                () -> assertEquals("RB-1", vo.getEvents().get(0).getBatchNo()),
                () -> assertTrue(vo.getEvents().get(0).getCanRecover()),
                () -> assertFalse(vo.getEvents().get(0).getRecovered())
        );
    }

    @Test
    @DisplayName("★时间线：S80 无可退目标 + 带 4102 治理指引（前端据此只显示作废/召回）")
    void timeline_signedHasNoTargetButHint() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S80));
        when(statusLogService.timeline(SAMPLE_ID)).thenReturn(List.of());

        RollbackTimelineVO vo = service.timeline(SAMPLE_ID);

        assertAll(
                () -> assertTrue(vo.getCanRollbackTo().isEmpty()),
                () -> assertTrue(vo.getRollbackEdges().isEmpty()),
                () -> assertEquals(1, vo.getRejectedEdges().size()),
                () -> assertEquals(4102, vo.getRejectedEdges().get(0).getCode())
        );
    }

    // ============================================================ B5 回退记录分页
    @Test
    @DisplayName("回退记录分页：映射 VO（含批次号 / 级数 / 分组 / 状态中文名 / 可否再撤销）")
    void history_ok() {
        SampleRollback rb = new SampleRollback();
        rb.setId(9L);
        rb.setSampleId(SAMPLE_ID);
        rb.setSampleNo("JK(2023)-SA-001");
        rb.setBatchNo("RB-20260930120000000ABCD");
        rb.setFromStatus(SampleStatus.S60);
        rb.setToStatus(SampleStatus.S40);
        rb.setStepCount(2);
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
                () -> assertEquals("已安排", page.getRecords().get(0).getToStatusLabel()),
                () -> assertEquals(2, page.getRecords().get(0).getStepCount()),
                () -> assertEquals("RB-20260930120000000ABCD", page.getRecords().get(0).getBatchNo()),
                () -> assertEquals(3, page.getRecords().get(0).getAffectedResultCount())
        );
    }
}
