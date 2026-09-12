package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.AuditAction;
import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import com.lims.common.exception.BizException;
import com.lims.dto.AuditApproveDTO;
import com.lims.dto.AuditReturnDTO;
import com.lims.dto.ReportSignDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleAuditLog;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleAuditLogMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.vo.AuditActionVO;
import com.lims.vo.AuditDetailVO;
import com.lims.vo.AuditPendingVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核 / 签发服务单元测试（T-701）。
 *
 * <p>覆盖：待审核/待签发列表统计、审核通过（含**异常项显式确认**红线）、
 * 审核退回（独立退回白名单 + 清空审核信息 + 原因落流水）、签发、
 * 状态前置校验、并发乐观 UPDATE 冲突、异常项清单分类（未录入 / 待判定）、
 * 流水只追加。</p>
 *
 * <p>状态机本身（正向 vs 退回两张表互不干扰）由 {@code SampleStatusTransitionTest} 覆盖。</p>
 */
class AuditServiceImplTest {

    private static final Long SAMPLE_ID = 1L;

    private SampleMapper sampleMapper;
    private SampleItemMapper sampleItemMapper;
    private SampleResultMapper sampleResultMapper;
    private SampleAuditLogMapper auditLogMapper;

    /** 内存流水表替身（验证「只追加」语义） */
    private final List<SampleAuditLog> logStore = new ArrayList<>();

    private AuditServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, SampleItem.class);
        TableInfoHelper.initTableInfo(assistant, SampleResult.class);
        TableInfoHelper.initTableInfo(assistant, SampleAuditLog.class);
    }

    @BeforeEach
    void setUp() {
        sampleMapper = mock(SampleMapper.class);
        sampleItemMapper = mock(SampleItemMapper.class);
        sampleResultMapper = mock(SampleResultMapper.class);
        auditLogMapper = mock(SampleAuditLogMapper.class);
        logStore.clear();

        service = new AuditServiceImpl(sampleMapper, sampleItemMapper, sampleResultMapper);
        try {
            java.lang.reflect.Field f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                    .getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(service, auditLogMapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入 baseMapper", e);
        }

        when(auditLogMapper.insert(any(SampleAuditLog.class))).thenAnswer(inv -> {
            logStore.add(inv.getArgument(0));
            return 1;
        });
        when(auditLogMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(logStore));
        when(sampleMapper.update(any(), any())).thenReturn(1);
    }

    // ------------------------------------------------------------------ 工具

    private Sample sample(SampleStatus status) {
        Sample s = new Sample();
        s.setId(SAMPLE_ID);
        s.setSampleNo("JK(2026)-SA-001");
        s.setSampleName("花鲢");
        s.setClientName("南通润发生态园");
        s.setStatus(status);
        s.setConclusion(ResultConclusion.QUALIFIED);
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
        r.setSampleId(SAMPLE_ID);
        r.setSampleItemId(itemId);
        r.setTestValue(testValue);
        r.setConclusion(conclusion);
        r.setConclusionSource(ConclusionSource.ENGINE);
        r.setJudgeBasis("测试依据");
        return r;
    }

    /** 已录齐且均为合格 / 不合格（无异常项） */
    private void stubAllEntered() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1),
                item(12L, 2, "氯霉素", 2)));
        when(sampleResultMapper.selectList(any())).thenReturn(List.of(
                result(11L, "0.10", ResultConclusion.QUALIFIED),
                result(12L, "未检出", ResultConclusion.QUALIFIED)));
    }

    /** 一项待判定（有值但引擎判不出）+ 一项空值（未录入） */
    private void stubWithAbnormal() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1),
                item(12L, 2, "孔雀石绿", 2),
                item(13L, 3, "镉（以Cd计）", 1)));
        when(sampleResultMapper.selectList(any())).thenReturn(List.of(
                result(11L, "0.10", ResultConclusion.QUALIFIED),
                result(12L, "0.01", ResultConclusion.PENDING),   // 待判定（检出限缺失）
                result(13L, "  ", ResultConclusion.PENDING)));   // 空值行 → 未录入
    }

    private AuditApproveDTO approveDto(Boolean confirmed) {
        AuditApproveDTO dto = new AuditApproveDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setOpinion("数据核对无误");
        dto.setAbnormalConfirmed(confirmed);
        return dto;
    }

    // ============================================================ 7.2 列表

    @Test
    @DisplayName("待审核列表：统计 itemTotal / 有效录入数 / 异常项数（空值行不计入有效录入）")
    void pagePendingAudit_counts() {
        when(sampleMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Sample> p = inv.getArgument(0);
            p.setRecords(List.of(sample(SampleStatus.S60)));
            p.setTotal(1);
            return p;
        });
        stubWithAbnormal();

        AuditPendingVO row = service.pagePendingAudit(1, 10, null, null).getRecords().get(0);

        assertAll(
                () -> assertEquals(3, row.getItemTotal()),
                () -> assertEquals(2, row.getEnteredCount(), "空值行不算有效录入（3 项中 2 项有效）"),
                () -> assertEquals(2, row.getAbnormalCount(), "1 个待判定（有值判不出）+ 1 个未录入（空值）"),
                () -> assertEquals(60, row.getStatus()),
                () -> assertEquals("检验完成", row.getStatusLabel())
        );
    }

    @Test
    @DisplayName("待签发列表：带出审核人与审核时间")
    void pagePendingSign_carriesAuditInfo() {
        Sample s = sample(SampleStatus.S70);
        s.setAuditBy("nj001");
        s.setAuditAt(java.time.LocalDateTime.now());
        when(sampleMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Sample> p = inv.getArgument(0);
            p.setRecords(List.of(s));
            p.setTotal(1);
            return p;
        });
        stubAllEntered();

        AuditPendingVO row = service.pagePendingSign(1, 10, null, null).getRecords().get(0);

        assertAll(
                () -> assertEquals(70, row.getStatus()),
                () -> assertEquals("nj001", row.getAuditBy()),
                () -> assertEquals(0, row.getAbnormalCount())
        );
    }

    // ============================================================ 7.4 审核通过

    @Test
    @DisplayName("审核通过：S60→S70，写审核人/时间/意见，追加一条 APPEND 流水")
    void approve_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubAllEntered();

        AuditActionVO vo = service.approve(approveDto(true));

        assertAll(
                () -> assertEquals(70, vo.getStatus()),
                () -> assertEquals("已审核", vo.getStatusLabel()),
                () -> assertEquals(AuditAction.APPROVE.getCode(), vo.getAction()),
                () -> assertEquals("数据核对无误", vo.getOpinion())
        );
        assertEquals(1, logStore.size(), "流水只追加一条");
        SampleAuditLog log = logStore.get(0);
        assertAll(
                () -> assertEquals(AuditAction.APPROVE, log.getAction()),
                () -> assertEquals(SampleStatus.S60, log.getFromStatus()),
                () -> assertEquals(SampleStatus.S70, log.getToStatus()),
                () -> assertEquals("数据核对无误", log.getOpinion()),
                () -> assertEquals(1, log.getAbnormalConfirmed())
        );
        // 实体式 update 带出审核字段
        ArgumentCaptor<Sample> captor = ArgumentCaptor.forClass(Sample.class);
        verify(sampleMapper, times(1)).update(captor.capture(), any());
        assertAll(
                () -> assertEquals(SampleStatus.S70, captor.getValue().getStatus()),
                () -> assertEquals("数据核对无误", captor.getValue().getAuditOpinion()),
                () -> assertTrue(captor.getValue().getAuditBy() != null)
        );
    }

    @Test
    @DisplayName("★放行红线：存在异常项且未确认 → 拒绝，不流转、不留流水")
    void approve_withAbnormalButNotConfirmed_rejected() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubWithAbnormal();

        BizException ex = assertThrows(BizException.class, () -> service.approve(approveDto(false)));

        assertAll(
                () -> assertEquals(400, ex.getCode()),
                () -> assertTrue(ex.getMessage().contains("异常项清单"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("2"), ex.getMessage())
        );
        verify(sampleMapper, never()).update(any(), any());
        assertTrue(logStore.isEmpty(), "被拒绝的动作不得留流水");
    }

    @Test
    @DisplayName("放行红线：已显式确认异常项 → 放行，流水 abnormalConfirmed=1")
    void approve_withAbnormalConfirmed_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubWithAbnormal();

        AuditActionVO vo = service.approve(approveDto(true));

        assertAll(
                () -> assertEquals(70, vo.getStatus()),
                () -> assertEquals(1, logStore.size()),
                () -> assertEquals(1, logStore.get(0).getAbnormalConfirmed())
        );
    }

    @Test
    @DisplayName("审核通过：非 S60（如 S50）→ 拒绝")
    void approve_wrongStatus() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));

        BizException ex = assertThrows(BizException.class, () -> service.approve(approveDto(true)));
        assertTrue(ex.getMessage().contains("不允许审核"), ex.getMessage());
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("审核通过：并发导致 UPDATE 影响 0 行 → 抛「状态已变更」")
    void approve_concurrentConflict() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubAllEntered();
        when(sampleMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.approve(approveDto(true)));
        assertTrue(ex.getMessage().contains("状态已变更"), ex.getMessage());
        assertTrue(logStore.isEmpty(), "流转失败不得留流水");
    }

    // ============================================================ 7.5 审核退回

    @Test
    @DisplayName("★审核退回：S60→S50（退回白名单），原因落流水，并清空当前审核信息")
    void returnToTester_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubAllEntered();

        AuditReturnDTO dto = new AuditReturnDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setReason("铅的原始记录与录入值不一致，请复核后重录");

        AuditActionVO vo = service.returnToTester(dto);

        assertAll(
                () -> assertEquals(50, vo.getStatus()),
                () -> assertEquals("检验中", vo.getStatusLabel()),
                () -> assertEquals(AuditAction.RETURN.getCode(), vo.getAction()),
                () -> assertEquals("检验中", vo.getStatusLabel())
        );
        assertEquals(1, logStore.size());
        assertAll(
                () -> assertEquals(AuditAction.RETURN, logStore.get(0).getAction()),
                () -> assertEquals(SampleStatus.S60, logStore.get(0).getFromStatus()),
                () -> assertEquals(SampleStatus.S50, logStore.get(0).getToStatus()),
                () -> assertTrue(logStore.get(0).getOpinion().contains("原始记录"))
        );
    }

    @Test
    @DisplayName("审核退回：非 S60（如 S70）→ 拒绝（退回也受状态前置约束）")
    void returnToTester_wrongStatus() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S70));

        AuditReturnDTO dto = new AuditReturnDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setReason("有问题");

        BizException ex = assertThrows(BizException.class, () -> service.returnToTester(dto));
        assertTrue(ex.getMessage().contains("不允许审核退回"), ex.getMessage());
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("审核退回：原因空白 → 拒绝（退回必须说明原因）")
    void returnToTester_blankReason() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubAllEntered();

        AuditReturnDTO dto = new AuditReturnDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setReason("   ");

        BizException ex = assertThrows(BizException.class, () -> service.returnToTester(dto));
        assertTrue(ex.getMessage().contains("退回原因不能为空"), ex.getMessage());
        assertTrue(logStore.isEmpty());
    }

    // ============================================================ 7.6 签发

    @Test
    @DisplayName("签发：S70→S80，写签发人/时间，追加一条 SIGN 流水")
    void sign_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S70));

        ReportSignDTO dto = new ReportSignDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setOpinion("同意签发");

        AuditActionVO vo = service.sign(dto);

        assertAll(
                () -> assertEquals(80, vo.getStatus()),
                () -> assertEquals("已签发", vo.getStatusLabel()),
                () -> assertEquals(AuditAction.SIGN.getCode(), vo.getAction())
        );
        assertEquals(1, logStore.size());
        assertEquals(AuditAction.SIGN, logStore.get(0).getAction());

        ArgumentCaptor<Sample> captor = ArgumentCaptor.forClass(Sample.class);
        verify(sampleMapper, times(1)).update(captor.capture(), any());
        assertAll(
                () -> assertEquals(SampleStatus.S80, captor.getValue().getStatus()),
                () -> assertTrue(captor.getValue().getSignBy() != null),
                () -> assertTrue(captor.getValue().getSignAt() != null)
        );
    }

    @Test
    @DisplayName("签发：未审核（S60）→ 拒绝，不允许跳过审核")
    void sign_wrongStatus() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));

        ReportSignDTO dto = new ReportSignDTO();
        dto.setSampleId(SAMPLE_ID);

        BizException ex = assertThrows(BizException.class, () -> service.sign(dto));
        assertTrue(ex.getMessage().contains("不允许签发"), ex.getMessage());
        verify(sampleMapper, never()).update(any(), any());
    }

    // ============================================================ 7.3 明细

    @Test
    @DisplayName("明细：异常项清单分「未录入(BLANK)」与「待判定(PENDING)」两类，未录入项不出网结论")
    void detail_abnormalItemsClassified() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S60));
        stubWithAbnormal();

        AuditDetailVO vo = service.detail(SAMPLE_ID);

        assertAll(
                () -> assertEquals(3, vo.getItemTotal()),
                () -> assertEquals(2, vo.getEnteredCount(), "3 项中 2 项有效录入"),
                () -> assertEquals(2, vo.getAbnormalCount()),
                () -> assertEquals(1, vo.getBlankCount()),
                () -> assertEquals(1, vo.getPendingCount()),
                () -> assertTrue(vo.getAllowAudit()),
                () -> assertFalse(vo.getAllowSign()),
                // 排序确定：未录入在前、待判定在后（审核人先看「根本没数据」的项）
                () -> assertEquals("BLANK", vo.getAbnormalItems().get(0).getType()),
                () -> assertEquals("未录入", vo.getAbnormalItems().get(0).getTypeLabel()),
                () -> assertEquals("镉（以Cd计）", vo.getAbnormalItems().get(0).getItemName()),
                () -> assertEquals("PENDING", vo.getAbnormalItems().get(1).getType()),
                () -> assertEquals("待判定", vo.getAbnormalItems().get(1).getTypeLabel()),
                () -> assertEquals("孔雀石绿", vo.getAbnormalItems().get(1).getItemName())
        );
        // 空值项（项次 3）：entered=false 且不出网 conclusion
        AuditDetailVO.Item blank = vo.getItems().get(2);
        assertAll(
                () -> assertFalse(blank.getEntered()),
                () -> assertNull(blank.getConclusion(), "未录入不得出网 conclusion"),
                () -> assertNull(blank.getConclusionLabel())
        );
    }

    @Test
    @DisplayName("明细：已审核(S70) → allowSign=true、allowAudit=false，带出审核信息")
    void detail_signable() {
        Sample s = sample(SampleStatus.S70);
        s.setAuditBy("nj001");
        s.setAuditOpinion("同意");
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        stubAllEntered();

        AuditDetailVO vo = service.detail(SAMPLE_ID);

        assertAll(
                () -> assertFalse(vo.getAllowAudit()),
                () -> assertTrue(vo.getAllowSign()),
                () -> assertEquals("nj001", vo.getAuditBy()),
                () -> assertEquals("同意", vo.getAuditOpinion()),
                () -> assertEquals(0, vo.getAbnormalCount())
        );
    }

    @Test
    @DisplayName("明细：样品不存在 → BizException(400)")
    void detail_sampleNotFound() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.detail(SAMPLE_ID));
        assertEquals(400, ex.getCode());
    }
}
