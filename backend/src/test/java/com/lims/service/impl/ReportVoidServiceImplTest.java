package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.dto.ReportVoidDTO;
import com.lims.entity.ReportVoid;
import com.lims.entity.Sample;
import com.lims.mapper.ReportVoidMapper;
import com.lims.mapper.SampleMapper;
import com.lims.service.SampleStatusLogService;
import com.lims.vo.ReportVoidResultVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报告作废 / 召回服务单元测试（feature B，T02）。
 *
 * <p>覆盖三条不变式：① **不改 status**（仅在 void_status 打标记）；② 乐观条件 UPDATE
 * （并发下只有一次生效）；③ **恰好一条 {@code event_type=6} 流水**（from==to，原因必填）。
 * 另覆盖：状态前置（仅 S80/S90）、幂等（不可重复作废）、二次确认、类型白名单。</p>
 */
class ReportVoidServiceImplTest {

    private static final String SAMPLE_NO = "JK(2023)-SA-001";

    private SampleMapper sampleMapper;
    private ReportVoidMapper reportVoidMapper;
    private SampleStatusLogService statusLogService;

    private ReportVoidServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, ReportVoid.class);
    }

    @BeforeEach
    void setUp() {
        sampleMapper = mock(SampleMapper.class);
        reportVoidMapper = mock(ReportVoidMapper.class);
        statusLogService = mock(SampleStatusLogService.class);
        service = new ReportVoidServiceImpl(sampleMapper, reportVoidMapper, statusLogService);
        when(sampleMapper.update(any(), any())).thenReturn(1);
        when(reportVoidMapper.insert(any(ReportVoid.class))).thenReturn(1);
    }

    // ------------------------------------------------------------------ 工具

    private Sample sample(SampleStatus status, Integer voidStatus) {
        Sample s = new Sample();
        s.setId(10L);
        s.setSampleNo(SAMPLE_NO);
        s.setStatus(status);
        s.setVoidStatus(voidStatus);
        return s;
    }

    private ReportVoidDTO dto(int voidType, boolean confirmed, String reason) {
        ReportVoidDTO d = new ReportVoidDTO();
        d.setSampleNo(SAMPLE_NO);
        d.setVoidType(voidType);
        d.setSecondConfirmed(confirmed);
        d.setReason(reason);
        return d;
    }

    // ============================================================ 作废（S80）
    @Test
    @DisplayName("★作废：S80 → 写 report_void + void_status=1，status 保持 80，恰好一条 event_type=6 流水")
    @SuppressWarnings("unchecked")
    void voidReport_ok() {
        when(sampleMapper.selectOne(any())).thenReturn(sample(SampleStatus.S80, 0));

        ReportVoidResultVO vo = service.voidReport(dto(ReportVoid.TYPE_VOID, true, "受检单位申请撤回"));

        assertAll(
                () -> assertEquals(10L, vo.getSampleId()),
                () -> assertEquals(SAMPLE_NO, vo.getSampleNo()),
                () -> assertEquals(ReportVoid.TYPE_VOID, vo.getVoidType()),
                () -> assertEquals("作废", vo.getVoidTypeLabel()),
                // 关键不变式：state 未变
                () -> assertEquals(80, vo.getStatusAtVoid()),
                () -> assertEquals("已签发", vo.getStatusAtVoidLabel()),
                () -> assertEquals("受检单位申请撤回", vo.getReason()),
                () -> assertEquals(1, vo.getSecondConfirmed())
        );
        // 只写标记 + 记录，不改 status 字段
        ArgumentCaptor<Sample> sampleCaptor = ArgumentCaptor.forClass(Sample.class);
        ArgumentCaptor<Wrapper<Sample>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sampleMapper, times(1)).update(sampleCaptor.capture(), wrapperCaptor.capture());
        assertEquals(ReportVoid.TYPE_VOID, sampleCaptor.getValue().getVoidStatus());
        assertNull(sampleCaptor.getValue().getStatus(), "status 不得被改写");
        // 不变式②加固：乐观条件必须带「void_status = 0」——并发下重复作废只有一次生效
        LambdaUpdateWrapper<Sample> wrapper = (LambdaUpdateWrapper<Sample>) wrapperCaptor.getValue();
        String where = String.valueOf(wrapper.getSqlSegment()).toLowerCase();
        assertAll(
                () -> assertTrue(where.contains("void_status"),
                        "乐观条件 UPDATE 必须带 void_status 条件，实际：" + where),
                () -> assertTrue(where.contains("id"),
                        "乐观条件 UPDATE 必须带 id 条件，实际：" + where),
                () -> assertTrue(wrapper.getParamNameValuePairs().values().stream()
                                .anyMatch(v -> Integer.valueOf(0).equals(v)),
                        "应把 VOID_NONE=0 绑定为乐观条件")
        );
        verify(reportVoidMapper, times(1)).insert(any(ReportVoid.class));
        // 恰好一条流水：event_type=6，from==to（状态未变），原因带上
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.VOID),
                eq(SampleStatus.S80), eq(SampleStatus.S80), eq("作废"), eq("受检单位申请撤回"),
                eq("REPORT"), isNull(), any(String.class));
    }

    @Test
    @DisplayName("召回：voidStatus=2，label=召回，status 保持 90")
    void recallReport_ok() {
        when(sampleMapper.selectOne(any())).thenReturn(sample(SampleStatus.S90, 0));

        ReportVoidResultVO vo = service.voidReport(dto(ReportVoid.TYPE_RECALL, true, "数据需更正"));

        assertAll(
                () -> assertEquals(ReportVoid.TYPE_RECALL, vo.getVoidType()),
                () -> assertEquals("召回", vo.getVoidTypeLabel()),
                () -> assertEquals(90, vo.getStatusAtVoid())
        );
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.VOID),
                eq(SampleStatus.S90), eq(SampleStatus.S90), eq("召回"), eq("数据需更正"),
                eq("REPORT"), isNull(), any(String.class));
    }

    @Test
    @DisplayName("★前置：非 S80/S90（如 S60）→ 拒绝，不写库不留流水")
    void voidReport_wrongStatus() {
        when(sampleMapper.selectOne(any())).thenReturn(sample(SampleStatus.S60, 0));

        BizException ex = assertThrows(BizException.class,
                () -> service.voidReport(dto(ReportVoid.TYPE_VOID, true, "x")));
        assertTrue(ex.getMessage().contains("仅「已签发 / 已出报告」"), ex.getMessage());
        verify(reportVoidMapper, never()).insert(any(ReportVoid.class));
        verify(statusLogService, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★幂等：已作废/已召回（voidStatus≠0）→ 拒绝")
    void voidReport_alreadyVoided() {
        when(sampleMapper.selectOne(any())).thenReturn(sample(SampleStatus.S80, 1));

        BizException ex = assertThrows(BizException.class,
                () -> service.voidReport(dto(ReportVoid.TYPE_VOID, true, "x")));
        assertTrue(ex.getMessage().contains("已作废或已召回"), ex.getMessage());
    }

    @Test
    @DisplayName("★合规红线：未二次确认 → 拒绝")
    void voidReport_secondConfirmRequired() {
        BizException ex = assertThrows(BizException.class,
                () -> service.voidReport(dto(ReportVoid.TYPE_VOID, false, "x")));
        assertTrue(ex.getMessage().contains("二次确认"), ex.getMessage());
        verify(sampleMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("类型白名单：voidType=3 → 拒绝")
    void voidReport_invalidType() {
        BizException ex = assertThrows(BizException.class,
                () -> service.voidReport(dto(3, true, "x")));
        assertTrue(ex.getMessage().contains("作废类型非法"), ex.getMessage());
    }

    @Test
    @DisplayName("并发：乐观 UPDATE 影响 0 行 → 「请刷新后重试」")
    void voidReport_concurrentConflict() {
        when(sampleMapper.selectOne(any())).thenReturn(sample(SampleStatus.S80, 0));
        when(sampleMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> service.voidReport(dto(ReportVoid.TYPE_VOID, true, "x")));
        assertTrue(ex.getMessage().contains("刷新"), ex.getMessage());
        verify(reportVoidMapper, never()).insert(any(ReportVoid.class));
    }

    @Test
    @DisplayName("原因必填：空白原因虽被 JSR-303 拦，服务层亦兜底拒绝")
    void voidReport_blankReason() {
        BizException ex = assertThrows(BizException.class,
                () -> service.voidReport(dto(ReportVoid.TYPE_VOID, true, "   ")));
        assertTrue(ex.getMessage().contains("原因不能为空"), ex.getMessage());
    }
}
