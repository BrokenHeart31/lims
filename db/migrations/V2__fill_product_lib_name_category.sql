-- =============================================================================
-- V2__fill_product_lib_name_category.sql  补全 product_lib 的 product_name / category（T-903）
-- 源：旧系统导出 lims.sql 中的 `product` 表（同库保留作迁移源，未改名）
-- 目标：新表 `product_lib` 的 `product_name` / `category` 两列（V1 迁移时留 NULL）
-- =============================================================================
-- 背景：
--   V1 只迁了 `lib` 表的 productId → product_lib.product_code，未迁产品名与类别，
--   导致 product_lib.product_name / category 全为 NULL（92/92），
--   而 T-401「项目分解自动套库」必须按产品名匹配，因此必须先补齐。
--
-- 字段映射（旧 `product` → 新 `product_lib`，一对一）：
--   product.id           → 匹配键，等于 product_lib.product_code
--   product.libName      → product_lib.product_name
--   product.prd_category → product_lib.category
--   （product.prd_contains 为旧系统产品组成说明，新表无对应列，不迁移）
--
-- 匹配依据（已实测）：
--   product 92 行 / product_lib 92 行，product.id 与 product_lib.product_code 100% 一一对应。
--
-- 原则（AGENTS 0.1 / 6.2）：
--   - 单向清洗迁移，禁止复用旧表驼峰列名风格；
--   - 脚本含字段映射、条数校验、before/after 抽查；
--   - 只更新仍为 NULL 的行（幂等：重复执行不覆盖已人工修正的值）。
-- ⚠️ 前置：已执行 V1__import_legacy_data.sql（product_lib 已有 92 行）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 0. BEFORE：目标列填充率基线（人工核对用）
-- -----------------------------------------------------------------------------
SELECT 'product_lib 总数'                        AS metric, COUNT(*) AS cnt FROM `product_lib`
UNION ALL SELECT 'product_lib.product_name 非空', COUNT(*) FROM `product_lib`
      WHERE `product_name` IS NOT NULL AND `product_name` <> ''
UNION ALL SELECT 'product_lib.category 非空',     COUNT(*) FROM `product_lib`
      WHERE `category` IS NOT NULL AND `category` <> '';

-- -----------------------------------------------------------------------------
-- 1. 补齐：按 product_code = product.id 匹配，回填名称与类别
--    只填空值（幂等），不覆盖已有值。
-- -----------------------------------------------------------------------------
UPDATE `product_lib` pl
JOIN `product` p ON p.`id` = pl.`product_code`
SET pl.`product_name` = NULLIF(TRIM(p.`libName`), ''),
    pl.`category`     = NULLIF(TRIM(p.`prd_category`), ''),
    pl.`updated_by`   = 'migration-v2',
    pl.`updated_at`   = NOW()
WHERE (pl.`product_name` IS NULL OR pl.`product_name` = '')
  AND (pl.`category`     IS NULL OR pl.`category`     = '')
  AND TRIM(IFNULL(p.`libName`, '')) <> '';

-- -----------------------------------------------------------------------------
-- 2. AFTER：填充率复核（应与总数 92 相等）
-- -----------------------------------------------------------------------------
SELECT 'product_lib 总数'                        AS metric, COUNT(*) AS cnt FROM `product_lib`
UNION ALL SELECT 'product_lib.product_name 非空', COUNT(*) FROM `product_lib`
      WHERE `product_name` IS NOT NULL AND `product_name` <> ''
UNION ALL SELECT 'product_lib.category 非空',     COUNT(*) FROM `product_lib`
      WHERE `category` IS NOT NULL AND `category` <> '';

-- -----------------------------------------------------------------------------
-- 3. 抽查：类别分布 + 明细样例 + 残留空值（应为 0）
-- -----------------------------------------------------------------------------
SELECT `category`, COUNT(*) AS cnt FROM `product_lib` GROUP BY `category` ORDER BY cnt DESC;

SELECT `id`, `product_code`, `product_name`, `category` FROM `product_lib` ORDER BY `product_code` LIMIT 10;

SELECT COUNT(*) AS product_lib_残留空值 FROM `product_lib`
WHERE `product_name` IS NULL OR `category` IS NULL;
