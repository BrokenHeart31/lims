package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.GbClause;
import com.lims.vo.GbSearchHitVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GB 标准条款 Mapper（feature A，T03）—— ngram 全文检索 + 可物理重建。
 *
 * <p><b>只查倒排索引，绝不读文件</b>（设计 §2.4）：检索走
 * {@code MATCH(content) AGAINST(? IN BOOLEAN MODE)}，由 DDL 的
 * {@code ft_gbc_content(content) WITH PARSER ngram} 支撑。</p>
 *
 * <p><b>⚠️ ngram 三个坑（实现依据，设计 §2.4）</b>：</p>
 * <ol>
 *   <li>{@code MATCH(...)} 的列清单必须与 FULLTEXT 索引列**完全一致**（这里都是 {@code content}），
 *       否则 MySQL 报 1191；故 {@code std_no} 过滤放 WHERE，不进 MATCH。</li>
 *   <li>单字（长度 1）检索词会被 ngram 忽略（分词粒度 = {@code ngram_token_size} = 2）——
 *       检索词需 ≥2 字，补足/提示逻辑在 {@code GbRetriever}。</li>
 *   <li>{@code innodb_ft_min_token_size}（=3）对 ngram 解析器**不生效**，别据此误判「3 字以下搜不到」。</li>
 * </ol>
 *
 * <p><b>物理重建</b>（设计 §2.10）：{@code deleteByDocumentId} 用原生 {@code @Delete} 绕过
 * MP 的逻辑删除，物理清空某文档条款后重建——GB 索引表是**派生数据**，不适用「历史不消失」约束
 * （若走软删，换版会无限堆积失效行、拖垮 FULLTEXT 体积）。</p>
 */
public interface GbClauseMapper extends BaseMapper<GbClause> {

    /**
     * ngram 布尔模式检索（score 降序 top-N）。查询串由 {@code GbRetriever} 构造（如 {@code +铅 +限量}）。
     *
     * @param query 布尔查询串（已含 + / 引号 / 空格语义）
     * @param stdNo 限定标准号（模糊 LIKE，可为 null）
     * @param topN  返回条数
     */
    List<GbSearchHitVO> searchByNgram(@Param("query") String query,
                                      @Param("stdNo") String stdNo,
                                      @Param("topN") int topN);

    /**
     * ngram <b>自然语言模式</b>检索（score 降序 top-N）——布尔模式无命中时的兜底层。
     *
     * <p><b>为什么需要</b>：布尔模式下「不含空格的整句中文」会被当成短语（要求词序邻接）而必然 0 命中，
     * 而用户提问天然是整句（如「毒死蜱在黄瓜上的限量是多少」）。自然语言模式会按 ngram 切分整句
     * 并按相关度排序。实测同一整句：布尔 0 条 / 自然语言 844 条。</p>
     *
     * @param query 原始检索语句（**不加** + / 引号等布尔运算符，让 ngram 自行切分）
     * @param stdNo 限定标准号（模糊 LIKE，可为 null）
     * @param topN  返回条数
     */
    List<GbSearchHitVO> searchByNatural(@Param("query") String query,
                                        @Param("stdNo") String stdNo,
                                        @Param("topN") int topN);

    /** 物理删除某文档全部条款（重建索引前调用，见 §2.10）。 */
    @Delete("DELETE FROM gb_clause WHERE document_id = #{documentId}")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * 批量插入条款块（单条 SQL，减少往返；条款为派生索引，无需回填自增主键）。
     *
     * <p>为什么显式带 {@code operator}：原生批量 SQL **不触发** {@code AuditMetaObjectHandler}，
     * 且导入在 {@code @Async} 线程执行（无 SecurityContext）。若不显式承接请求线程的操作人，
     * {@code created_by} 会落成 {@code system}，丢失「谁导的」。此写法与
     * {@code SampleItemMapper.invalidateBySampleId(..., operator)} 一致（审计人显式传入）。</p>
     */
    int insertBatch(@Param("list") List<GbClause> list, @Param("operator") String operator);
}
