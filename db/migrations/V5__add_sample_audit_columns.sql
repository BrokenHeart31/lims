-- =============================================================================
-- V5__add_sample_audit_columns.sql  样品审核/签发字段（T-701）
-- 目的：业务说明书报告页脚要求「报告无制表、审核、批准人签字无效」——
--   审核人/签发人必须落到样品上，供 T-702 报告合成直接取用（避免反查流水）。
--   `sample_audit_log`（db/init/08）保存**完整流水**；本处在 sample_info 保存**当前有效值**，
--   与既有 `confirmed_by`/`confirmed_at`（S10→S20）同一先例。
-- 语义：
--   audit_by/audit_at/audit_opinion —— 审核通过时写入（S60→S70）；审核退回时清空并记流水。
--   sign_by/sign_at                 —— 签发时写入（S70→S80）。
-- 说明：本脚本与 db/init/05_sample_tables.sql 列定义保持一致（init 全量重建口径，
--   migration 存量库增量口径）；重复执行安全（先判存在再 ALTER）。
-- =============================================================================

SET NAMES utf8mb4;

-- MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，用 information_schema 判定 + 动态 SQL
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sample_info'
    AND COLUMN_NAME = 'audit_by'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_info`
     ADD COLUMN `audit_by`      VARCHAR(64)  DEFAULT NULL COMMENT ''审核人工号（S60→S70 写入）'' AFTER `conclusion`,
     ADD COLUMN `audit_at`      DATETIME     DEFAULT NULL COMMENT ''审核时间'' AFTER `audit_by`,
     ADD COLUMN `audit_opinion` VARCHAR(500) DEFAULT NULL COMMENT ''审核意见'' AFTER `audit_at`,
     ADD COLUMN `sign_by`       VARCHAR(64)  DEFAULT NULL COMMENT ''签发人工号（S70→S80 写入）'' AFTER `audit_opinion`,
     ADD COLUMN `sign_at`       DATETIME     DEFAULT NULL COMMENT ''签发时间'' AFTER `sign_by`',
  'SELECT ''sample_info 审核/签发列已存在，跳过'' AS msg');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW COLUMNS FROM `sample_info` LIKE 'audit%';
SHOW COLUMNS FROM `sample_info` LIKE 'sign%';
SELECT COUNT(*) AS sample_total,
       SUM(`audit_by` IS NOT NULL) AS audited,
       SUM(`sign_by`  IS NOT NULL) AS signed
FROM `sample_info`;
