-- =============================================================================
-- V10__gb_clause_title_fulltext.sql
-- 2026-09-18  为 gb_clause.clause_title 增加 ngram 全文索引（检索排序用）
-- =============================================================================
-- 背景
-- -----------------------------------------------------------------------------
-- GB 标准 OCR 正文里存在大量「检测方法清单」式超长块（同一块提到几十种农药名），
-- 仅按正文相关度排序时，它们会排在真正的条款块之前。
-- 实测（GB 2763-2021）：问「毒死蜱」，首位是「异狄氏剂」块，
-- 4.121 毒死蜱 的条款块反而被挤出前列 —— 会让引用卡片指向无关条款。
--
-- 条款标题（clause_title）是「这一条讲什么」的权威信号，故：
--   SELECT ... ORDER BY (MATCH(clause_title) AGAINST(query) > 0) DESC, score DESC
-- 即：能命中标题的块优先，再按正文相关度、块序排序。
--
-- 幂等性
-- -----------------------------------------------------------------------------
-- 通过 information_schema.STATISTICS 判定，索引已存在则跳过（可重复执行）。
-- gb_clause 是**派生索引表**（可由 ai/standards/parsed/ 重建），加索引无业务数据风险。
-- =============================================================================

SET @has_title_ft := (
    SELECT COUNT(*)
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'gb_clause'
       AND INDEX_NAME = 'ft_gbc_title'
);

SET @sql := IF(@has_title_ft = 0,
    'ALTER TABLE `gb_clause` ADD FULLTEXT KEY `ft_gbc_title` (`clause_title`) WITH PARSER ngram',
    'SELECT ''ft_gbc_title 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 校验（期望：ngram_active=1；ft_gbc_title 在列）
-- -----------------------------------------------------------------------------
SELECT 'ngram_active' AS item,
       COUNT(*) AS val
  FROM information_schema.PLUGINS
 WHERE PLUGIN_NAME = 'ngram' AND PLUGIN_STATUS = 'ACTIVE';

SELECT INDEX_NAME, COLUMN_NAME, INDEX_TYPE
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'gb_clause'
   AND INDEX_TYPE = 'FULLTEXT'
 ORDER BY INDEX_NAME;
