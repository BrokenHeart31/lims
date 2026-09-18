package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * GB 导入任务（{@code gb_import_job}，feature A，T03）—— 进度与失败可查。
 *
 * <p>一次导入 = 一个 job（单文件上传也建 job，口径统一）。{@code @Async} 后台逐文件推进，
 * 前端每 2s 轮询 {@code GET /ai/kb/import/jobs} 看**真实计数**（禁假进度，设计 §6.2）。</p>
 *
 * <p>{@code file_path} 兼作「批次定位」：scan 任务记为待扫描目录，upload 任务记为落盘文件路径；
 * {@code retry} 据此重新推导文件清单，无需额外列。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gb_import_job")
public class GbImportJob extends BaseEntity {

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：解析中 */
    public static final int STATUS_PARSING = 1;
    /** 状态：已完成 */
    public static final int STATUS_DONE = 2;
    /** 状态：失败 */
    public static final int STATUS_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件名（或 scan 批次标记，如 {@code scan:ai/standards/parsed}） */
    private String fileName;

    /** 解析产物路径（scan 任务存目录；upload 任务存落盘文件） */
    private String filePath;

    /** 状态 0=待处理 1=解析中 2=已完成 3=失败 */
    private Integer status;

    /** 批次文件总数 */
    private Integer totalFiles;

    /** 已完成文件数 */
    private Integer doneFiles;

    /** 总切块数 */
    private Integer totalClauses;

    /** 已建索引切块数 */
    private Integer doneClauses;

    /** 失败文件数 */
    private Integer failCount;

    /** 失败明细（逐条「文件 + 原因」，截断到 1000 字防超列） */
    private String errorMsg;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    /** 状态中文名（非持久化，出网供前端展示） */
    public String getStatusLabel() {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case STATUS_PENDING -> "待处理";
            case STATUS_PARSING -> "解析中";
            case STATUS_DONE -> "已完成";
            case STATUS_FAILED -> "失败";
            default -> "未知";
        };
    }
}
