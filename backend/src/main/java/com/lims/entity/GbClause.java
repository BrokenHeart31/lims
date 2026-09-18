package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * GB 标准条款切块（{@code gb_clause}，feature A，T03）—— ngram 全文检索索引。
 *
 * <p>一个检测单项的判定依据往往只有一小段；把整份标准切成 200~500 字的块，让检索命中后
 * 投喂模型的上下文受控（设计 §2.4：块小 → 单行小 → 检索与投喂开销都受控）。</p>
 *
 * <p><b>⚠️ FULLTEXT 索引列 = {@code content} 单列</b>（DDL {@code ft_gbc_content(content) WITH PARSER ngram}）。
 * {@code MATCH(...)} 的列清单必须与本索引列**完全一致**，否则 MySQL 报 1191；因此
 * {@link com.lims.mapper.GbClauseMapper#searchByNgram} 只用 {@code MATCH(content)}，
 * 其它条件（std_no 过滤）放 WHERE 而非 MATCH 内。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gb_clause")
public class GbClause extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** gb_document.id */
    private Long documentId;

    /** 标准号（冗余，检索结果直接可用） */
    private String stdNo;

    /** 条款号（如 4.2 / 表3） */
    private String clauseNo;

    /** 条款标题 */
    private String clauseTitle;

    /** 切块正文（目标 200~500 字） */
    private String content;

    /** 正文字符数 */
    private Integer contentLen;

    /** 近似页码 */
    private Integer pageNo;

    /** 块序（文档内） */
    private Integer chunkOrder;
}
