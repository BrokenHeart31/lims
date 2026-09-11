-- =============================================================================
-- V3__correct_product_lib_item_judge_type.sql
--   judge_type 口径校验器（可重跑 / fail-loud）
-- =============================================================================
-- 裁决依据：docs/knowledge/2026-09-11-judge-engine-whitelist.md  D5 节
--   T-905（2026-09-11）修订裁决 —— 采纳 R1：
--     · 原 D5 前提（lib 存在 jt2/jt3 形态、judge_type 全 1 属迁移缺陷）**已被实测证伪**；
--     · 本脚本定位由「一次性数据订正」改为「**可重跑的口径校验器**」；
--     · 【硬性要求】校验器必须 **fail-loud** —— 若「形态应然 ≠ judge_type 实然」
--       且 UPDATE 后仍不一致，必须报错退出，**禁止静默通过**，
--       否则它将退化成新的「静默零变更」。
--     · R3（从旧 prj_detail 反建兽残标准库）**已否决**，不在本脚本职责内。
--
-- 🔴 实测结论（GLM 全库核对，2026-09-11；证据见
--    docs/knowledge/2026-09-11-adjudication-request-d5.md）：
--   本脚本在当前数据上为 **零变更（no-op）**，且这是数据事实而非脚本缺陷：
--   - product_lib_item 3728 行，std_value **100% 为纯数值或数值+`*`**（如 '0.5'、'0.1*'）；
--   - 无一条含「不得检出」「不得使用」，无 `--`，无非数值纯文本 → judge_type 应然全为 1；
--   - 根因：product_lib_item 唯一数据源是旧 `lib` 表（农残 GB 2763-2021 体系）。
--     旧 `prj_detail` 的 220 条「不得检出」属**兽药残留等另一套标准体系**
--     （硝基呋喃类代谢物/诺氟沙星/恩诺沙星/孔雀石绿/氯霉素），这 5 个项目名在
--     product_lib_item 中 **0 匹配** → 两表不同源，无法用于「订正」lib 的行。
--
-- 已知数据覆盖缺口（登记，不修）：5 个兽残「不得检出」项目暂无标准库行。
--   将来业务需要时，由业务方提供**标准文本**，走基础数据补录（/api/base 域）后
--   重跑本脚本即可归一化。**禁止**从历史检验记录反建标准库行。
--
-- ⚠️ 前置：已执行 V1（product_lib_item 已有数据）。
-- ⚠️ 幂等：只更新 judge_type 与应然值不一致的行，可反复重跑。
-- ⚠️ 执行方式：请使用 mysql 客户端执行（`DELIMITER` 为客户端指令，
--    JDBC/Flyway 等不识别；本项目迁移脚本统一由 mysql 客户端按序执行）。
--    推荐：mysql -uroot -p --default-character-set=utf8mb4 <库名> < 本文件
-- ⚠️ 退出码：校验通过 = 0；断言失败 = 1（脚本中止，后续语句不执行）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 0. BEFORE：judge_type 分布 + 按形态分类的应然分布（诊断用）
--    白名单闭集（与 docs/knowledge/2026-09-11-judge-engine-whitelist.md 一致）：
--      去 `*` 后为纯数值               → 1 限量比较
--      含「不得检出」或「不得使用」     → 2 不得检出/不得使用
--      其余（`--` 占位 / 非数值纯文本） → 3 文本/感官人工
-- -----------------------------------------------------------------------------
SELECT 'BEFORE judge_type=1' AS metric, COUNT(*) AS cnt FROM `product_lib_item` WHERE `judge_type` = 1
UNION ALL SELECT 'BEFORE judge_type=2', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 2
UNION ALL SELECT 'BEFORE judge_type=3', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 3
UNION ALL SELECT 'BEFORE 其他值',       COUNT(*) FROM `product_lib_item` WHERE `judge_type` NOT IN (1, 2, 3)
UNION ALL SELECT 'BEFORE 总行数',       COUNT(*) FROM `product_lib_item`;

-- 形态诊断：各形态实际条数（用于解释 before 分布为何如此）
SELECT '形态=纯数值(含*)' AS shape, COUNT(*) AS cnt FROM `product_lib_item`
  WHERE TRIM(TRAILING '*' FROM TRIM(IFNULL(`std_value`, ''))) REGEXP '^[0-9]+(\\.[0-9]+)?$'
UNION ALL SELECT '形态=不得检出/不得使用', COUNT(*) FROM `product_lib_item`
  WHERE `std_value` LIKE '%不得检出%' OR `std_value` LIKE '%不得使用%'
UNION ALL SELECT '形态=-- 占位', COUNT(*) FROM `product_lib_item`
  WHERE TRIM(IFNULL(`std_value`, '')) = '--'
UNION ALL SELECT '形态=非数值文本', COUNT(*) FROM `product_lib_item`
  WHERE TRIM(IFNULL(`std_value`, '')) <> ''
    AND TRIM(TRAILING '*' FROM TRIM(`std_value`)) NOT REGEXP '^[0-9]+(\\.[0-9]+)?$'
    AND `std_value` NOT LIKE '%不得检出%' AND `std_value` NOT LIKE '%不得使用%'
    AND TRIM(`std_value`) <> '--'
UNION ALL SELECT '形态=std_value 空', COUNT(*) FROM `product_lib_item`
  WHERE TRIM(IFNULL(`std_value`, '')) = '';

-- -----------------------------------------------------------------------------
-- 1. 归一化：按白名单口径重判 judge_type（幂等，只改不一致行）
--    写法要点：应然值由**派生表统一计算一次**（避免 SET 子句与 WHERE 子句各写一遍而漂移），
--    并用 NULL 安全比较 `<=>` —— 否则 `judge_type IS NULL` 的行会因 `NULL <> x` 为 UNKNOWN
--    而被漏更新，留下永久漂移。
-- -----------------------------------------------------------------------------
UPDATE `product_lib_item` `t`
JOIN (
    SELECT `id` AS `rid`,
           CASE
               WHEN TRIM(TRAILING '*' FROM TRIM(IFNULL(`std_value`, '')))
                    REGEXP '^[0-9]+(\\.[0-9]+)?$' THEN 1
               WHEN `std_value` LIKE '%不得检出%' OR `std_value` LIKE '%不得使用%' THEN 2
               ELSE 3
           END AS `expected`
      FROM `product_lib_item`
) `e` ON `e`.`rid` = `t`.`id`
SET `t`.`judge_type` = `e`.`expected`,
    `t`.`updated_by` = 'migration-v3',
    `t`.`updated_at` = NOW()
WHERE NOT (`t`.`judge_type` <=> `e`.`expected`);

-- -----------------------------------------------------------------------------
-- 2. AFTER：分布复核（当前数据下应与 BEFORE 完全一致 → 证明零变更）
-- -----------------------------------------------------------------------------
SELECT 'AFTER judge_type=1' AS metric, COUNT(*) AS cnt FROM `product_lib_item` WHERE `judge_type` = 1
UNION ALL SELECT 'AFTER judge_type=2', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 2
UNION ALL SELECT 'AFTER judge_type=3', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 3
UNION ALL SELECT 'AFTER 其他值',       COUNT(*) FROM `product_lib_item` WHERE `judge_type` NOT IN (1, 2, 3)
UNION ALL SELECT 'AFTER 总行数',       COUNT(*) FROM `product_lib_item`;

-- -----------------------------------------------------------------------------
-- 3. 证据：与旧 prj_detail 的「不得检出」项目对照（证明不同源，非漏迁）
--    预期：prj_detail_jt2_items = 5，而 matched_in_new_lib = 0（兽残项目在新库不存在）
-- -----------------------------------------------------------------------------
SELECT (SELECT COUNT(DISTINCT TRIM(SUBSTRING_INDEX(`testItem`, ',', 1)))
        FROM `prj_detail` WHERE `stdValue` = '不得检出')            AS prj_detail_jt2_items,
       (SELECT COUNT(DISTINCT `item_name`) FROM `product_lib_item`
        WHERE `item_name` IN (
            SELECT DISTINCT TRIM(SUBSTRING_INDEX(`testItem`, ',', 1))
            FROM `prj_detail` WHERE `stdValue` = '不得检出'))        AS matched_in_new_lib;

-- =============================================================================
-- 4. 🔴 fail-loud 断言（T-905 硬性要求）
--    独立重新推导「应然」，与库中「实然」逐行比对：
--      · drift   = judge_type 与 std_value 形态推导值不一致的行数（含 NULL 行）
--      · outside = judge_type 落在白名单 {1,2,3} 之外的行数
--    任一 > 0 即 SIGNAL 报错并中止脚本 —— 绝不静默通过。
--    （独立重算是刻意的：它是一次真正的交叉校验，而非复用第 1 段的计算结果。）
-- =============================================================================
DELIMITER $$
DROP PROCEDURE IF EXISTS `_v3_assert_judge_type_consistent`$$
CREATE PROCEDURE `_v3_assert_judge_type_consistent`()
BEGIN
    DECLARE v_total   INT DEFAULT 0;
    DECLARE v_drift   INT DEFAULT 0;
    DECLARE v_outside INT DEFAULT 0;
    DECLARE v_msg     VARCHAR(512) DEFAULT '';

    SELECT COUNT(*) INTO v_total FROM `product_lib_item`;

    SELECT COUNT(*) INTO v_drift
      FROM `product_lib_item`
     WHERE NOT (`judge_type` <=> CASE
                    WHEN TRIM(TRAILING '*' FROM TRIM(IFNULL(`std_value`, '')))
                         REGEXP '^[0-9]+(\\.[0-9]+)?$' THEN 1
                    WHEN `std_value` LIKE '%不得检出%' OR `std_value` LIKE '%不得使用%' THEN 2
                    ELSE 3
                END);

    SELECT COUNT(*) INTO v_outside FROM `product_lib_item` WHERE `judge_type` NOT IN (1, 2, 3);

    IF v_drift > 0 OR v_outside > 0 THEN
        -- 先输出明细结果集（便于定位），再用短消息中止。
        -- 注意：SIGNAL 的 MESSAGE_TEXT 上限为 128 字符，故此处刻意精简，
        -- 人类的排查信息放在上面的结果集与脚本头部注释里。
        SELECT '❌ V3 fail-loud 断言失败' AS v3_assert_result,
               v_total AS 总行数, v_drift AS 形态不一致行数, v_outside AS 白名单外行数;
        SET v_msg = CONCAT('V3 fail-loud: judge_type 形态校验未通过 (drift=',
                           v_drift, ', outside=', v_outside, ')');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT CONCAT('✅ V3 口径校验通过：', v_total,
                  ' 行 judge_type 全部符合白名单口径（drift=0，越界=0）') AS v3_assert_result;
END$$
DELIMITER ;

CALL `_v3_assert_judge_type_consistent`();
DROP PROCEDURE IF EXISTS `_v3_assert_judge_type_consistent`;
