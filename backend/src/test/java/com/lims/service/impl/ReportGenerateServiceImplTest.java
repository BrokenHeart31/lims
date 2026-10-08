package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.ReportType;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.dto.ReportGenerateDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.service.SampleStatusLogService;
import com.lims.service.report.ReportDataBuilder;
import com.lims.vo.ReportVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报告生成服务单元测试（T-702 / F20 合规门禁）。
 *
 * <p>覆盖：<b>F20 门禁</b>——存在「未完成判定」（未录入 / 待判定）的检测单项时拒绝生成
 * （{@code code=400}，报告正文不得出现「待判定」行）；全部单项已裁决为确定结论时放行走通
 * S80→S90（乐观 UPDATE + 流水 + 报告合成）。</p>
 */
class ReportGenerateServiceImplTest {

    private static final Long SAMPLE_ID = 10L;
    private static final String SAMPLE_NO = "JK(2026)-SA-010";

    private SampleMapper sampleMapper;
    private SampleItemMapper sampleItemMapper;
    private SampleResultMapper sampleResultMapper;
    private SysUserMapper sysUserMapper;
    private ReportDataBuilder reportDataBuilder;
    private SampleStatusLogService statusLogService;

    private ReportGenerateServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, SampleItem.class);
        TableInfoHelper.initTableInfo(assistant, SampleResult.class);
    }

    @BeforeEach
    void setUp() {
        sampleMapper = mock(SampleMapper.class);
        sampleItemMapper = mock(SampleItemMapper.class);
        sampleResultMapper = mock(SampleResultMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        reportDataBuilder = mock(ReportDataBuilder.class);
        statusLogService = mock(SampleStatusLogService.class);

        service = new ReportGenerateServiceImpl(sampleMapper, sampleItemMapper, sampleResultMapper,
                sysUserMapper, reportDataBuilder, statusLogService);

        when(sampleMapper.selectOne(any())).thenReturn(sample());
        when(sampleMapper.update(any(), any())).thenReturn(1);
    }

    // ------------------------------------------------------------------ 工具

    private Sample sample() {
        Sample s = new Sample();
        s.setId(SAMPLE_ID);
        s.setSampleNo(SAMPLE_NO);
        s.setStatus(SampleStatus.S80);
        return s;
    }

    private SampleItem item(Long id, int order, String name, int judgeType) {
        SampleItem i = new SampleItem();
        i.setId(id);
        i.setSampleId(SAMPLE_ID);
        i.setItemOrder(order);
        i.setItemName(name);
        i.setJudgeType(judgeType);
        i.setIsReference(0);
        return i;
    }

    private SampleResult result(Long itemId, String testValue, ResultConclusion conclusion) {
        SampleResult r = new SampleResult();
        r.setId(itemId);
        r.setSampleId(SAMPLE_ID);
        r.setSampleItemId(itemId);
        r.setTestValue(testValue);
        r.setConclusion(conclusion);
        return r;
    }

    private ReportGenerateDTO dto() {
        ReportGenerateDTO d = new ReportGenerateDTO();
        d.setSampleNo(SAMPLE_NO);
        d.setReportType(ReportType.CMA.getCode());
        return d;
    }

    // ============================================================ F20 门禁

    @Test
    @DisplayName("★F20：存在「待判定」检测单项 → 400，不流转不留流水")
    void generate_withPendingItem_rejected() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1),
                item(12L, 2, "孔雀石绿", 2)));
        when(sampleResultMapper.selectList(any())).thenReturn(List.of(
                result(11L, "0.10", ResultConclusion.QUALIFIED),
                result(12L, "0.01", ResultConclusion.PENDING)));

        BizException ex = assertThrows(BizException.class, () -> service.generate(dto()));

        assertAll(
                () -> assertEquals(400, ex.getCode()),
                () -> assertTrue(ex.getMessage().contains("未完成判定"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("孔雀石绿"), ex.getMessage())
        );
        verify(sampleMapper, never()).update(any(), any());
        verify(statusLogService, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(reportDataBuilder, never()).build(any(), any());
    }

    @Test
    @DisplayName("★F20：存在「未录入」检测单项 → 400")
    void generate_withBlankItem_rejected() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1),
                item(12L, 2, "镉（以Cd计）", 1)));
        when(sampleResultMapper.selectList(any())).thenReturn(List.of(
                result(11L, "0.10", ResultConclusion.QUALIFIED),
                result(12L, "  ", ResultConclusion.PENDING))); // 空值行 → 未录入

        BizException ex = assertThrows(BizException.class, () -> service.generate(dto()));

        assertAll(
                () -> assertEquals(400, ex.getCode()),
                () -> assertTrue(ex.getMessage().contains("未完成判定"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("镉（以Cd计）"), ex.getMessage())
        );
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("全部单项已裁决为确定结论 → 放行 S80→S90，写流水并合成报告")
    void generate_allDecided_ok() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1),
                item(12L, 2, "孔雀石绿", 2)));
        when(sampleResultMapper.selectList(any())).thenReturn(List.of(
                result(11L, "0.10", ResultConclusion.QUALIFIED),
                result(12L, "未检出", ResultConclusion.QUALIFIED)));
        ReportVO stub = new ReportVO();
        stub.setReportNo(SAMPLE_NO);
        when(reportDataBuilder.build(anyLong(), any())).thenReturn(stub);

        ReportVO vo = service.generate(dto());

        assertNotNull(vo);
        assertEquals(SAMPLE_NO, vo.getReportNo());

        ArgumentCaptor<Sample> captor = ArgumentCaptor.forClass(Sample.class);
        verify(sampleMapper, times(1)).update(captor.capture(), any());
        assertAll(
                () -> assertEquals(SampleStatus.S90, captor.getValue().getStatus()),
                () -> assertEquals(ReportType.CMA, captor.getValue().getReportType()),
                () -> assertNotNull(captor.getValue().getReportGeneratedAt())
        );
        verify(statusLogService, times(1)).append(any(Sample.class), eq(StatusEventType.REPORT),
                eq(SampleStatus.S80), eq(SampleStatus.S90), eq("报告生成"), isNull(),
                eq("REPORT"), isNull(), isNull());
        verify(reportDataBuilder, times(1)).build(anyLong(), eq(ReportType.CMA));
    }

    @Test
    @DisplayName("非「已签发」（如 S60）→ 400，门禁前先拒")
    void generate_wrongStatus() {
        Sample s = sample();
        s.setStatus(SampleStatus.S60);
        when(sampleMapper.selectOne(any())).thenReturn(s);

        BizException ex = assertThrows(BizException.class, () -> service.generate(dto()));

        assertTrue(ex.getMessage().contains("仅已签发样品"), ex.getMessage());
        verify(sampleItemMapper, never()).selectList(any());
        verify(sampleMapper, never()).update(any(), any());
    }
}
