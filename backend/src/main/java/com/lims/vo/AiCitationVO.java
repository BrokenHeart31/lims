package com.lims.vo;

import lombok.Data;

/**
 * AI 回答引用（标准条款卡片，feature A，T03 / api-spec A2）。
 *
 * <p>结构化引用的意义：让「必带标准号 + 出处」成为**结构性必然**，而不是靠模型自觉——
 * 前端把每条 citation 渲染成卡片，无 citations 即无卡片，天然满足 G3/T3（设计 §2.6）。</p>
 */
@Data
public class AiCitationVO {

    /** 标准号（如 GB 2762-2022） */
    private String stdNo;

    /** 条款号（如 4.2 表3） */
    private String clauseNo;

    /** 条款标题 */
    private String clauseTitle;

    /** 命中片段（截断展示） */
    private String snippet;

    /** 来源文档ID（gb_document.id） */
    private Long docId;

    /** 来源文件名 */
    private String sourceFile;

    /** ngram 相关度得分（MATCH…AGAINST 结果） */
    private Double score;

    /** 来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR（feature 增量） */
    private Integer sourceType;

    /** 来源类型中文名（文本版 / 扫描件OCR） */
    private String sourceTypeLabel;

    /** 是否来自扫描件 OCR：true → 前端强制渲染警示标签 + 「数值请以系统标准库为准」 */
    private boolean ocrDerived;
}
