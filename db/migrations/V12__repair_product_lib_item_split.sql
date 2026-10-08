-- =============================================================================
-- V12__repair_product_lib_item_split.sql
--   product_lib_item item_name/unit 切分缺陷「定向修复 + fail-loud 校验」（F17 后继）
-- =============================================================================
-- 背景（取证见 _logs/f17-analysis-2026-10-08.md，2026-10-08 主理人/GLM）：
--   V1 §4.2 旧实现按【首个逗号】切分 testItem，当名称本身含逗号（出现多段）时切错，
--   在 product_lib_item 留下「item_name 只剩 1 个字符、unit 变成 '..., xxx' 两段」的坏行。
--   全库命中坏签名 `CHAR_LENGTH(TRIM(item_name))=1 AND unit LIKE '%,%'` 共 **22 行**，
--   涉及 **14 个产品**：nagw002/006/007/010/012/013、nasc005/008/021/026/030、
--   nasg003/007/013。
--
-- 本脚本职责（严格定向、只动这 22 行，不重导 lims.sql）：
--   ① 回源 `lib`（迁移源表仍在库）按【最后一个逗号切分 + 单位白名单】重算 name/unit；
--   ② 仅更新满足上述坏签名的行（幂等：修复后签名不再命中，可反复重跑）；
--   ③ 跑与 V1 完全同口径的 fail-loud 断言，任一异常即 SIGNAL 中止。
--   单位白名单：mg/kg, μg/kg, mg/100g, CFU/g, g/kg, mg/L, μg/L, /
--
-- ⚠️ 前置：已执行 V1（product_lib_item 有数据）、V3（judge_type 已归一）。
-- ⚠️ 幂等：WHERE 命中坏签名；重复执行第二次为 no-op。
-- ⚠️ 执行方式：请使用 mysql 客户端执行（DELIMITER 为客户端指令，
--    JDBC/Flyway 等不识别；本项目迁移脚本统一由 mysql 客户端按序执行）。
-- ⚠️ 退出码：校验通过 = 0；断言失败 = 1（脚本中止）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 0. BEFORE：坏签名行数（预期 22）+ 涉及产品数（预期 14）
-- -----------------------------------------------------------------------------
SELECT 'BEFORE 坏签名行数' AS metric,
       COUNT(*) AS cnt
  FROM `product_lib_item`
 WHERE CHAR_LENGTH(TRIM(IFNULL(`item_name`, ''))) = 1
   AND `unit` LIKE '%,%'
UNION ALL
SELECT 'BEFORE 涉及产品数', COUNT(DISTINCT `product_lib_id`)
  FROM `product_lib_item`
 WHERE CHAR_LENGTH(TRIM(IFNULL(`item_name`, ''))) = 1
   AND `unit` LIKE '%,%';

-- -----------------------------------------------------------------------------
-- 1. 定向修复：join 回 lib（product_code=xh 口径同 V1），按最后逗号 + 白名单重算
--    只更新坏签名行；`pl_id`/`xh` 组合定位，避免误伤正常行。
-- -----------------------------------------------------------------------------
UPDATE `product_lib_item` `t`
JOIN (
    SELECT pl.`id`  AS `pl_id`,
           l.`xh`   AS `xh`,
           CASE
                WHEN l.`testItem` LIKE '%,%'
                 AND TRIM(SUBSTRING_INDEX(l.`testItem`, ',', -1)) IN
                     ('mg/kg', 'μg/kg', 'mg/100g', 'CFU/g', 'g/kg', 'mg/L', 'μg/L', '/')
                THEN TRIM(SUBSTRING(l.`testItem`, 1,
                         CHAR_LENGTH(l.`testItem`)
                         - CHAR_LENGTH(SUBSTRING_INDEX(l.`testItem`, ',', -1)) - 1))
                ELSE TRIM(l.`testItem`)
           END      AS `new_name`,
           CASE
                WHEN l.`testItem` LIKE '%,%'
                 AND TRIM(SUBSTRING_INDEX(l.`testItem`, ',', -1)) IN
                     ('mg/kg', 'μg/kg', 'mg/100g', 'CFU/g', 'g/kg', 'mg/L', 'μg/L', '/')
                THEN TRIM(SUBSTRING_INDEX(l.`testItem`, ',', -1))
                ELSE NULL
           END      AS `new_unit`
      FROM `lib` l
      JOIN `product_lib` pl ON pl.`product_code` = TRIM(l.`productId`)
) `d` ON `d`.`pl_id` = `t`.`product_lib_id` AND `d`.`xh` = `t`.`item_order`
SET `t`.`item_name`  = `d`.`new_name`,
    `t`.`unit`       = `d`.`new_unit`,
    `t`.`updated_by` = 'migration-v12',
    `t`.`updated_at` = NOW()
WHERE CHAR_LENGTH(TRIM(IFNULL(`t`.`item_name`, ''))) = 1
  AND `t`.`unit` LIKE '%,%';

-- -----------------------------------------------------------------------------
-- 2. AFTER：坏签名行数复核（预期 0）
-- -----------------------------------------------------------------------------
SELECT 'AFTER 坏签名行数' AS metric,
       COUNT(*) AS cnt
  FROM `product_lib_item`
 WHERE CHAR_LENGTH(TRIM(IFNULL(`item_name`, ''))) = 1
   AND `unit` LIKE '%,%';

-- =============================================================================
-- 3. 🔴 fail-loud 断言（与 V1 §6 完全同口径，独立重算）
--      ① 切分缺陷签名 = 0（含「lib 回源失败导致未修复」的行 → 会在此暴露）
--      ② 非空 unit 必须在单位白名单内
--      ③ 乱码特征（C1 控制符 C2 80~C2 9F） = 0
-- =============================================================================
DELIMITER $$
DROP PROCEDURE IF EXISTS `_v12_assert_item_split_ok`$$
CREATE PROCEDURE `_v12_assert_item_split_ok`()
BEGIN
    DECLARE v_total     INT DEFAULT 0;
    DECLARE v_bad_split INT DEFAULT 0;
    DECLARE v_bad_unit  INT DEFAULT 0;
    DECLARE v_mojibake  INT DEFAULT 0;
    DECLARE v_msg       VARCHAR(128) DEFAULT '';

    SELECT COUNT(*) INTO v_total FROM `product_lib_item`;

    SELECT COUNT(*) INTO v_bad_split
      FROM `product_lib_item`
     WHERE CHAR_LENGTH(TRIM(IFNULL(`item_name`, ''))) = 1
       AND `unit` LIKE '%,%';

    SELECT COUNT(*) INTO v_bad_unit
      FROM `product_lib_item`
     WHERE `unit` IS NOT NULL
       AND `unit` NOT IN ('mg/kg', 'μg/kg', 'mg/100g', 'CFU/g', 'g/kg', 'mg/L', 'μg/L', '/');

    SELECT COUNT(*) INTO v_mojibake
      FROM `product_lib_item`
     WHERE HEX(IFNULL(`item_name`, ''))  REGEXP 'C2(8[0-9A-F]|9[0-9A-F])'
        OR HEX(IFNULL(`methods`, ''))    REGEXP 'C2(8[0-9A-F]|9[0-9A-F])'
        OR HEX(IFNULL(`basis_code`, '')) REGEXP 'C2(8[0-9A-F]|9[0-9A-F])';

    IF v_bad_split > 0 OR v_bad_unit > 0 OR v_mojibake > 0 THEN
        SELECT '❌ V12 修正断言失败' AS v12_assert_result,
               v_total AS 总行数, v_bad_split AS 切分缺陷行数,
               v_bad_unit AS 单位越界行数, v_mojibake AS 乱码行数;
        SET v_msg = CONCAT('V12 fail-loud: 切分/单位校验未通过 (split=', v_bad_split,
                           ', unit=', v_bad_unit, ', mojibake=', v_mojibake, ')');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT CONCAT('✅ V12 修正校验通过：', v_total,
                  ' 行 item_name/unit 切分与单位白名单均符合口径（坏签名=0）') AS v12_assert_result;
END$$
DELIMITER ;

CALL `_v12_assert_item_split_ok`();
DROP PROCEDURE IF EXISTS `_v12_assert_item_split_ok`;
