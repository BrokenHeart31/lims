package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /** 开始时间（F28：LocalDateTime 必须显式 @JsonFormat，否则输出 ISO-8601 与其余 VO 不一致） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /** 完成时间（F28：同上） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
