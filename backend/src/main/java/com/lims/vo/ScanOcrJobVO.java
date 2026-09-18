package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描件 OCR 任务视图（feature 增量 ai_flow_assistant，T03，设计 §3.3 / §4.5）。
 *
 * <p>后端 {@code ScanOcrJobStore} **只读**侧车位文件（{@code ai/standards/.scan/<stdKey>/progress.json}）
 * 拼装，**不代跑 OCR**。展示「第 N/总页（%）+ 失败页清单」。</p>
 */
@Data
public class ScanOcrJobVO {

    /** 归一化标准号键（如 GB-2763-2021） */
    private String stdKey;

    /** 源文件名 */
    private String sourceFile;

    /** 标准号（如 GB 2763-2021） */
    private String stdNo;

    /** 模式（ocr） */
    private String mode;

    /** 总页数 */
    private Integer totalPages;

    /** 已完成页数（= 片段文件数） */
    private Integer donePages;

    /** 失败页清单（逐页列理由，fail-loud） */
    private List<FailedPage> failedPages = new ArrayList<>();

    /** 状态：pending | running | done | partial_failed */
    private String status;

    /** 完成百分比（保留 1 位） */
    private Double percent;

    /** 产物文件名（如 GB2763-2021-ys.ocr.txt） */
    private String outputTxt;

    /** 最近更新时间 */
    private String updatedAt;

    /** 单页失败：页码 + 理由 */
    @Data
    public static class FailedPage {
        private Integer page;
        private String reason;

        public FailedPage() {
        }

        public FailedPage(Integer page, String reason) {
            this.page = page;
            this.reason = reason;
        }
    }
}
