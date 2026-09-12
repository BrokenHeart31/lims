package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import com.lims.common.exception.BizException;
import com.lims.dto.ResultSaveDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.service.judge.JudgeEngine;
import com.lims.vo.ResultDetailVO;
import com.lims.vo.ResultJudgeVO;
import com.lims.vo.ResultSaveVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * 检验结果录入服务单元测试（T-601）。
 *
 * <p>覆盖：状态前置校验（S40/S50 可录，其余拒绝）、检测单项归属校验、
 * 分次录入、首次保存 S40→S50、提交要求全录齐、S50→S60 流转、
 * 整体结论聚合（规则 6 + 白名单 D3 参考项不计入整体）。</p>
 *
 * <p>判定矩阵本身由 {@code JudgeEngineTest} 用构造数据穷举；本测试只验证**编排**，
 * 故用内存 Map 充当结果表、Mock 三个 Mapper。</p>
 */
class ResultServiceImplTest {

    private static final Long SAMPLE_ID = 1L;

    private SampleMapper sampleMapper;
    private SampleItemMapper sampleItemMapper;
    private SampleResultMapper resultMapper;

    /** 内存结果表（替身），模拟 sample_result 的 upsert 持久化 */
    private final List<SampleResult> resultStore = new ArrayList<>();

    private ResultServiceImpl service;

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
        resultMapper = mock(SampleResultMapper.class);
        resultStore.clear();

        service = new ResultServiceImpl(sampleMapper, sampleItemMapper, new JudgeEngine());
        try {
            java.lang.reflect.Field f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                    .getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(service, resultMapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入 baseMapper", e);
        }

        // 结果表替身：insert 落内存，selectList 读内存，selectOne 恒 null（本测试只走新增路径）
        when(resultMapper.insert(any(SampleResult.class))).thenAnswer(inv -> {
            resultStore.add(inv.getArgument(0));
            return 1;
        });
        when(resultMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(resultStore));
        when(resultMapper.selectOne(any())).thenReturn(null);
        when(sampleMapper.update(any(), any())).thenReturn(1);
    }

    // ------------------------------------------------------------------ 工具

    private Sample sample(SampleStatus status) {
        Sample s = new Sample();
        s.setId(SAMPLE_ID);
        s.setSampleNo("JK(2023)-SA-001");
        s.setSampleName("花鲢");
        s.setStatus(status);
        return s;
    }

    private SampleItem item(Long id, int order, String name, int judgeType,
                            String stdValue, String lowerLimit, int isReference) {
        SampleItem i = new SampleItem();
        i.setId(id);
        i.setSampleId(SAMPLE_ID);
        i.setSampleNo("JK(2023)-SA-001");
        i.setItemOrder(order);
        i.setItemName(name);
        i.setUnit("mg/kg");
        i.setJudgeType(judgeType);
        i.setStdValue(stdValue);
        i.setLowerLimit(lowerLimit);
        i.setIsReference(isReference);
        return i;
    }

    private ResultSaveDTO.Item dtoItem(Long itemId, String testValue) {
        ResultSaveDTO.Item it = new ResultSaveDTO.Item();
        it.setItemId(itemId);
        it.setTestValue(testValue);
        return it;
    }

    private ResultSaveDTO dto(Long... itemIds) {
        ResultSaveDTO dto = new ResultSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        List<ResultSaveDTO.Item> items = new ArrayList<>();
        for (Long id : itemIds) {
            items.add(dtoItem(id, "0.10"));
        }
        dto.setItems(items);
        return dto;
    }

    private SampleResult stored(Long itemId, ResultConclusion conclusion) {
        SampleResult r = new SampleResult();
        r.setSampleId(SAMPLE_ID);
        r.setSampleItemId(itemId);
        r.setConclusion(conclusion);
        r.setConclusionSource(ConclusionSource.ENGINE);
        r.setTestValue("0.10");
        return r;
    }

    // ============================================================ 6.4 保存录入

    @Test
    @DisplayName("保存：S40 首次录入 → 流转 S50 + 结果落库 + 整体结论合格")
    void save_firstEntry() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0)));

        ResultSaveVO vo = service.save(dto(11L));

        assertAll(
                () -> assertEquals(50, vo.getStatus(), "首次录入应推进到 S50"),
                () -> assertEquals("检验中", vo.getStatusLabel()),
                () -> assertEquals(1, vo.getItemTotal()),
                () -> assertEquals(1, vo.getEnteredCount()),
                () -> assertEquals(ResultConclusion.QUALIFIED.getCode(), vo.getConclusion()),
                () -> assertEquals(1, vo.getItems().size()),
                () -> assertEquals(ResultConclusion.QUALIFIED.getCode(), vo.getItems().get(0).getConclusion()),
                () -> assertTrue(vo.getItems().get(0).getJudgeBasis().contains("≤ 限量"),
                        "应回传判定依据：" + vo.getItems().get(0).getJudgeBasis())
        );
        assertEquals(ResultConclusion.QUALIFIED, resultStore.get(0).getConclusion(), "落库结论应为合格");
        assertEquals("JK(2023)-SA-001", resultStore.get(0).getSampleNo(), "样品编号应冗余落库");
        // 一次推进状态 + 一次回写整体结论
        verify(sampleMapper, times(2)).update(any(), any());
    }

    @Test
    @DisplayName("保存：S50 续录 → 不重复流转，整体结论随录入进度变化")
    void save_resumeEntry() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0),
                item(12L, 2, "镉（以Cd计）", 1, "0.1", "0.01", 0)));

        ResultSaveVO vo = service.save(dto(11L));

        assertAll(
                () -> assertEquals(50, vo.getStatus()),
                () -> assertEquals(2, vo.getItemTotal()),
                () -> assertEquals(1, vo.getEnteredCount(), "只录了 1 项"),
                () -> assertEquals(ResultConclusion.PENDING.getCode(), vo.getConclusion(),
                        "存在未录入项 → 整体待判定")
        );
    }

    @Test
    @DisplayName("保存：样品状态为 S30（未安排）→ 拒绝且不写库")
    void save_wrongStatus() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S30));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto(11L)));
        assertAll(
                () -> assertEquals(400, ex.getCode()),
                () -> assertTrue(ex.getMessage().contains("不允许录入检验结果"), ex.getMessage())
        );
        verify(resultMapper, never()).insert(any(SampleResult.class));
    }

    @Test
    @DisplayName("保存：检测单项不属于该样品 → 拒绝")
    void save_itemNotBelongsToSample() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0)));

        BizException ex = assertThrows(BizException.class, () -> service.save(dto(99L)));
        assertTrue(ex.getMessage().contains("不属于该样品"), ex.getMessage());
        verify(resultMapper, never()).insert(any(SampleResult.class));
    }

    @Test
    @DisplayName("保存：无检测单项 → 拒绝")
    void save_noItems() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.save(dto(11L)));
        assertTrue(ex.getMessage().contains("尚未完成项目分解"), ex.getMessage());
    }

    @Test
    @DisplayName("保存：jt3 感官项人工判不合格 → 结论来源=人工判定")
    void save_manualConclusion() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "感官", 3, "符合要求", null, 0)));

        ResultSaveDTO dto = new ResultSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        ResultSaveDTO.Item it = dtoItem(11L, "有异味");
        it.setManualConclusion(ResultConclusion.UNQUALIFIED.getCode());
        dto.setItems(List.of(it));

        ResultSaveVO vo = service.save(dto);

        assertAll(
                () -> assertEquals(ResultConclusion.UNQUALIFIED.getCode(), vo.getConclusion()),
                () -> assertEquals(ConclusionSource.MANUAL.getCode(), vo.getItems().get(0).getConclusionSource())
        );
        assertEquals(ConclusionSource.MANUAL, resultStore.get(0).getConclusionSource());
    }

    // ============================================================ 6.5 提交

    @Test
    @DisplayName("提交：存在未录入项 → 拒绝，不流转 S60")
    void submit_missingItems() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0),
                item(12L, 2, "镉（以Cd计）", 1, "0.1", "0.01", 0)));
        resultStore.add(stored(11L, ResultConclusion.QUALIFIED));

        BizException ex = assertThrows(BizException.class, () -> service.submit(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("未录入结果"), ex.getMessage());
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("提交：全部录齐且有不合格项 → S50→S60，整体结论不合格")
    void submit_allEntered_unqualified() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0),
                item(12L, 2, "氯霉素", 2, "不得检出", "0.02", 0)));
        resultStore.add(stored(11L, ResultConclusion.UNQUALIFIED));
        resultStore.add(stored(12L, ResultConclusion.QUALIFIED));

        ResultSaveVO vo = service.submit(SAMPLE_ID);

        assertAll(
                () -> assertEquals(60, vo.getStatus()),
                () -> assertEquals("检验完成", vo.getStatusLabel()),
                () -> assertEquals(ResultConclusion.UNQUALIFIED.getCode(), vo.getConclusion()),
                () -> assertEquals(2, vo.getItems().size())
        );
        // S50→S60 的乐观 UPDATE 只走一次（已是 S50，无需再推 S40→S50）
        verify(sampleMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("★D3：参考项不合格不计入整体结论（整体仍合格）")
    void submit_referenceUnqualifiedNotCounted() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0),
                item(12L, 2, "参考项-镉", 1, "0.1", "0.01", 1)));
        resultStore.add(stored(11L, ResultConclusion.QUALIFIED));
        resultStore.add(stored(12L, ResultConclusion.UNQUALIFIED));

        ResultSaveVO vo = service.submit(SAMPLE_ID);

        assertEquals(ResultConclusion.QUALIFIED.getCode(), vo.getConclusion(),
                "参考项不合格不应触发整体不合格");
    }

    @Test
    @DisplayName("提交：S40 一次性录齐 → 先补 S50 再 S60（两步都走白名单）")
    void submit_fromS40() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S40));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0)));
        resultStore.add(stored(11L, ResultConclusion.QUALIFIED));

        ResultSaveVO vo = service.submit(SAMPLE_ID);

        assertEquals(60, vo.getStatus());
        // S40→S50 一次 + S50→S60 一次
        verify(sampleMapper, times(2)).update(any(), any());
    }

    // ============================================================ 6.2 明细

    @Test
    @DisplayName("明细：全部为参考项 → 整体结论待判定（禁止自动判合格）")
    void detail_allReferencePending() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "参考项A", 1, "0.5", "0.02", 1),
                item(12L, 2, "参考项B", 1, "0.1", "0.01", 1)));
        resultStore.add(stored(11L, ResultConclusion.QUALIFIED));

        ResultDetailVO vo = service.detail(SAMPLE_ID);

        assertAll(
                () -> assertEquals(ResultConclusion.PENDING.getCode(), vo.getConclusion()),
                () -> assertTrue(vo.getAllowEdit(), "S50 应允许录入"),
                () -> assertEquals(2, vo.getItemTotal()),
                () -> assertEquals(1, vo.getEnteredCount()),
                () -> assertTrue(vo.getItems().get(0).getEntered(), "已录入项应标记 entered"),
                () -> assertFalse(vo.getItems().get(1).getEntered(), "未录入项应为 false"),
                () -> assertEquals("限量比较", vo.getItems().get(0).getJudgeTypeLabel())
        );
    }

    @Test
    @DisplayName("明细：样品已出报告（S90）→ allowEdit=false")
    void detail_readOnly() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S90));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0)));
        resultStore.add(stored(11L, ResultConclusion.QUALIFIED));

        ResultDetailVO vo = service.detail(SAMPLE_ID);

        assertAll(
                () -> assertFalse(vo.getAllowEdit()),
                () -> assertEquals(ResultConclusion.QUALIFIED.getCode(), vo.getConclusion())
        );
    }

    @Test
    @DisplayName("明细：样品不存在 → BizException(400)")
    void detail_sampleNotFound() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.detail(SAMPLE_ID));
        assertEquals(400, ex.getCode());
    }

    // ============================================================ 6.3 判定预览

    @Test
    @DisplayName("预览：不落库，返回结论 + 来源 + 依据")
    void judgePreview_ok() {
        when(sampleItemMapper.selectById(11L))
                .thenReturn(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0));

        ResultJudgeVO vo = service.judgePreview(11L, "0.60", null);

        assertAll(
                () -> assertEquals(11L, vo.getItemId()),
                () -> assertEquals("铅（以Pb计）", vo.getItemName()),
                () -> assertEquals(ResultConclusion.UNQUALIFIED.getCode(), vo.getConclusion()),
                () -> assertEquals("不合格", vo.getConclusionLabel()),
                () -> assertEquals(ConclusionSource.ENGINE.getCode(), vo.getConclusionSource()),
                () -> assertTrue(vo.getJudgeBasis().contains("> 限量"), vo.getJudgeBasis())
        );
        verify(resultMapper, never()).insert(any(SampleResult.class));
    }

    @Test
    @DisplayName("预览：检测单项不存在 → BizException(400)")
    void judgePreview_itemNotFound() {
        when(sampleItemMapper.selectById(11L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.judgePreview(11L, "0.1", null));
        assertEquals(400, ex.getCode());
    }

    // ============================================================ T-912 口径
    // 「已录入」= testValue 非空 ∥ (jt3 且已人工选结论)；空值行不算录入，
    // 与引擎产出的「待判定」严格区分（前者是操作缺漏，后者是数据缺口）。

    /** 空值结果行（有值但被清空 / 保存时即留空） */
    private SampleResult blankResult(Long itemId, int judgeType) {
        SampleResult r = new SampleResult();
        r.setSampleId(SAMPLE_ID);
        r.setSampleItemId(itemId);
        r.setTestValue("   ");
        r.setConclusion(ResultConclusion.PENDING);
        r.setConclusionSource(
                judgeType == 3 ? ConclusionSource.MANUAL : ConclusionSource.ENGINE);
        if (judgeType == 3) {
            // jt3 空值且未选结论 → 未录入
            r.setConclusion(ResultConclusion.PENDING);
        }
        return r;
    }

    @Test
    @DisplayName("★T-912：存在空值结果行 → 提交被拒（空值不算「已录入」）")
    void submit_blankValueRow_rejected() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(
                item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0),
                item(12L, 2, "镉（以Cd计）", 1, "0.1", "0.01", 0)));
        resultStore.add(stored(11L, ResultConclusion.QUALIFIED));   // 有效录入
        resultStore.add(blankResult(12L, 1));                        // 空值 → 未录入

        BizException ex = assertThrows(BizException.class, () -> service.submit(SAMPLE_ID));

        assertAll(
                () -> assertEquals(400, ex.getCode()),
                () -> assertTrue(ex.getMessage().contains("未录入结果"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("1"), ex.getMessage())
        );
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("★T-912：明细中空值行 entered=false 且不出网 conclusion（不伪装成待判定）")
    void detail_blankRow_notEntered() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0)));
        resultStore.add(blankResult(11L, 1));

        ResultDetailVO vo = service.detail(SAMPLE_ID);

        assertAll(
                () -> assertEquals(0, vo.getEnteredCount(), "空值行不计入已录入"),
                () -> assertFalse(vo.getItems().get(0).getEntered()),
                () -> assertNull(vo.getItems().get(0).getConclusion(), "未录入不出网 conclusion"),
                () -> assertNull(vo.getItems().get(0).getConclusionLabel()),
                () -> assertEquals(ResultConclusion.PENDING.getCode(), vo.getConclusion(),
                        "未录齐 → 整体待判定")
        );
    }

    @Test
    @DisplayName("T-912：保存空值（清空既有录入）→ 已录入数归零、整体结论回退待判定")
    void save_clearedValue_notEntered() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "铅（以Pb计）", 1, "0.5", "0.02", 0)));

        ResultSaveDTO dto = new ResultSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setItems(List.of(dtoItem(11L, "   ")));   // 清空该值

        ResultSaveVO vo = service.save(dto);

        assertAll(
                () -> assertEquals(0, vo.getEnteredCount(), "空值不算已录入"),
                () -> assertEquals(ResultConclusion.PENDING.getCode(), vo.getConclusion())
        );
    }

    @Test
    @DisplayName("T-912：jt3 感官项检验值可空，但已选结论即视为已录入")
    void save_manualJudgeType_enteredWithoutText() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S50));
        when(sampleItemMapper.selectList(any()))
                .thenReturn(List.of(item(11L, 1, "感官", 3, "符合要求", null, 0)));

        ResultSaveDTO dto = new ResultSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        ResultSaveDTO.Item it = dtoItem(11L, "");
        it.setManualConclusion(ResultConclusion.QUALIFIED.getCode());
        dto.setItems(List.of(it));

        ResultSaveVO vo = service.save(dto);

        assertAll(
                () -> assertEquals(1, vo.getEnteredCount(), "jt3 已选结论 → 已录入"),
                () -> assertEquals(ResultConclusion.QUALIFIED.getCode(), vo.getConclusion())
        );
    }
}
