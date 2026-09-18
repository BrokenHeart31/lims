package com.lims.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * GB 导入任务（进度/失败，feature A，T03 / api-spec A8、A9）。
 *
 * <p>所有计数均为**真实值**（禁假进度，设计 §6.2 / AGENTS 禁止 mock）。</p>
 */
@Data
public class GbImportJobVO {

    private Long id;

    private String fileName;

    private String filePath;

    /** 状态 0=待处理 1=解析中 2=已完成 3=失败 */
    private Integer status;

    /** 状态中文名 */
    private String statusLabel;

    private Integer totalFiles;

    private Integer doneFiles;

    private Integer totalClauses;

    private Integer doneClauses;

    private Integer failCount;

    /** 失败明细（逐条「文件 + 原因」） */
    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
