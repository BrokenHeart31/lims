package com.lims.dto;

import lombok.Data;

/**
 * GB 目录扫描导入请求（feature A，T03 / api-spec A7）。
 *
 * <p>一键脚本 {@code import-standards.ps1} 先跑 Python 预处理（PDF→TXT）再调本接口扫描
 * {@code ai/standards/parsed/}。</p>
 */
@Data
public class KbScanDTO {

    /** 待扫描目录；留空则取 {@code lims.ai.standards-dir + /parsed} */
    private String dir;

    /** 来源类型 1=TXT 2=HTML 3=MD 4=CSV；留空按文件扩展名自动判定 */
    private Integer sourceType;

    /**
     * 是否来自扫描件 OCR（feature 增量）：
     * 未传则后端按文件名后缀 {@code .ocr.txt} 自动判定（true）。
     */
    private Boolean ocrDerived;
}
