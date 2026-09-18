package com.lims.vo;

import lombok.Data;

/**
 * GB 检索命中项（feature A，T03 / api-spec A12）。
 *
 * <p>由 {@code GbClauseMapper.searchByNgram} 直接映射（XML 内列别名与本类字段一致）。</p>
 */
@Data
public class GbSearchHitVO {

    /** 标准号 */
    private String stdNo;

    /** 条款号 */
    private String clauseNo;

    /** 条款标题 */
    private String clauseTitle;

    /** 命中片段（条款正文） */
    private String snippet;

    /** 来源文档ID */
    private Long docId;

    /** 来源文件名 */
    private String sourceFile;

    /** ngram 相关度得分（MATCH…AGAINST） */
    private Double score;

    /** 来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR（feature 增量，用于来源标注） */
    private Integer sourceType;

    /** 是否来自扫描件 OCR（true → 引用卡片强制标注「可能有识别误差、数值以系统库为准」） */
    private boolean ocrDerived;
}
