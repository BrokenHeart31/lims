package com.lims.service.ai;

import com.lims.entity.GbDocument;
import com.lims.entity.GbImportJob;
import com.lims.mapper.GbClauseMapper;
import com.lims.mapper.GbDocumentMapper;
import com.lims.mapper.GbImportJobMapper;
import com.lims.service.ai.parser.DocumentParser;
import com.lims.service.ai.parser.TxtParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GB 导入后台执行器单元测试（feature A，T03 / A-04）。
 *
 * <p>固化：**幂等**（checksum 命中 → 跳过、不产生重复库）；新增文件 → 落文档 + 条款、
 * 进度回写真实计数；抽不出正文 → fail-loud（job 置 FAILED 且带明细）。</p>
 */
class GbIndexWorkerTest {

    @TempDir
    Path tmp;

    private GbImportJobMapper jobMapper;
    private GbDocumentMapper documentMapper;
    private GbClauseMapper clauseMapper;
    private GbStandardsStore store;
    private GbIndexWorker worker;

    @BeforeEach
    void setUp() {
        jobMapper = mock(GbImportJobMapper.class);
        documentMapper = mock(GbDocumentMapper.class);
        clauseMapper = mock(GbClauseMapper.class);
        store = mock(GbStandardsStore.class);
        List<DocumentParser> parsers = List.of(new TxtParser());
        worker = new GbIndexWorker(jobMapper, documentMapper, clauseMapper, store, new ChunkSplitter(), parsers);
    }

    private Path write(String name, String content) throws IOException {
        Path f = tmp.resolve(name);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    private GbImportJob job(String filePath) {
        GbImportJob j = new GbImportJob();
        j.setId(1L);
        j.setFileName("batch");
        j.setFilePath(filePath);
        j.setStatus(GbImportJob.STATUS_PENDING);
        return j;
    }

    private GbStandardsStore.StoredFile stored(Path f, byte[] bytes) {
        return new GbStandardsStore.StoredFile(f.getFileName().toString(), f, bytes, "txt", 1);
    }

    @Test
    @DisplayName("★幂等：checksum 已入库 → 跳过、不产生重复文档/条款，job 完成")
    void idempotentSkipWhenChecksumExists() throws IOException {
        Path f = write("GB 2762-2022.txt", "正文".repeat(200));
        byte[] bytes = Files.readAllBytes(f);
        GbImportJob j = job(f.toString());
        when(jobMapper.selectById(1L)).thenReturn(j);
        when(store.listImportable(anyString())).thenReturn(List.of(f));
        when(store.load(any())).thenReturn(stored(f, bytes));
        when(store.sha256(any())).thenReturn("dup-checksum");
        when(documentMapper.selectOne(any())).thenReturn(new GbDocument()); // 命中已入库

        worker.run(1L, "tester", null, null);

        verify(documentMapper, never()).insert(any(GbDocument.class));
        verify(clauseMapper, never()).insertBatch(any(), anyString());
        assertEquals(GbImportJob.STATUS_DONE, j.getStatus());
        assertEquals(1, j.getDoneFiles());
        assertEquals(0, j.getFailCount());
    }

    @Test
    @DisplayName("★新增文件 → 落文档 + 批量条款，且审计人显式承接为操作人")
    void indexInsertsWhenNew() throws IOException {
        Path f = write("GB 5009.11-2014.txt", "正文".repeat(200));
        byte[] bytes = Files.readAllBytes(f);
        GbImportJob j = job(f.toString());
        when(jobMapper.selectById(1L)).thenReturn(j);
        when(store.listImportable(anyString())).thenReturn(List.of(f));
        when(store.load(any())).thenReturn(stored(f, bytes));
        when(store.sha256(any())).thenReturn("new-checksum");
        when(documentMapper.selectOne(any())).thenReturn(null);
        when(documentMapper.insert(any(GbDocument.class))).thenAnswer(inv -> {
            GbDocument d = inv.getArgument(0);
            d.setId(5L);
            return 1;
        });
        when(clauseMapper.insertBatch(anyList(), anyString())).thenReturn(1);

        worker.run(1L, "tester", null, null);

        verify(documentMapper).insert(argThat((GbDocument d) -> "tester".equals(d.getCreatedBy())));
        verify(clauseMapper).insertBatch(argThat(l -> !l.isEmpty()), eq("tester"));
        assertEquals(GbImportJob.STATUS_DONE, j.getStatus());
        assertTrue(j.getTotalClauses() >= 1);
    }

    @Test
    @DisplayName("★fail-loud：抽不出正文（过短）→ job FAILED 且带失败明细")
    void failLoudOnEmptyText() throws IOException {
        Path f = write("broken.txt", "x"); // < 10 字
        byte[] bytes = Files.readAllBytes(f);
        GbImportJob j = job(f.toString());
        when(jobMapper.selectById(1L)).thenReturn(j);
        when(store.listImportable(anyString())).thenReturn(List.of(f));
        when(store.load(any())).thenReturn(stored(f, bytes));
        when(store.sha256(any())).thenReturn("sum");
        when(documentMapper.selectOne(any())).thenReturn(null);

        worker.run(1L, "tester", null, null);

        assertEquals(GbImportJob.STATUS_FAILED, j.getStatus());
        assertEquals(1, j.getFailCount());
        assertNotNull(j.getErrorMsg());
    }
}
