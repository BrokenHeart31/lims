-- =============================================================================
-- V3__correct_product_lib_item_judge_type.sql  judge_type 一次性订正（D5 裁决）
-- 目标：new.product_lib_item.judge_type 按 std_value 文本形态重判，输出 before/after 分布
-- =============================================================================
-- 裁决依据：docs/knowledge/2026-09-11-judge-engine-whitelist.md D5
--   「在 T-401 由 GLM 做一次性数据订正脚本（不做运行时回写）：按 stdValue 文本形态重判
--     judge_type（含"不得检出/不得使用"→2；非数值纯文本→3），脚本须输出 before/after 分布统计。
--     判定引擎运行时只读 product_lib_item.judge_type，禁止依赖旧 prj_detail。」
--
-- 🔴 实测结论（本轮 GLM 全库核对，2026-09-11）：
--   本脚本在当前数据上为 **零变更（no-op）**，且这不是脚本缺陷，而是数据事实：
--   - product_lib_item 3728 行，std_value **100% 为纯数值或数值+`*`**（如 '0.5'、'0.1*'）；
--   - 无一条含「不得检出」「不得使用」，无 `--`，无非数值纯文本；
--   - 故 judge_type 推导结果全部为 1，jt2/jt3 计数天然为 0。
--   根因：新表 product_lib_item 的唯一数据源是旧 `lib` 表（项目标准库，农残 GB 2763-2021 体系）。
--   旧 `prj_detail`（检验记录明细，463 行）中确有 220 条 `不得检出`、52 条 `--`，
--   但那是**兽药残留等另一套标准体系的检验项目**（硝基呋喃类代谢物/诺氟沙星/恩诺沙星/
--   孔雀石绿/氯霉素），这些项目名在 product_lib_item 中**一个都不存在**（0/5 匹配），
--   即 `prj_detail` 与 `lib` 不同源，无法用于「订正」lib 的行。
--
-- 因此本脚本的定位调整为：
--   ① 作为**可重跑的口径校验器**——任何时候 product_lib_item 被扩充（如后续补充兽残类标准库），
--      重跑本脚本即可将 judge_type 归一化到白名单口径；
--   ② 作为**当前零变更的证据**——输出 before/after 分布，证明 jt2/jt3 为 0 是数据事实而非漏迁。
--   ⚠️ 若后续确需在系统中支持「不得检出」类项目，应走「标准库数据补录」而非本脚本。
--
-- ⚠️ 前置：已执行 V1（product_lib_item 已有数据）。
-- ⚠️ 幂等：只更新 judge_type 与 before 值不同的行。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 0. BEFORE：judge_type 分布 + 按形态分类的应然分布（诊断用）
--    判据与 V1 保持一致，并补上白名单第 5 形态（`--`）：
--      去 `*` 后为纯数值               → 1 限量比较
--      含「不得检出」或「不得使用」     → 2 不得检出/不得使用
--      其余（`--` 占位 / 非数值纯文本） → 3 文本/感官人工
-- -----------------------------------------------------------------------------
SELECT 'BEFORE judge_type=1' AS metric, COUNT(*) AS cnt FROM `product_lib_item` WHERE `judge_type` = 1
UNION ALL SELECT 'BEFORE judge_type=2', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 2
UNION ALL SELECT 'BEFORE judge_type=3', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 3
UNION ALL SELECT 'BEFORE 其他值',       COUNT(*) FROM `product_lib_item` WHERE `judge_type` NOT IN (1, 2, 3)
UNION ALL SELECT 'BEFORE 总数',         COUNT(*) FROM `product_lib_item`;

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
-- 1. 订正：按白名单口径重判 judge_type（幂等，只改不一致行）
-- -----------------------------------------------------------------------------
UPDATE `product_lib_item`
SET `judge_type` = CASE
        WHEN TRIM(TRAILING '*' FROM TRIM(IFNULL(`std_value`, '')))
             REGEXP '^[0-9]+(\\.[0-9]+)?$' THEN 1
        WHEN `std_value` LIKE '%不得检出%' OR `std_value` LIKE '%不得使用%' THEN 2
        ELSE 3
    END,
    `updated_by` = 'migration-v3',
    `updated_at` = NOW()
WHERE `judge_type` <> CASE
        WHEN TRIM(TRAILING '*' FROM TRIM(IFNULL(`std_value`, '')))
             REGEXP '^[0-9]+(\\.[0-9]+)?$' THEN 1
        WHEN `std_value` LIKE '%不得检出%' OR `std_value` LIKE '%不得使用%' THEN 2
        ELSE 3
    END;

-- -----------------------------------------------------------------------------
-- 2. AFTER：分布复核（应与 BEFORE 完全一致 → 证明零变更）
-- -----------------------------------------------------------------------------
SELECT 'AFTER judge_type=1' AS metric, COUNT(*) AS cnt FROM `product_lib_item` WHERE `judge_type` = 1
UNION ALL SELECT 'AFTER judge_type=2', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 2
UNION ALL SELECT 'AFTER judge_type=3', COUNT(*) FROM `product_lib_item` WHERE `judge_type` = 3
UNION ALL SELECT 'AFTER 其他值',       COUNT(*) FROM `product_lib_item` WHERE `judge_type` NOT IN (1, 2, 3)
UNION ALL SELECT 'AFTER 总数',         COUNT(*) FROM `product_lib_item`;

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

-- -----------------------------------------------------------------------------
-- 4. 兜底断言：不得存在白名单外形态（预期全为 0）
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS 白名单外形态残留 FROM `product_lib_item`
WHERE `judge_type` NOT IN (1, 2, 3);
