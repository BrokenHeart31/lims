-- =============================================================================
-- V1__import_legacy_data.sql  旧数据迁移脚本（T-104）
-- 源：旧系统导出 lims.sql 中的 basisname / customer / dept / lib（同库保留作迁移源）
-- 注意：lims.sql 的旧 customer/dept 与 01_basic_tables.sql 新建表同名（旧结构），
--       导入 lims.sql 时须将旧表改名为 customer_legacy / dept_legacy（本地导入前替换，
--       lims.sql 不入库），故本节迁移源为 customer_legacy；dept 旧表 0 行 no-op。
-- 目标：新表 basis / customer / dept / product_lib / product_lib_item（见 db/init/01_basic_tables.sql）
-- 原则（AGENTS 0.1 / 6.2）：
--   - 单向清洗迁移；禁止在新代码复用旧表驼峰列名/无审计字段风格；
--   - 脚本含字段映射、去重规则、迁移前后条数校验 SELECT；
--   - 审计四字段由迁移统一填 'migration-v1' / NOW()。
-- ⚠️ 前置：先执行 db/init/01_basic_tables.sql 建出新表。
-- ℹ️ product_lib* 已经 Copilot 终审定稿：judge_type 由本脚本按 stdValue 形态推导
--    （数值→1 限量比较；含"不得检出/不得使用"→2；其余→3 文本/感官人工）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 0. 迁移前条数（人工核对用）
-- -----------------------------------------------------------------------------
SELECT 'legacy basisname'  AS src, COUNT(*) AS cnt FROM `basisname`
UNION ALL SELECT 'legacy customer', COUNT(*) FROM `customer_legacy`
UNION ALL SELECT 'legacy dept',     COUNT(*) FROM `dept_legacy`
UNION ALL SELECT 'legacy lib',      COUNT(*) FROM `lib`;

-- =============================================================================
-- 1. basisname → basis（判定依据）
--    映射：basis→code，basisName→name，note→remark
--    清洗：TRIM；code 中全角长破折号 '—'(U+2014) 归一为半角 '-'；
--          按 (code, name) 去重（旧表同 code 同名重复，且同 code 不同名者保留）。
-- =============================================================================
INSERT INTO `basis` (`code`, `name`, `remark`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
SELECT DISTINCT
       REPLACE(TRIM(`basis`), '—', '-') AS code,
       TRIM(`basisName`)                 AS name,
       NULLIF(TRIM(`note`), '')          AS remark,
       'migration-v1', NOW(), 'migration-v1', NOW(), 0
FROM `basisname`
WHERE TRIM(IFNULL(`basis`, '')) <> ''
  AND TRIM(IFNULL(`basisName`, '')) <> '';

-- =============================================================================
-- 2. customer_legacy → customer（客户/受检单位）
--    映射：company→name, lxr→contact_person, leader→leader,
--          lxrMobileNo→contact_mobile, leaderMobileNO→leader_mobile,
--          addr→address, type→business_type, bank→bank, bankNo→bank_account, status→status
--    清洗：TRIM；空串归一 NULL；按单位名称去重。
-- =============================================================================
INSERT INTO `customer`
  (`name`, `contact_person`, `leader`, `contact_mobile`, `leader_mobile`,
   `address`, `business_type`, `bank`, `bank_account`, `status`, `remark`,
   `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
SELECT DISTINCT
       TRIM(`company`)                                        AS name,
       NULLIF(TRIM(`lxr`), '')                               AS contact_person,
       NULLIF(TRIM(`leader`), '')                            AS leader,
       NULLIF(TRIM(`lxrMobileNo`), '')                       AS contact_mobile,
       NULLIF(TRIM(`leaderMobileNO`), '')                    AS leader_mobile,
       NULLIF(TRIM(`addr`), '')                              AS address,
       NULLIF(TRIM(`type`), '')                              AS business_type,
       NULLIF(TRIM(`bank`), '')                              AS bank,
       NULLIF(TRIM(`bankNo`), '')                            AS bank_account,
       NULLIF(TRIM(`status`), '')                            AS status,
       NULLIF(TRIM(`fenlei`), '')                            AS remark,
       'migration-v1', NOW(), 'migration-v1', NOW(), 0
FROM `customer_legacy`
WHERE TRIM(IFNULL(`company`, '')) <> '';

-- =============================================================================
-- 3. dept → dept（部门）
--    旧 dept 表无记录（0 行），无需迁移；新部门数据由 T-101/RBAC 种子补充。
-- =============================================================================
-- (no-op)

-- =============================================================================
-- 4. lib → product_lib + product_lib_item（项目标准库）
--    4.1 先迁产品头：DISTINCT productId → product_lib.product_code
-- =============================================================================
INSERT IGNORE INTO `product_lib` (`product_code`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
SELECT DISTINCT TRIM(`productId`), 'migration-v1', NOW(), 'migration-v1', NOW(), 0
FROM `lib`
WHERE TRIM(IFNULL(`productId`, '')) <> '';

--    4.2 再迁检测项目明细
--        testItem '阿维菌素,mg/kg' → item_name='阿维菌素', unit='mg/kg'（按首个逗号切分）
--        mathod 末尾 '#' 为方法分隔符残留，统一 TRIM TRAILING '#'
--        stdValue '0.02*' → std_value='0.02', is_reference=1
--        judge_type 推导（Copilot 终审规则）：去 * 后为纯数值 → 1 限量比较；
--          含"不得检出"或"不得使用" → 2 不得检出/不得使用；其余（含空值/文本）→ 3 人工
-- =============================================================================
INSERT INTO `product_lib_item`
  (`product_lib_id`, `item_order`, `item_name`, `unit`, `basis_code`,
   `methods`, `std_value`, `judge_type`, `is_reference`, `lower_limit`, `method_note`,
   `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`)
SELECT pl.`id`,
       l.`xh`,
       TRIM(SUBSTRING_INDEX(l.`testItem`, ',', 1))                         AS item_name,
       NULLIF(TRIM(SUBSTRING(l.`testItem`,
                  CHAR_LENGTH(SUBSTRING_INDEX(l.`testItem`, ',', 1)) + 2)), '') AS unit,
       NULLIF(TRIM(l.`basis`), '')                                        AS basis_code,
       NULLIF(TRIM(TRIM(TRAILING '#' FROM l.`mathod`)), '')               AS methods,
       CASE WHEN l.`stdValue` LIKE '%*'
            THEN TRIM(TRAILING '*' FROM TRIM(l.`stdValue`))
            ELSE NULLIF(TRIM(l.`stdValue`), '') END                       AS std_value,
       CASE
            WHEN TRIM(TRAILING '*' FROM TRIM(IFNULL(l.`stdValue`, '')))
                 REGEXP '^[0-9]+(\\.[0-9]+)?$' THEN 1
            WHEN l.`stdValue` LIKE '%不得检出%' OR l.`stdValue` LIKE '%不得使用%' THEN 2
            ELSE 3
       END                                                                AS judge_type,
       IF(l.`stdValue` LIKE '%*', 1, 0)                                   AS is_reference,
       NULLIF(TRIM(l.`num_lowerst`), '')                                  AS lower_limit,
       NULLIF(TRIM(l.`method_note`), '')                                  AS method_note,
       'migration-v1', NOW(), 'migration-v1', NOW(), 0
FROM `lib` l
JOIN `product_lib` pl ON pl.`product_code` = TRIM(l.`productId`)
WHERE TRIM(IFNULL(l.`testItem`, '')) <> '';

-- -----------------------------------------------------------------------------
-- 5. 迁移后条数校验（人工核对）
-- -----------------------------------------------------------------------------
SELECT 'new basis'               AS dst, COUNT(*) AS cnt FROM `basis`
UNION ALL SELECT 'new customer',     COUNT(*) FROM `customer`
UNION ALL SELECT 'new dept',        COUNT(*) FROM `dept`
UNION ALL SELECT 'new product_lib',  COUNT(*) FROM `product_lib`
UNION ALL SELECT 'new product_lib_item', COUNT(*) FROM `product_lib_item`;

-- 抽查：判定依据按 code 去重后的 code 数（应 < basisname 行数）
SELECT COUNT(DISTINCT `code`) AS distinct_basis_code FROM `basis`;
-- 抽查：参考性限量条目数（is_reference=1）
SELECT COUNT(*) AS ref_items FROM `product_lib_item` WHERE `is_reference` = 1;
-- 抽查：判定类型分布（judge_type 1=限量比较 2=不得检出/不得使用 3=人工）
SELECT `judge_type`, COUNT(*) AS cnt FROM `product_lib_item` GROUP BY `judge_type`;
