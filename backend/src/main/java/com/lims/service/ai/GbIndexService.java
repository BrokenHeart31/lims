package com.lims.service.ai;

import com.lims.common.PageResult;
import com.lims.vo.GbDocumentVO;
import com.lims.vo.GbImportJobVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * GB 标准索引服务（feature A，T03 / api-spec A6–A13）。
 *
 * <p>导入（上传 / 扫描）→ 解析 → 切块 → 建 ngram 索引；进度与失败可查、可重导；
 * 已入库文档可查、可删（删=物理重建，见设计 §2.10）。</p>
 */
public interface GbIndexService {

    /** A6 上传单个文本文件（txt/html/htm/md/csv）→ 异步建索引，返回 jobId。PDF 抛 4211。 */
    Long importUpload(MultipartFile file, Integer sourceType);

    /**
     * A7 扫描目录（默认 parsed/）→ 异步批量建索引，返回 jobId。
     *
     * @param ocrDerived 是否来自扫描件 OCR（可空；空则按文件名后缀 {@code .ocr.txt} 自动判定）
     */
    Long importScanDir(String dir, Integer sourceType, Boolean ocrDerived);

    /** A8 分页查询导入任务与进度。 */
    PageResult<GbImportJobVO> pageJobs(long current, long size);

    /** A9 单任务详情（含失败明细）。 */
    GbImportJobVO detail(Long jobId);

    /** A10 失败重试（重置任务进度后重新导入）。 */
    boolean retry(Long jobId);

    /** A11 分页查询已入库标准。 */
    PageResult<GbDocumentVO> pageDocuments(long current, long size, String stdNo);

    /** A13 删除文档索引（物理删除文档与其条款，可重建）。 */
    boolean deleteDocument(Long documentId);
}
