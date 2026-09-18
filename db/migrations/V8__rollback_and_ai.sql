-- =============================================================================
-- V8__rollback_and_ai.sql  回退机制 + AI 助手 增量迁移（存量库升级）
-- 对应设计：docs/design/2026-09-17-arch-ai-assistant-and-rollback.md §3.2 / §2.9
-- 对应任务：T01 基础设施与契约 / T02 回退机制后端
-- 前置：db/init/05,06,07 + V1..V7
-- 本脚本只做 ALTER / 校验，**不重建业务表**；新表建表见 db/init/10、db/init/11。
-- 幂等：MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，沿用 V5/V6 的
--       information_schema 判定 + 动态 SQL 写法，可重复执行。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1) 回退：样品表新增「作废/召回」标记（不改状态机取值域）
--    S80/S90 的作废/召回是**标记动作**，status 保持 80/90 不变（见设计 §10-R6 待明确项）。
-- -----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_info' AND COLUMN_NAME = 'void_status'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_info`
     ADD COLUMN `void_status` TINYINT NOT NULL DEFAULT 0
     COMMENT ''作废/召回标记 0=正常 1=已作废 2=已召回（S80/S90 专用治理动作，不改 status）''
     AFTER `report_generated_by`,
     ADD KEY `idx_sample_void_status` (`void_status`)',
  'SELECT ''sample_info.void_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2) ⚠️ 关键：失效标记升级为「行自身 id」（列由 TINYINT 改 BIGINT）
--
--    背景（设计 §2.9 Pit 1）：sample_item 唯一键 uk_sample_item_order(sample_id,item_order,deleted)
--    与 sample_result 唯一键 uk_result_sample_item(sample_item_id,deleted) 只有 0/1 两态。
--    sample_item 的覆盖式重建（先逻辑删旧明细再全量 INSERT）一旦被回退触发**二次失效**，
--    就会撞唯一键（1062）。把失效值改为「该行自身 id」后，因每行 id 唯一，
--    (sample_id,item_order,deleted) 与 (sample_item_id,deleted) 永不可能碰撞。
--
--    @TableLogic 语义不受影响：查询恒为 deleted = 0（与列类型无关），非 0 一律视为已失效。
--    ⚠️ 生效前提：这两张表的「逻辑删除」必须改走 service/rollback/SampleDataDisposer
--       显式 SET deleted = id；**禁止再用 baseMapper.delete(...)**（MP 会写死 deleted=1 → 仍会撞键）。
-- -----------------------------------------------------------------------------
SET @item_type := (
  SELECT COLUMN_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_item' AND COLUMN_NAME = 'deleted'
);
SET @ddl := IF(@item_type IS NOT NULL AND @item_type LIKE 'tinyint%',
  'ALTER TABLE `sample_item` MODIFY COLUMN `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT ''失效标记 0=有效 非0=该行自身id（已失效）''',
  'SELECT ''sample_item.deleted 已是 BIGINT，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @result_type := (
  SELECT COLUMN_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_result' AND COLUMN_NAME = 'deleted'
);
SET @ddl := IF(@result_type IS NOT NULL AND @result_type LIKE 'tinyint%',
  'ALTER TABLE `sample_result` MODIFY COLUMN `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT ''失效标记 0=有效 非0=该行自身id（已失效）''',
  'SELECT ''sample_result.deleted 已是 BIGINT，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 3) 校验 SELECT（人工核对，fail-loud：应然 ≠ 实然即人工介入）
-- -----------------------------------------------------------------------------
SELECT 'sample_info.void_status' AS item, COUNT(*) AS should_be_1
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_info' AND COLUMN_NAME = 'void_status';
SELECT 'sample_item.deleted.type' AS item, COLUMN_TYPE AS val
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_item' AND COLUMN_NAME = 'deleted';
SELECT 'sample_result.deleted.type' AS item, COLUMN_TYPE AS val
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_result' AND COLUMN_NAME = 'deleted';

-- ngram 全文解析器自检（AI 检索前置；应返回 1）
SELECT 'ngram_active' AS item, COUNT(*) AS should_be_1
  FROM information_schema.PLUGINS WHERE PLUGIN_NAME = 'ngram' AND PLUGIN_STATUS = 'ACTIVE';

-- 孤儿引用自检（回退一致性基线；期望 orphan_result = 0）
--   回退把 sample_item 失效后，若 sample_result 仍指向已失效明细 → 孤儿，需人工介入。
SELECT 'orphan_result' AS item, COUNT(*) AS expect_0
  FROM `sample_result` r
  LEFT JOIN `sample_item` i ON i.`id` = r.`sample_item_id` AND i.`deleted` = 0
 WHERE r.`deleted` = 0 AND i.`id` IS NULL;
