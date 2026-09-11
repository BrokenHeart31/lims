package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.exception.BizException;
import com.lims.dto.ItemSaveDTO;
import com.lims.entity.ProductLib;
import com.lims.entity.ProductLibItem;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.ProductLibMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.vo.ItemMatchVO;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目分解服务单元测试（T-401，AGENTS 4.3）。
 *
 * <p>覆盖：套库命中/未命中/多候选、明细快照字段映射、覆盖式保存校验
 * （项次唯一、判定类型白名单、S20 状态前置）、分解确认的
 * 「无明细拒绝 + 状态机白名单 + 乐观 UPDATE 影响行数」。</p>
 */
class ItemServiceImplTest {

    private static final Long SAMPLE_ID = 1L;

    private SampleMapper sampleMapper;
    private ProductLibMapper productLibMapper;
    private ProductLibItemMapper productLibItemMapper;
    private SampleItemMapper sampleItemMapper;

    private ItemServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 让 MP 的 LambdaQueryWrapper 能解析实体列名（无 Spring 容器时的必要初始化）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, SampleItem.class);
        TableInfoHelper.initTableInfo(assistant, ProductLib.class);
        TableInfoHelper.initTableInfo(assistant, ProductLibItem.class);
    }

    @BeforeEach
    void setUp() {
        sampleMapper = mock(SampleMapper.class);
        productLibMapper = mock(ProductLibMapper.class);
        productLibItemMapper = mock(ProductLibItemMapper.class);
        sampleItemMapper = mock(SampleItemMapper.class);
        service = new ItemServiceImpl(sampleMapper, productLibMapper, productLibItemMapper);
        // 替换 ServiceImpl 的 baseMapper（受保护字段，测试内可见同包不可用 → 用反射）
        try {
            java.lang.reflect.Field f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                    .getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(service, sampleItemMapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入 baseMapper", e);
        }
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

    private ProductLib lib(Long id, String name) {
        ProductLib l = new ProductLib();
        l.setId(id);
        l.setProductCode("nagw001");
        l.setProductName(name);
        l.setCategory("谷物");
        return l;
    }

    private ProductLibItem libItem(Long id, int order, String name) {
        ProductLibItem i = new ProductLibItem();
        i.setId(id);
        i.setProductLibId(7L);
        i.setItemOrder(order);
        i.setItemName(name);
        i.setUnit("mg/kg");
        i.setBasisCode("GB 2763-2021");
        i.setMethods("GB 23200.121");
        i.setStdValue("0.5");
        i.setJudgeType(1);
        i.setIsReference(0);
        i.setLowerLimit("0.02");
        return i;
    }

    private ItemSaveDTO.Item dtoItem(int order, String name) {
        ItemSaveDTO.Item it = new ItemSaveDTO.Item();
        it.setItemOrder(order);
        it.setItemName(name);
        it.setJudgeType(1);
        it.setIsReference(0);
        it.setSourceType(1);
        return it;
    }

    // ============================================================ 4.2 套库预览

    @Test
    @DisplayName("套库：命中唯一产品 → matched=true 且明细字段快照完整")
    void match_hitSingleLib() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        when(productLibMapper.selectList(any())).thenReturn(List.of(lib(7L, "花鲢")));
        when(productLibItemMapper.selectList(any()))
                .thenReturn(List.of(libItem(120L, 1, "铅（以Pb计）"), libItem(121L, 2, "镉（以Cd计）")));

        ItemMatchVO vo = service.match(SAMPLE_ID);

        assertAll(
                () -> assertTrue(vo.getMatched(), "应匹配成功"),
                () -> assertEquals(7L, vo.getMatchedLibId()),
                () -> assertEquals("花鲢", vo.getMatchedProductName()),
                () -> assertTrue(vo.getCandidates().isEmpty(), "唯一命中时无候选列表"),
                () -> assertEquals(2, vo.getItems().size()),
                () -> assertEquals(1, vo.getItems().get(0).getItemOrder()),
                () -> assertEquals("铅（以Pb计）", vo.getItems().get(0).getItemName()),
                () -> assertEquals(120L, vo.getItems().get(0).getLibItemId(), "应带标准库明细ID"),
                () -> assertEquals("GB 2763-2021", vo.getItems().get(0).getBasisCode(), "判定依据应下沉"),
                () -> assertEquals("0.5", vo.getItems().get(0).getStdValue(), "标准值应下沉"),
                () -> assertEquals(1, vo.getItems().get(0).getJudgeType()),
                () -> assertEquals(0, vo.getItems().get(0).getIsReference()),
                () -> assertEquals("0.02", vo.getItems().get(0).getLowerLimit(), "检出限应下沉")
        );
    }

    @Test
    @DisplayName("套库：无匹配产品 → matched=false、明细为空且不报错")
    void match_noLib() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        when(productLibMapper.selectList(any())).thenReturn(List.of());

        ItemMatchVO vo = service.match(SAMPLE_ID);

        assertAll(
                () -> assertFalse(vo.getMatched()),
                () -> assertEquals("花鲢", vo.getSampleName(), "未命中仍应回传样品名供前端提示"),
                () -> assertTrue(vo.getItems().isEmpty()),
                () -> assertTrue(vo.getCandidates().isEmpty())
        );
        // 未命中不应去查明细表
        verify(productLibItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("套库：命中多条 → 取 id 最小者并返回全部候选")
    void match_multipleLibs() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        when(productLibMapper.selectList(any()))
                .thenReturn(List.of(lib(7L, "花鲢"), lib(9L, "花鲢（淡水）")));
        when(productLibItemMapper.selectList(any())).thenReturn(new ArrayList<>());

        ItemMatchVO vo = service.match(SAMPLE_ID);

        assertAll(
                () -> assertTrue(vo.getMatched()),
                () -> assertEquals(7L, vo.getMatchedLibId(), "应取 id 最小者"),
                () -> assertEquals(2, vo.getCandidates().size(), "候选应全部返回"),
                () -> assertEquals(9L, vo.getCandidates().get(1).getLibId())
        );
    }

    @Test
    @DisplayName("套库：样品名为空 → matched=false 且不查标准库")
    void match_blankSampleName() {
        Sample s = sample(SampleStatus.S20);
        s.setSampleName("  ");
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);

        ItemMatchVO vo = service.match(SAMPLE_ID);

        assertFalse(vo.getMatched());
        verify(productLibMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("套库：样品不存在 → 抛 BizException(400)")
    void match_sampleNotFound() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.match(SAMPLE_ID));
        assertEquals(400, ex.getCode());
    }

    // ============================================================ 4.4 保存分解

    @Test
    @DisplayName("保存：合法输入 → 先逻辑删除旧明细再逐条插入，sampleNo 由样品带入")
    void saveDecompose_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));

        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setItems(List.of(dtoItem(1, "铅（以Pb计）"), dtoItem(2, "镉（以Cd计）")));

        int count = service.saveDecompose(dto);

        assertEquals(2, count);
        // 覆盖式：先删后插
        verify(sampleItemMapper, times(1)).delete(any());
        ArgumentCaptor<SampleItem> captor = ArgumentCaptor.forClass(SampleItem.class);
        verify(sampleItemMapper, times(2)).insert(captor.capture());

        List<SampleItem> inserted = captor.getAllValues();
        assertAll(
                () -> assertEquals(SAMPLE_ID, inserted.get(0).getSampleId()),
                () -> assertEquals("JK(2023)-SA-001", inserted.get(0).getSampleNo(), "样品编号应冗余写入"),
                () -> assertEquals(1, inserted.get(0).getItemOrder()),
                () -> assertEquals(2, inserted.get(1).getItemOrder())
        );
    }

    @Test
    @DisplayName("保存：项次重复 → 抛 BizException 且不写库")
    void saveDecompose_duplicateOrder() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));

        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setItems(List.of(dtoItem(1, "铅"), dtoItem(1, "镉")));

        BizException ex = assertThrows(BizException.class, () -> service.saveDecompose(dto));
        assertTrue(ex.getMessage().contains("项次重复"), "错误信息应指明项次重复");
        verify(sampleItemMapper, never()).delete(any());
        verify(sampleItemMapper, never()).insert(any(SampleItem.class));
    }

    @Test
    @DisplayName("保存：判定类型越界（4）→ 抛 BizException")
    void saveDecompose_invalidJudgeType() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));

        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        ItemSaveDTO.Item bad = dtoItem(1, "铅");
        bad.setJudgeType(4);
        dto.setItems(List.of(bad));

        BizException ex = assertThrows(BizException.class, () -> service.saveDecompose(dto));
        assertTrue(ex.getMessage().contains("判定类型非法"));
    }

    @Test
    @DisplayName("保存：样品非 S20（已分解 S30）→ 拒绝")
    void saveDecompose_wrongStatus() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S30));

        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setItems(List.of(dtoItem(1, "铅")));

        BizException ex = assertThrows(BizException.class, () -> service.saveDecompose(dto));
        assertTrue(ex.getMessage().contains("不允许保存分解"), "应说明状态不允许该操作");
    }

    @Test
    @DisplayName("保存：明细为空 → 拒绝")
    void saveDecompose_emptyItems() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));

        ItemSaveDTO dto = new ItemSaveDTO();
        dto.setSampleId(SAMPLE_ID);
        dto.setItems(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.saveDecompose(dto));
        assertTrue(ex.getMessage().contains("至少保留一个检验项目"));
    }

    // ============================================================ 4.5 分解确认

    @Test
    @DisplayName("确认：S20 且有明细 → 乐观 UPDATE 成功，返回 S30")
    void confirm_ok() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        when(sampleItemMapper.selectCount(any())).thenReturn(3L);
        when(sampleMapper.update(any(), any())).thenReturn(1);

        int status = service.confirm(SAMPLE_ID);

        assertEquals(30, status, "应返回 S30 的 code");
        verify(sampleMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("确认：无分解明细 → 拒绝（不允许空分解进入安排）")
    void confirm_noItems() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        when(sampleItemMapper.selectCount(any())).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> service.confirm(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("请先完成项目分解"));
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("确认：样品非 S20 → 拒绝，不触发 UPDATE")
    void confirm_wrongStatus() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S10));

        BizException ex = assertThrows(BizException.class, () -> service.confirm(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("不允许分解确认"));
        verify(sampleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("确认：并发导致 UPDATE 影响 0 行 → 抛 BizException（防双击跳态）")
    void confirm_concurrentConflict() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(sample(SampleStatus.S20));
        when(sampleItemMapper.selectCount(any())).thenReturn(2L);
        when(sampleMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.confirm(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("状态已变更"), "应提示并发冲突");
    }
}
