package com.lims.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * GB 标准文档（已入库列表项，feature A，T03 / api-spec A11）。
 */
@Data
public class GbDocumentVO {

    private Long id;

    private String stdNo;

    private String stdTitle;

    private String sourceFile;

    /** 来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR */
    private Integer sourceType;

    /** 来源类型中文名 */
    private String sourceTypeLabel;

    /** 是否来自扫描件 OCR 0=否 1=是 */
    private Integer ocrDerived;

    /** 来源可信度中文名（文本版 / 扫描件OCR） */
    private String ocrDerivedLabel;

    private String checksum;

    private Integer clauseCount;

    /** 状态 1=已完成 2=已失效 */
    private Integer status;

    /** 状态中文名 */
    private String statusLabel;

    private LocalDateTime createdAt;
}
