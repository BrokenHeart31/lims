package com.lims.service.excel;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.SampleStatus;
import com.lims.dto.SampleImportDTO;
import com.lims.entity.Sample;
import com.lims.entity.SuperviseTask;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SuperviseTaskMapper;
import com.lims.vo.SampleImportResultVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采样单导入监听器单元测试（T-301，AGENTS 4.3）。
 *
 * <p>用 EasyExcel 在内存构造「2 行表头 + 数据自第 3 行」的小样本 xlsx，
 * 断言：合法行入库数、错误行定位（行号）、文件内重复拦截、任务编号校验、
 * 「以下空白」终止行忽略、落库状态为 S10、日期按 yyyy.M.d 解析。</p>
 */
class SampleImportListenerTest {

    private static final int COL_COUNT = 22;
    private static final String[] HEADERS = {
            "样品编号", "样品名称", "受检单位", "抽样地址", "收款人", "费用", "样品数量", "项目名称",
            "日期", "备注", "采样者", "生产单位", "抽样基数", "样品状态", "规格型号", "商标", "样品等级",
            "原编号或生产日期", "检验类别", "要求完成日期", "任务编号", "任务批号"
    };
    private static final String VALID_TASK_NO = "RW-SA-20260901";

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 单测无 Spring 上下文，手动初始化 MP 表信息，保证 LambdaQueryWrapper 可解析列名
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, SuperviseTask.class);
    }

    @Test
    @DisplayName("导入：合法行入库 S10，错误行逐条报行号，重复/非法任务号/缺名被拦截，终止行忽略")
    void shouldImportValidRowsAndCollectRowLevelErrors() {
        List<List<String>> data = new ArrayList<>();
        data.add(row("JK(2026)-SA-001", "花鲢", "300", "2026.9.12", VALID_TASK_NO));   // 行3 合法
        data.add(row("JK(2026)-SA-001", "河蟹", "200", "2026.9.12", VALID_TASK_NO));   // 行4 文件内重复编号
        data.add(row("JK(2026)-SA-002", "", "150", "2026.9.12", VALID_TASK_NO));       // 行5 缺样品名称
        data.add(row("JK(2026)-SA-003", "草鱼", "180", "2026.9.12", "RW-XX-999"));     // 行6 任务编号不存在
        data.add(terminatorRow());                                                     // 行7 「以下空白」忽略

        SampleImportResultVO result = doImportWithMocks(data);

        assertAll(
                () -> assertEquals(4, result.getTotal(), "以下空白行不应计入统计"),
                () -> assertEquals(1, result.getSuccessCount()),
                () -> assertEquals(3, result.getFailCount()),
                () -> assertEquals(3, result.getErrors().size())
        );

        List<SampleImportResultVO.ErrorRow> errors = result.getErrors();
        assertAll(
                () -> assertEquals(4, errors.get(0).getRowNum()),
                () -> assertTrue(errors.get(0).getMessage().contains("本文件内重复")),
                () -> assertEquals(5, errors.get(1).getRowNum()),
                () -> assertTrue(errors.get(1).getMessage().contains("样品名称不能为空")),
                () -> assertEquals(6, errors.get(2).getRowNum()),
                () -> assertTrue(errors.get(2).getMessage().contains("任务编号不存在"))
        );
    }

    @Test
    @DisplayName("导入：合法行落库字段正确——状态 S10、日期解析为 2026-09-12、费用 300")
    void shouldPersistValidRowWithStatusS10AndParsedDate() {
        List<List<String>> data = new ArrayList<>();
        data.add(row("JK(2026)-SA-009", "鲫鱼", "300", "2026.9.12", VALID_TASK_NO));

        SampleMapper sampleMapper = mock(SampleMapper.class);
        when(sampleMapper.selectList(any())).thenReturn(List.of());
        when(sampleMapper.insert(any(Sample.class))).thenReturn(1);
        SuperviseTaskMapper taskMapper = mockTaskMapper();

        SampleImportListener listener = new SampleImportListener(sampleMapper, taskMapper);
        EasyExcel.read(new ByteArrayInputStream(buildXlsx(data)), SampleImportDTO.class, listener)
                .headRowNumber(2).sheet().doRead();

        ArgumentCaptor<Sample> captor = ArgumentCaptor.forClass(Sample.class);
        verify(sampleMapper, times(1)).insert(captor.capture());
        Sample saved = captor.getValue();

        assertAll(
                () -> assertEquals("JK(2026)-SA-009", saved.getSampleNo()),
                () -> assertEquals("鲫鱼", saved.getSampleName()),
                () -> assertEquals(SampleStatus.S10, saved.getStatus()),
                () -> assertEquals(LocalDate.of(2026, 9, 12), saved.getSamplingDate()),
                () -> assertEquals(0, saved.getFee().compareTo(new java.math.BigDecimal("300"))),
                () -> assertEquals(VALID_TASK_NO, saved.getTaskNo())
        );
    }

    // ------------------------------------------------------------------ helpers

    private SampleImportResultVO doImportWithMocks(List<List<String>> data) {
        SampleMapper sampleMapper = mock(SampleMapper.class);
        when(sampleMapper.selectList(any())).thenReturn(List.of());
        when(sampleMapper.insert(any(Sample.class))).thenReturn(1);
        SuperviseTaskMapper taskMapper = mockTaskMapper();

        SampleImportListener listener = new SampleImportListener(sampleMapper, taskMapper);
        EasyExcel.read(new ByteArrayInputStream(buildXlsx(data)), SampleImportDTO.class, listener)
                .headRowNumber(2).sheet().doRead();
        return listener.getResult();
    }

    private SuperviseTaskMapper mockTaskMapper() {
        SuperviseTask task = new SuperviseTask();
        task.setTaskNo(VALID_TASK_NO);
        SuperviseTaskMapper taskMapper = mock(SuperviseTaskMapper.class);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        return taskMapper;
    }

    /** 构造 22 列数据行（缺失列补空串，保持列序与列头一致） */
    private static List<String> row(String sampleNo, String sampleName, String fee,
                                    String samplingDate, String taskNo) {
        List<String> r = new ArrayList<>(COL_COUNT);
        for (int i = 0; i < COL_COUNT; i++) {
            r.add("");
        }
        r.set(0, sampleNo);
        r.set(1, sampleName);
        r.set(5, fee);
        r.set(8, samplingDate);
        r.set(20, taskNo);
        return r;
    }

    private static List<String> terminatorRow() {
        List<String> r = new ArrayList<>(COL_COUNT);
        for (int i = 0; i < COL_COUNT; i++) {
            r.add("");
        }
        r.set(0, "以下空白");
        return r;
    }

    /** 2 行表头（第1行=A1文件标记行，第2行=列头）+ 数据，数据自第 3 行开始 */
    private static byte[] buildXlsx(List<List<String>> data) {
        // 注意：EasyExcel 的 head 结构为「外层=列，内层=该列的各表头行」，
        // 故此处按列构造：每列 2 个表头行（第 1 行仅首列放文件标记，第 2 行放列名）
        List<List<String>> head = new ArrayList<>(COL_COUNT);
        for (int c = 0; c < COL_COUNT; c++) {
            head.add(List.of(c == 0 ? "UNIT-TEST-MARKER" : "", HEADERS[c]));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EasyExcel.write(baos).head(head).sheet("sheet1").doWrite(data);
        return baos.toByteArray();
    }
}
