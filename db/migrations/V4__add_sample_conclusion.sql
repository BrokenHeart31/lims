-- =============================================================================
-- V4__add_sample_conclusion.sql  样品整体结论列（T-601）
-- 目的：AGENTS 7.3 规则 6「任一单项不合格 → 样品整体不合格」的载体。
--   整体结论是**派生值**，在录入保存/提交/查询时按「全部非参考项单项结论」重算并回写，
--   便于报告生成（T-702）与查询（T-801）直接取用，无需每次聚合 sample_result。
-- 语义（落实白名单 D3 裁决）：
--   存在非参考项不合格 → 2 不合格；存在非参考项待判定 → 3 待判定；
--   全部非参考项合格且数量 ≥1 → 1 合格；无非参考项（全为参考项）或存在未录入 → 3 待判定。
-- 说明：本脚本与 db/init/05_sample_tables.sql 的列定义保持一致（init 为全量重建口径，
--   migration 为存量库增量口径）；重复执行安全（先判存在再 ALTER）。
-- =============================================================================

SET NAMES utf8mb4;

-- MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，用 information_schema 判定 + 动态 SQL
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sample_info'
    AND COLUMN_NAME = 'conclusion'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_info` ADD COLUMN `conclusion` TINYINT DEFAULT NULL COMMENT ''整体结论 1=合格 2=不合格 3=待判定（见 common/enums/ResultConclusion）'' AFTER `status`',
  'SELECT ''sample_info.conclusion 已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW COLUMNS FROM `sample_info` LIKE 'conclusion';
SELECT COUNT(*) AS sample_total,
       SUM(`conclusion` IS NULL) AS conclusion_null,
       SUM(`conclusion` = 1) AS conclusion_qualified,
       SUM(`conclusion` = 2) AS conclusion_unqualified,
       SUM(`conclusion` = 3) AS conclusion_pending
FROM `sample_info`;
