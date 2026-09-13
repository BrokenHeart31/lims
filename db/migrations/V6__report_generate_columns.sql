-- =============================================================================
-- V6__report_generate_columns.sql  T-702 报告生成落点 + 电子签名位（增量迁移）
-- 目的：
--   1. `sample_info` 记录「报告是否已生成 / 生成的是哪类报告 / 何时由谁生成」——
--      落实说明书「九、检验业务流程之六：自动生成检验报告」，并把 S80→S90 的
--      流转结果落到可查询的字段上（T-801 历史查询要按「是否已出报告」筛选）。
--   2. `sys_user.signature_url` 承载「预先保存的电子签名」——
--      说明书原文：「检验报告可自动调用预先保存的电子签名」。
--      设计取舍：报告**不落快照**（实时聚合 sample_result + sample_item）。
--      依据：S80 已签发后样品再无写路径（S90 为终态），数据天然冻结，实时聚合不会漂移；
--      落快照反而引入「快照与事实两处真相」的维护成本与一致性风险。
-- 说明：本脚本与 db/init/05_sample_tables.sql、db/init/02_rbac_tables.sql 的列定义保持一致
--      （init 全量重建口径 / migration 存量库增量口径）；重复执行安全（先判存在再 ALTER）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. sample_info：报告生成落点（3 列）
-- -----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_info' AND COLUMN_NAME = 'report_type'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_info`
     ADD COLUMN `report_type`         TINYINT  DEFAULT NULL COMMENT ''报告类型 1=CMA 2=CMA-CATL（见 common/enums/ReportType，T-702）'' AFTER `sign_at`,
     ADD COLUMN `report_generated_at` DATETIME DEFAULT NULL COMMENT ''报告生成时间（S80→S90 时写入）'' AFTER `report_type`,
     ADD COLUMN `report_generated_by` BIGINT   DEFAULT NULL COMMENT ''报告生成人 sys_user.id'' AFTER `report_generated_at`',
  'SELECT ''sample_info 报告生成列已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 2. sys_user：电子签名位（1 列）
--    可为空：未配置签名的用户，报告上渲染虚线占位框（绝不伪造签名图片）。
-- -----------------------------------------------------------------------------
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'signature_url'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sys_user`
     ADD COLUMN `signature_url` VARCHAR(255) DEFAULT NULL COMMENT ''电子签名图片地址（报告自动调用；空则报告渲染占位框）'' AFTER `phone`',
  'SELECT ''sys_user.signature_url 已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW COLUMNS FROM `sample_info` LIKE 'report%';
SHOW COLUMNS FROM `sys_user` LIKE 'signature%';
SELECT COUNT(*) AS sample_total,
       SUM(`report_generated_at` IS NOT NULL) AS cnt_generated,
       SUM(`status` = 80) AS cnt_pending_report,
       SUM(`status` = 90) AS cnt_reported
FROM `sample_info`;
