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
--        F17 修正（2026-10-08，取证见 _logs/f17-analysis-2026-10-08.md）：
--          旧实现按【首个逗号】切分，当 testItem 的名称本身含逗号（出现多段）时切错，
--          产生「item_name 只剩 1 个字符、unit 变成 '..., xxx' 两段」的坏行。
--          改为按【最后一个逗号】切分 + 单位白名单：
--            · 末段（最后一个逗号之后）命中单位白名单 → unit=末段、item_name=末段之前的整体
--            · 否则（无逗号 / 末段不是合法单位）        → item_name=整串、unit=NULL
--          单位白名单：mg/kg, μg/kg, mg/100g, CFU/g, g/kg, mg/L, μg/L, /
--          末尾三条 fail-loud 断言（见 §6）兜底，任何切分/单位/乱码异常即中止。
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
       -- item_name：末段为合法单位时取「最后一个逗号之前的整体」，否则取整串
       CASE
            WHEN l.`testItem` LIKE '%,%'
             AND TRIM(SUBSTRING_INDEX(l.`testItem`, ',', -1)) IN
                 ('mg/kg', 'μg/kg', 'mg/100g', 'CFU/g', 'g/kg', 'mg/L', 'μg/L', '/')
            THEN TRIM(SUBSTRING(l.`testItem`, 1,
                     CHAR_LENGTH(l.`testItem`)
                     - CHAR_LENGTH(SUBSTRING_INDEX(l.`testItem`, ',', -1)) - 1))
            ELSE TRIM(l.`testItem`)
       END                                                                AS item_name,
       -- unit：仅当末段命中白名单才取值，否则 NULL
       CASE
            WHEN l.`testItem` LIKE '%,%'
             AND TRIM(SUBSTRING_INDEX(l.`testItem`, ',', -1)) IN
                 ('mg/kg', 'μg/kg', 'mg/100g', 'CFU/g', 'g/kg', 'mg/L', 'μg/L', '/')
            THEN TRIM(SUBSTRING_INDEX(l.`testItem`, ',', -1))
            ELSE NULL
       END                                                                AS unit,
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

-- =============================================================================
-- 6. 🔴 fail-loud 断言（F17 修正的兜底校验，2026-10-08；仿 V3 的 SIGNAL 写法）
--    F17 修正把「按首个逗号切分」改为「按最后一个逗号切分 + 单位白名单」，
--    这里独立重新统计三类异常行，任一 > 0 即 SIGNAL 报错并中止脚本，绝不静默通过：
--      ① 切分缺陷签名：item_name 只剩 1 个字符 且 unit 里还含逗号（旧切法的残留形态）
--      ② 单位越界：非空 unit 不在单位白名单内
--      ③ 乱码特征：item_name/methods/basis_code 的 HEX 中出现 C1 控制符（C2 80~C2 9F，
--         UTF-8 被 Latin-1 误读后重编码的签名，详见 _logs/f17-analysis-2026-10-08.md）
--    ⚠️ 执行方式：DELIMITER 为 mysql 客户端指令，请用 mysql 客户端按序执行本文件。
-- =============================================================================
DELIMITER $$
DROP PROCEDURE IF EXISTS `_v1_assert_item_split_ok`$$
CREATE PROCEDURE `_v1_assert_item_split_ok`()
BEGIN
    DECLARE v_total     INT DEFAULT 0;
    DECLARE v_bad_split INT DEFAULT 0;
    DECLARE v_bad_unit  INT DEFAULT 0;
    DECLARE v_mojibake  INT DEFAULT 0;
    DECLARE v_msg       VARCHAR(128) DEFAULT '';

    SELECT COUNT(*) INTO v_total FROM `product_lib_item`;

    -- ① 切分缺陷签名
    SELECT COUNT(*) INTO v_bad_split
      FROM `product_lib_item`
     WHERE CHAR_LENGTH(TRIM(IFNULL(`item_name`, ''))) = 1
       AND `unit` LIKE '%,%';

    -- ② 单位白名单
    SELECT COUNT(*) INTO v_bad_unit
      FROM `product_lib_item`
     WHERE `unit` IS NOT NULL
       AND `unit` NOT IN ('mg/kg', 'μg/kg', 'mg/100g', 'CFU/g', 'g/kg', 'mg/L', 'μg/L', '/');

    -- ③ 乱码特征（C1 控制符签名 C2 80~C2 9F）
    SELECT COUNT(*) INTO v_mojibake
      FROM `product_lib_item`
     WHERE HEX(IFNULL(`item_name`, ''))  REGEXP 'C2(8[0-9A-F]|9[0-9A-F])'
        OR HEX(IFNULL(`methods`, ''))    REGEXP 'C2(8[0-9A-F]|9[0-9A-F])'
        OR HEX(IFNULL(`basis_code`, '')) REGEXP 'C2(8[0-9A-F]|9[0-9A-F])';

    IF v_bad_split > 0 OR v_bad_unit > 0 OR v_mojibake > 0 THEN
        -- 先输出明细结果集（便于定位），再用 ≤128 字符的短消息中止
        SELECT '❌ V1 4.2 切分断言失败' AS v1_assert_result,
               v_total AS 总行数, v_bad_split AS 切分缺陷行数,
               v_bad_unit AS 单位越界行数, v_mojibake AS 乱码行数;
        SET v_msg = CONCAT('V1 fail-loud: 切分/单位校验未通过 (split=', v_bad_split,
                           ', unit=', v_bad_unit, ', mojibake=', v_mojibake, ')');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT CONCAT('✅ V1 4.2 切分校验通过：', v_total,
                  ' 行 item_name/unit 切分与单位白名单均符合口径') AS v1_assert_result;
END$$
DELIMITER ;

CALL `_v1_assert_item_split_ok`();
DROP PROCEDURE IF EXISTS `_v1_assert_item_split_ok`;
