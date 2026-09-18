package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * GB 标准文档（{@code gb_document}，feature A，T03）。
 *
 * <p>导入通道产物：一个文件（TXT/MD/HTML/CSV，PDF 须先预处理）→ 一行文档 + N 行条款块。</p>
 *
 * <p><b>幂等键 = {@code checksum}（文件内容 sha256）</b>：同文件重复导入命中唯一键则跳过，
 * 不产生重复库（A-04 硬要求）。</p>
 *
 * <p><b>可物理重建</b>（设计 §2.10）：本表与其条款是可由原始文件随时重建的**派生检索索引**，
 * 不适用「历史不消失」约束；换版标准时物理删除重建，不做软删累积。</p>
 *
 * <p>审计四字段（含 {@code created_at}）继承自 {@link BaseEntity}，业务代码不手工赋值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gb_document")
public class GbDocument extends BaseEntity {

    /** 来源类型：TXT */
    public static final int SOURCE_TXT = 1;
    /** 来源类型：HTML */
    public static final int SOURCE_HTML = 2;
    /** 来源类型：Markdown */
    public static final int SOURCE_MD = 3;
    /** 来源类型：CSV */
    public static final int SOURCE_CSV = 4;
    /** 来源类型：扫描件 OCR（feature 增量 ai_flow_assistant） */
    public static final int SOURCE_OCR = 5;

    /** 状态：已完成（可检索） */
    public static final int STATUS_DONE = 1;
    /** 状态：已失效（被新版替换，不参与检索） */
    public static final int STATUS_INVALID = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标准号（如 GB 2762-2022）；未从文件名/正文识别到时为「未识别」占位 */
    private String stdNo;

    /** 标准名称 */
    private String stdTitle;

    /** 来源文件名 */
    private String sourceFile;

    /** 来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR */
    private Integer sourceType;

    /** 是否来自扫描件 OCR 1=是（数值请以系统标准库为准）0=否（文本版） */
    private Integer ocrDerived;

    /** 文件内容 sha256（幂等导入键，唯一） */
    private String checksum;

    /** 切块数 */
    private Integer clauseCount;

    /** 状态 1=已完成 2=已失效 */
    private Integer status;

    /** 来源类型中文名（非持久化，出网供前端展示） */
    public String getSourceTypeLabel() {
        if (sourceType == null) {
            return null;
        }
        return switch (sourceType) {
            case SOURCE_TXT -> "TXT";
            case SOURCE_HTML -> "HTML";
            case SOURCE_MD -> "MD";
            case SOURCE_CSV -> "CSV";
            case SOURCE_OCR -> "扫描件OCR";
            default -> "未知";
        };
    }

    /** 来源可信度中文名（非持久化，出网供前端展示来源标签） */
    public String getOcrDerivedLabel() {
        return Integer.valueOf(1).equals(ocrDerived) ? "扫描件OCR" : "文本版";
    }

    /** 状态中文名（非持久化，出网供前端展示） */
    public String getStatusLabel() {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case STATUS_DONE -> "已完成";
            case STATUS_INVALID -> "已失效";
            default -> "未知";
        };
    }
}
