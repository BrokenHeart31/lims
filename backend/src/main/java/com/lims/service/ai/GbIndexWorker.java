package com.lims.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.entity.GbClause;
import com.lims.entity.GbDocument;
import com.lims.entity.GbImportJob;
import com.lims.mapper.GbClauseMapper;
import com.lims.mapper.GbDocumentMapper;
import com.lims.mapper.GbImportJobMapper;
import com.lims.service.ai.parser.DocumentParser;
import com.lims.service.ai.parser.ParsedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * GB 导入后台执行器（feature A，T03，设计 §6.2）。
 *
 * <p>{@code @Async} 独立于请求线程：导入接口立即返回 jobId，本器在 {@code aiTaskExecutor} 上
 * **逐文件**解析→切块→落索引，并**每处理完一个文件就回写进度**（前端轮询看到的是真实计数，
 * 禁假进度）。</p>
 *
 * <p><b>幂等</b>：以文件内容 sha256 命中 {@code gb_document} 唯一键则**跳过**，同文件重复导入
 * 不产生重复库（A-04）。</p>
 *
 * <p><b>失败不中断</b>：单个文件失败只累计 {@code fail_count} 与 {@code error_msg}，
 * 继续处理同批其它文件；全部失败才把 job 置为 FAILED（部分成功置 DONE + 失败明细）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbIndexWorker {

    /** error_msg 列长 1000，超出截断（保留最近失败，足够人工定位）。 */
    private static final int ERROR_MSG_MAX = 990;

    private final GbImportJobMapper jobMapper;
    private final GbDocumentMapper documentMapper;
    private final GbClauseMapper clauseMapper;
    private final GbStandardsStore store;
    private final ChunkSplitter chunkSplitter;
    private final List<DocumentParser> parsers;

    /**
     * 执行一次导入任务（异步）。
     *
     * @param jobId              任务ID
     * @param operator           发起导入的操作人（异步线程无 SecurityContext，显式承接）
     * @param sourceTypeOverride 来源类型覆盖（可空；空则按扩展名自动判定）
     * @param ocrDerivedOverride 是否扫描件 OCR 来源覆盖（可空；空则按文件名 {@code .ocr.txt} 自动判定）
     */
    @Async("aiTaskExecutor")
    public void run(Long jobId, String operator, Integer sourceTypeOverride, Boolean ocrDerivedOverride) {
        GbImportJob job = jobMapper.selectById(jobId);
        if (job == null) {
            log.warn("[ai-kb] 导入任务不存在：{}", jobId);
            return;
        }
        job.setStatus(GbImportJob.STATUS_PARSING);
        job.setStartedAt(LocalDateTime.now());
        jobMapper.updateById(job);

        List<Path> files = store.listImportable(job.getFilePath());
        job.setTotalFiles(files.size());
        jobMapper.updateById(job);

        int doneFiles = 0;
        int totalClauses = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                GbStandardsStore.StoredFile stored = store.load(file);
                int clauses = indexOne(stored, sourceTypeOverride, ocrDerivedOverride, operator);
                if (clauses < 0) {
                    // 幂等跳过（返回 -1）
                    log.info("[ai-kb] 跳过已入库文件（checksum 命中）：{}", name);
                } else {
                    totalClauses += clauses;
                }
                doneFiles++;
            } catch (Exception e) {
                failCount++;
                errors.add(name + "：" + rootMessage(e));
                log.warn("[ai-kb] 导入失败：{} - {}", name, e.getMessage());
            }
            // 逐文件回写进度（真实计数）
            job.setDoneFiles(doneFiles);
            job.setTotalClauses(totalClauses);
            job.setDoneClauses(totalClauses);
            job.setFailCount(failCount);
            job.setErrorMsg(truncate(String.join("；", errors)));
            jobMapper.updateById(job);
        }

        // 终态：全部文件失败 → FAILED；否则 DONE（含部分失败的情形，失败明细在 error_msg）
        boolean allFailed = !files.isEmpty() && doneFiles == 0 && failCount > 0;
        job.setStatus(allFailed ? GbImportJob.STATUS_FAILED : GbImportJob.STATUS_DONE);
        job.setFinishedAt(LocalDateTime.now());
        job.setErrorMsg(truncate(String.join("；", errors)));
        jobMapper.updateById(job);
        log.info("[ai-kb] 导入任务 {} 结束：文件 {}/{}，条款 {}，失败 {}",
                jobId, doneFiles, files.size(), totalClauses, failCount);
    }

    /**
     * 处理单个文件。
     *
     * @return 建立的条款数；幂等跳过返回 -1
     */
    private int indexOne(GbStandardsStore.StoredFile stored, Integer sourceTypeOverride,
                         Boolean ocrDerivedOverride, String operator) {
        String checksum = store.sha256(stored.bytes());
        GbDocument existing = documentMapper.selectOne(new LambdaQueryWrapper<GbDocument>()
                .eq(GbDocument::getChecksum, checksum)
                .last("LIMIT 1"));
        if (existing != null) {
            return -1;
        }
        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(stored.ext()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无解析器支持扩展名：" + stored.ext()));
        ParsedDocument parsed = parser.parse(stored.fileName(), stored.bytes());
        String text = parsed.text() == null ? "" : parsed.text().strip();
        if (text.length() < 10) {
            // fail-loud：抽不出正文（多为扫描版/图片型 PDF 未 OCR，或空文件）——明确报错而非静默入库
            throw new IllegalStateException("抽取正文为空或过短（可能是图片型/扫描版文件，需先转文本）");
        }
        List<ChunkSplitter.Chunk> chunks = chunkSplitter.split(text);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("切块结果为空");
        }
        int sourceType = sourceTypeOverride != null
                ? sourceTypeOverride
                : (stored.sourceType() > 0 ? stored.sourceType() : GbStandardsStore.sourceTypeOf(stored.ext()));

        // 扫描件 OCR 来源判定：显式覆盖优先，否则按文件名后缀 .ocr.txt 自动判定（设计 §4.6）
        boolean ocrDerived = ocrDerivedOverride != null
                ? ocrDerivedOverride
                : stored.fileName().toLowerCase().endsWith(".ocr.txt");
        if (ocrDerived) {
            // 扫描件来源统一标 SOURCE_OCR(5)，前端据此渲染「扫描件OCR · 可能有识别误差」标签
            sourceType = GbDocument.SOURCE_OCR;
        }

        GbDocument doc = new GbDocument();
        doc.setStdNo(parsed.stdNo() != null ? parsed.stdNo() : "未识别");
        doc.setStdTitle(parsed.stdTitle());
        doc.setSourceFile(stored.fileName());
        doc.setSourceType(sourceType);
        doc.setOcrDerived(ocrDerived ? 1 : 0);
        doc.setChecksum(checksum);
        doc.setClauseCount(chunks.size());
        doc.setStatus(GbDocument.STATUS_DONE);
        // 审计人显式承接（@Async 线程无 SecurityContext；不清空则落 system，丢失「谁导的」）
        doc.setCreatedBy(operator);
        doc.setUpdatedBy(operator);
        documentMapper.insert(doc);

        List<GbClause> clauses = new ArrayList<>(chunks.size());
        for (ChunkSplitter.Chunk c : chunks) {
            GbClause clause = new GbClause();
            clause.setDocumentId(doc.getId());
            clause.setStdNo(doc.getStdNo());
            clause.setClauseNo(c.clauseNo());
            clause.setClauseTitle(c.clauseTitle());
            clause.setContent(c.content());
            clause.setContentLen(c.content().length());
            clause.setPageNo(c.pageNo());
            clause.setChunkOrder(c.chunkOrder());
            clauses.add(clause);
        }
        clauseMapper.insertBatch(clauses, operator);
        return clauses.size();
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private String truncate(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() <= ERROR_MSG_MAX ? s : s.substring(0, ERROR_MSG_MAX) + "…";
    }
}
