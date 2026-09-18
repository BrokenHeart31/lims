package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import com.lims.dto.KbScanDTO;
import com.lims.dto.KbSearchDTO;
import com.lims.service.ai.GbIndexService;
import com.lims.service.ai.GbRetriever;
import com.lims.service.ai.scan.ScanOcrJobStore;
import com.lims.vo.GbDocumentVO;
import com.lims.vo.GbImportJobVO;
import com.lims.vo.GbSearchHitVO;
import com.lims.vo.ScanOcrJobVO;
import com.lims.vo.ScanOcrRetryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * GB 标准库导入与检索接口（api-spec 第 16 章 /api/ai/kb，feature A，T03）。
 *
 * <p>权限标识与 seed {@code sys_menu}(id=121/122) 一致：
 * <ul>
 *   <li>{@code ai:kb:import}（121）：上传/扫描/任务查询/重试/删除文档；</li>
 *   <li>{@code ai:kb:query}（122）：已入库文档查询、直接检索。</li>
 * </ul>
 * 逐条核对 seed：121/122/123/124 均在 {@code db/seed/01_rbac_seed.sql} 已定义，本批未新增权限点。</p>
 */
@Validated
@RestController
@RequestMapping("/ai/kb")
@RequiredArgsConstructor
public class AiKbController {

    private final GbIndexService gbIndexService;
    private final GbRetriever gbRetriever;
    private final ScanOcrJobStore scanOcrJobStore;

    /** A6 上传文本文件（txt/html/htm/md/csv）→ 异步建索引；PDF 抛 4211。 */
    @PostMapping("/import/upload")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<Long> upload(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "sourceType", required = false) Integer sourceType) {
        return R.ok(gbIndexService.importUpload(file, sourceType));
    }

    /** A7 扫描目录（默认 parsed/）→ 异步批量建索引。 */
    @PostMapping("/import/scan")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<Long> scan(@Valid @RequestBody(required = false) KbScanDTO dto) {
        KbScanDTO scan = dto == null ? new KbScanDTO() : dto;
        return R.ok(gbIndexService.importScanDir(scan.getDir(), scan.getSourceType(), scan.getOcrDerived()));
    }

    /** A8 分页查询导入任务与进度。 */
    @GetMapping("/import/jobs")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<PageResult<GbImportJobVO>> jobs(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size) {
        return R.ok(gbIndexService.pageJobs(current, size));
    }

    /** A9 单任务详情（含失败明细）。 */
    @GetMapping("/import/jobs/{id}")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<GbImportJobVO> jobDetail(@PathVariable Long id) {
        return R.ok(gbIndexService.detail(id));
    }

    /** A10 失败重试。 */
    @PostMapping("/import/jobs/{id}/retry")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<Boolean> retry(@PathVariable Long id) {
        return R.ok(gbIndexService.retry(id));
    }

    /** A11 分页查询已入库标准。 */
    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('ai:kb:query')")
    public R<PageResult<GbDocumentVO>> documents(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @RequestParam(required = false) String stdNo) {
        return R.ok(gbIndexService.pageDocuments(current, size, stdNo));
    }

    /** A12 直接检索标准条款（页面联动/调试）。 */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('ai:kb:query')")
    public R<List<GbSearchHitVO>> search(@Valid @RequestBody KbSearchDTO dto) {
        int topN = dto.getTopN() == null ? 5 : dto.getTopN();
        return R.ok(gbRetriever.search(dto.getQuery(), topN, dto.getStdNo()));
    }

    /** A13 删除文档索引（物理重建，可重导）。 */
    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<Boolean> deleteDocument(@PathVariable Long id) {
        return R.ok(gbIndexService.deleteDocument(id));
    }

    // =====================================================================
    // 扫描件 OCR 通道（feature 增量 ai_flow_assistant，A17/A18/A19）
    // =====================================================================

    /** A17 扫描件 OCR 任务列表（读侧车位，页级进度；不占 Web 线程）。 */
    @GetMapping("/scan/jobs")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<List<ScanOcrJobVO>> scanJobs() {
        return R.ok(scanOcrJobStore.list());
    }

    /** A18 单个 OCR 任务详情（逐页进度 + 失败页清单）；不存在抛 4204。 */
    @GetMapping("/scan/jobs/{stdKey}")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<ScanOcrJobVO> scanJob(@PathVariable String stdKey) {
        return R.ok(scanOcrJobStore.get(stdKey)
                .orElseThrow(() -> new BizException(ResultCode.AI_OCR_JOB_NOT_FOUND)));
    }

    /** A19 复位失败页为待跑 + 返回**待执行命令**（**不代跑** OCR）。 */
    @PostMapping("/scan/jobs/{stdKey}/retry")
    @PreAuthorize("hasAuthority('ai:kb:import')")
    public R<ScanOcrRetryVO> retryScanJob(@PathVariable String stdKey) {
        return R.ok(scanOcrJobStore.resetFailedToPending(stdKey));
    }
}
