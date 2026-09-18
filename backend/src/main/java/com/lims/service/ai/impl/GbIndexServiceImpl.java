package com.lims.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.exception.BizException;
import com.lims.entity.GbDocument;
import com.lims.entity.GbImportJob;
import com.lims.mapper.GbClauseMapper;
import com.lims.mapper.GbDocumentMapper;
import com.lims.mapper.GbImportJobMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.ai.GbIndexService;
import com.lims.service.ai.GbIndexWorker;
import com.lims.service.ai.GbStandardsStore;
import com.lims.vo.GbDocumentVO;
import com.lims.vo.GbImportJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * GB 索引服务实现（feature A，T03）。
 *
 * <p><b>只写 gb_* 表</b>（AI 域硬约束）：导入通道不碰任何业务表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbIndexServiceImpl implements GbIndexService {

    private final GbImportJobMapper jobMapper;
    private final GbDocumentMapper documentMapper;
    private final GbClauseMapper clauseMapper;
    private final GbStandardsStore store;
    private final GbIndexWorker worker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importUpload(MultipartFile file, Integer sourceType) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "上传文件为空");
        }
        // saveUpload 内做 PDF(4211/4212) 与类型校验，并落盘到 standards-dir/parsed/
        GbStandardsStore.StoredFile stored = store.saveUpload(file);
        GbImportJob job = newJob(stored.fileName(), stored.path().toString(), 1);
        jobMapper.insert(job);
        Long jobId = job.getId();
        // 异步执行（接口立即返回 jobId）；单文件上传按 .ocr.txt 后缀自动判定是否 OCR 来源
        dispatchAfterCommit(jobId, operator(), sourceType, isOcrDerivedName(stored.fileName(), null));
        return jobId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importScanDir(String dir, Integer sourceType, Boolean ocrDerived) {
        Path resolved = store.resolveDir(dir);
        List<Path> files = store.listImportable(resolved.toString());
        if (files.isEmpty()) {
            throw new BizException(400, "指定目录下没有可导入的文本文件（仅支持 txt/html/htm/md/csv/tsv）：" + resolved);
        }
        GbImportJob job = newJob("scan:" + resolved, resolved.toString(), files.size());
        jobMapper.insert(job);
        Long jobId = job.getId();
        dispatchAfterCommit(jobId, operator(), sourceType, ocrDerived);
        return jobId;
    }

    /**
     * 在**当前事务提交之后**再投递异步建索引任务。
     *
     * <p><b>为什么必须这样（实测踩坑）</b>：{@code worker.run(...)} 是 {@code @Async}，
     * 会在**另一个线程**上立刻读取 {@code gb_import_job} 的那一行；而调用方处于事务中、
     * {@code insert} 尚未提交，异步线程以新事务读不到该行 → 直接以
     * 「导入任务不存在」WARN 退出。外部表现是**任务永远停在「待处理」**、库里
     * {@code gb_document}/{@code gb_clause} 一行不增，且没有任何错误提示。
     * （实测：job id=1 停在 status=0 十几分钟，日志只有一条 WARN。）</p>
     *
     * <p>故：有活跃事务 → 注册 {@code afterCommit} 回调；无事务 → 直接投递。</p>
     */
    private void dispatchAfterCommit(Long jobId, String operator, Integer sourceType, Boolean ocrDerived) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.run(jobId, operator, sourceType, ocrDerived);
                }
            });
        } else {
            worker.run(jobId, operator, sourceType, ocrDerived);
        }
    }

    /** 是否扫描件 OCR 来源：显式指定优先，否则按文件名后缀 {@code .ocr.txt} 自动判定。 */
    private Boolean isOcrDerivedName(String fileName, Boolean override) {
        if (override != null) {
            return override;
        }
        return fileName != null && fileName.toLowerCase().endsWith(".ocr.txt");
    }

    @Override
    public PageResult<GbImportJobVO> pageJobs(long current, long size) {
        Page<GbImportJob> page = jobMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<GbImportJob>().orderByDesc(GbImportJob::getId));
        return PageResult.of(page, this::toJobVO);
    }

    @Override
    public GbImportJobVO detail(Long jobId) {
        GbImportJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(400, "导入任务不存在: id=" + jobId);
        }
        return toJobVO(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean retry(Long jobId) {
        GbImportJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(400, "导入任务不存在: id=" + jobId);
        }
        // 重置进度（保留 filePath / fileName 以便重新推导文件清单）
        job.setStatus(GbImportJob.STATUS_PENDING);
        job.setDoneFiles(0);
        job.setTotalClauses(0);
        job.setDoneClauses(0);
        job.setFailCount(0);
        job.setErrorMsg(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        jobMapper.updateById(job);
        dispatchAfterCommit(jobId, operator(), null, null);
        return true;
    }

    @Override
    public PageResult<GbDocumentVO> pageDocuments(long current, long size, String stdNo) {
        Page<GbDocument> page = documentMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<GbDocument>()
                        .like(StringUtils.hasText(stdNo), GbDocument::getStdNo, stdNo)
                        .orderByDesc(GbDocument::getId));
        return PageResult.of(page, this::toDocVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(Long documentId) {
        GbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BizException(400, "标准文档不存在: id=" + documentId);
        }
        // 物理重建（设计 §2.10）：先清条款，再删文档（避免 checksum 唯一键残留）
        int clauses = clauseMapper.deleteByDocumentId(documentId);
        documentMapper.deletePhysicallyById(documentId);
        log.info("[ai-kb] 删除标准文档 {}（{}）：清理条款 {} 条", documentId, doc.getStdNo(), clauses);
        return true;
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private GbImportJob newJob(String fileName, String filePath, int totalFiles) {
        GbImportJob job = new GbImportJob();
        job.setFileName(truncateName(fileName));
        job.setFilePath(filePath);
        job.setStatus(GbImportJob.STATUS_PENDING);
        job.setTotalFiles(totalFiles);
        job.setDoneFiles(0);
        job.setTotalClauses(0);
        job.setDoneClauses(0);
        job.setFailCount(0);
        return job;
    }

    /** file_name 列长 255。 */
    private String truncateName(String name) {
        if (name == null) {
            return null;
        }
        return name.length() <= 255 ? name : name.substring(0, 255);
    }

    private String operator() {
        return SecurityUtils.getUsername().orElse("system");
    }

    private GbImportJobVO toJobVO(GbImportJob j) {
        GbImportJobVO vo = new GbImportJobVO();
        vo.setId(j.getId());
        vo.setFileName(j.getFileName());
        vo.setFilePath(j.getFilePath());
        vo.setStatus(j.getStatus());
        vo.setStatusLabel(j.getStatusLabel());
        vo.setTotalFiles(j.getTotalFiles());
        vo.setDoneFiles(j.getDoneFiles());
        vo.setTotalClauses(j.getTotalClauses());
        vo.setDoneClauses(j.getDoneClauses());
        vo.setFailCount(j.getFailCount());
        vo.setErrorMsg(j.getErrorMsg());
        vo.setStartedAt(j.getStartedAt());
        vo.setFinishedAt(j.getFinishedAt());
        return vo;
    }

    private GbDocumentVO toDocVO(GbDocument d) {
        GbDocumentVO vo = new GbDocumentVO();
        vo.setId(d.getId());
        vo.setStdNo(d.getStdNo());
        vo.setStdTitle(d.getStdTitle());
        vo.setSourceFile(d.getSourceFile());
        vo.setSourceType(d.getSourceType());
        vo.setSourceTypeLabel(d.getSourceTypeLabel());
        vo.setChecksum(d.getChecksum());
        vo.setClauseCount(d.getClauseCount());
        vo.setStatus(d.getStatus());
        vo.setStatusLabel(d.getStatusLabel());
        vo.setCreatedAt(d.getCreatedAt());
        return vo;
    }
}
