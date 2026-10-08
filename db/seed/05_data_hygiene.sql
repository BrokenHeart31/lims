-- =============================================================================
-- 05_data_hygiene.sql —— 数据卫生修正（F22 + F24，幂等可重跑）
-- =============================================================================
-- 依据：docs/test/2026-10-07-fullflow-test-report.md（F22/F24）+ _logs/f17-analysis-2026-10-08.md
--   · F22-a：花鲢「菌落总数」结果串行 —— 检验结果错填为「色泽正常」。
--   · F22-b：花鲢「孔雀石绿」未维护最低检出限 → 单项长期「待判定」（放行红线误报）。
--   · F24  ：陈旧 TEST-* 样品（停留 S10/S20 500+ 小时）长期污染在检视图，清理之。
--
-- 原则：
--   · **幂等**：全部用「目标态赋值 + 变更守卫」/「按条件删除」，可反复重跑、二次为 no-op。
--   · **不硬编码 id**：一律按 样品编号 + 项目名（F22）/ 编号前缀 + 状态（F24）定位；
--     JOIN sample_info 收敛范围，绝不按自增 id 操作。
--   · **不删演示数据**：F24 只命中 `TEST-%`（不含 `TEST2-`），`DEMO-*` 与非 TEST-* 数据一律保留。
--
-- ⚠️ 本脚本属「数据修正」，请在**业务低峰**执行，执行前先备份；
--    建议用 mysql 客户端按序执行（SET NAMES utf8mb4；无需 DELIMITER）。
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- 0. BEFORE 基线（人工核对；TEST-* 目标行预期 7 条）
-- =============================================================================
SELECT 'BEFORE TEST-* 在检样品数' AS metric, COUNT(*) AS cnt
  FROM `sample_info`
 WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20)
UNION ALL
SELECT 'BEFORE 花鲢菌落总数项(参考性)', COUNT(*)
  FROM `sample_item` i JOIN `sample_info` s ON s.`id` = i.`sample_id`
 WHERE s.`sample_no` = 'JK(2026)-SA-001' AND i.`item_name` = '菌落总数'
UNION ALL
SELECT 'BEFORE 花鲢孔雀石绿项(缺检出限)', COUNT(*)
  FROM `sample_item` i JOIN `sample_info` s ON s.`id` = i.`sample_id`
 WHERE s.`sample_no` = 'JK(2026)-SA-001' AND i.`item_name` = '孔雀石绿'
   AND (i.`lower_limit` IS NULL OR TRIM(i.`lower_limit`) = '');

-- =============================================================================
-- 1. F22-a：花鲢「菌落总数」修正为可判定的限量项 + 结果改正
--    目标态：judge_type=1（限量比较）、std_value='50000'、is_reference=0；
--            结果 test_value='1200'、conclusion=1（合格）、conclusion_source=1（引擎）。
-- =============================================================================
UPDATE `sample_item` i
JOIN `sample_info` s ON s.`id` = i.`sample_id`
SET i.`judge_type`   = 1,
    i.`std_value`    = '50000',
    i.`is_reference` = 0,
    i.`updated_by`   = 'hygiene-05',
    i.`updated_at`   = NOW()
WHERE s.`sample_no` = 'JK(2026)-SA-001'
  AND i.`item_name` = '菌落总数'
  -- 变更守卫：已是目标态则跳过（幂等）
  AND (i.`judge_type` <> 1
       OR IFNULL(i.`std_value`, '') <> '50000'
       OR IFNULL(i.`is_reference`, 0) <> 0);

UPDATE `sample_result` r
JOIN `sample_info` s ON s.`id` = r.`sample_id`
SET r.`test_value`        = '1200',
    r.`conclusion`        = 1,
    r.`conclusion_source` = 1,
    r.`judge_basis`       = '限量值 50000，实测 1200 ≤ 限量，判定合格',
    r.`updated_by`        = 'hygiene-05',
    r.`updated_at`        = NOW()
WHERE s.`sample_no` = 'JK(2026)-SA-001'
  AND r.`item_name` = '菌落总数'
  AND (IFNULL(r.`test_value`, '') <> '1200'
       OR IFNULL(r.`conclusion`, 0) <> 1
       OR IFNULL(r.`conclusion_source`, 0) <> 1);

-- =============================================================================
-- 2. F22-b：花鲢「孔雀石绿」补最低检出限（0.5）→ 由「待判定」转为「合格」
--    目标态：lower_limit='0.5'；结果 conclusion=1、conclusion_source=1、
--            依据文案与引擎口径一致（实测低于检出限视同未检出）。
-- =============================================================================
UPDATE `sample_item` i
JOIN `sample_info` s ON s.`id` = i.`sample_id`
SET i.`lower_limit` = '0.5',
    i.`updated_by`  = 'hygiene-05',
    i.`updated_at`  = NOW()
WHERE s.`sample_no` = 'JK(2026)-SA-001'
  AND i.`item_name` = '孔雀石绿'
  AND i.`judge_type` = 2
  AND (i.`lower_limit` IS NULL OR TRIM(i.`lower_limit`) = '');

UPDATE `sample_result` r
JOIN `sample_info` s ON s.`id` = r.`sample_id`
SET r.`conclusion`        = 1,
    r.`conclusion_source` = 1,
    r.`judge_basis`       = '实测 0.01 低于最低检出限 0.5，视同未检出，判定合格',
    r.`updated_by`        = 'hygiene-05',
    r.`updated_at`        = NOW()
WHERE s.`sample_no` = 'JK(2026)-SA-001'
  AND r.`item_name` = '孔雀石绿'
  AND (IFNULL(r.`conclusion`, 0) <> 1
       OR IFNULL(r.`conclusion_source`, 0) <> 1);

-- 备注：花鲢样品整体结论仍为 2（不合格，另由「恩诺沙星 / 镉」不合规定），
--       上述两项修正不改变整体结论，无需回写 sample_info.conclusion。

-- =============================================================================
-- 3. F24：清理陈旧 TEST-* 样品（停留 S10/S20）及其全部关联数据
--    目标：sample_no LIKE 'TEST-%' AND status IN (10,20)（预期 7 条）。
--    子表删除顺序：先删下游（结果/明细/流水/回退/留档），最后删样品主表。
--    ⚠️ 关联范围按 sample_id（经 sample_info 子查询）收敛，不硬编码 id、不误伤 DEMO-*。
-- =============================================================================

-- 3.1 检验结果
DELETE r FROM `sample_result` r
 WHERE r.`sample_id` IN (
        SELECT id FROM `sample_info`
         WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20));

-- 3.2 检测单项
DELETE i FROM `sample_item` i
 WHERE i.`sample_id` IN (
        SELECT id FROM `sample_info`
         WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20));

-- 3.3 审核签发流水
DELETE a FROM `sample_audit_log` a
 WHERE a.`sample_id` IN (
        SELECT id FROM `sample_info`
         WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20));

-- 3.4 状态流水
DELETE l FROM `sample_status_log` l
 WHERE l.`sample_id` IN (
        SELECT id FROM `sample_info`
         WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20));

-- 3.5 回退记录
DELETE rb FROM `sample_rollback` rb
 WHERE rb.`sample_id` IN (
        SELECT id FROM `sample_info`
         WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20));

-- 3.6 失效/修订留档（该表一般只增不删；此处仅为彻底清理测试残留）
DELETE ar FROM `sample_data_archive` ar
 WHERE ar.`sample_id` IN (
        SELECT id FROM `sample_info`
         WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20));

-- 3.7 样品主表（最后删除，作为子表子查询的依据仍可用）
DELETE s FROM `sample_info` s
 WHERE s.`sample_no` LIKE 'TEST-%' AND s.`status` IN (10, 20);

-- 3.8 采样单导入批次（⚠️ 无硬外键指向样品，按文件名/标记做 best-effort 关联）
--     判据：file_name 或 file_marker 含 'TEST'（造测数据文件命名约定）。
--     该步骤建议执行前人工核对下面这条 SELECT 的结果集再确认。
SELECT '待清理导入批次（人工核对）' AS review, b.`id`, b.`file_marker`, b.`file_name`
  FROM `sample_import_batch` b
 WHERE b.`file_name` LIKE '%TEST%' OR b.`file_marker` LIKE '%TEST%';

DELETE b FROM `sample_import_batch` b
 WHERE b.`file_name` LIKE '%TEST%' OR b.`file_marker` LIKE '%TEST%';

-- =============================================================================
-- 4. AFTER 复核（人工核对；TEST-* 在检样品应归零）
-- =============================================================================
SELECT 'AFTER TEST-* 在检样品数' AS metric, COUNT(*) AS cnt
  FROM `sample_info`
 WHERE `sample_no` LIKE 'TEST-%' AND `status` IN (10, 20)
UNION ALL
SELECT 'AFTER TEST-* 明细残留', COUNT(*)
  FROM `sample_item` i
 WHERE i.`sample_id` NOT IN (SELECT id FROM `sample_info`)
UNION ALL
SELECT 'AFTER 花鲢菌落总数(std_value)', COUNT(*)
  FROM `sample_item` i JOIN `sample_info` s ON s.`id` = i.`sample_id`
 WHERE s.`sample_no` = 'JK(2026)-SA-001' AND i.`item_name` = '菌落总数'
   AND i.`std_value` = '50000' AND i.`judge_type` = 1 AND i.`is_reference` = 0
UNION ALL
SELECT 'AFTER 花鲢孔雀石绿(lower_limit=0.5)', COUNT(*)
  FROM `sample_item` i JOIN `sample_info` s ON s.`id` = i.`sample_id`
 WHERE s.`sample_no` = 'JK(2026)-SA-001' AND i.`item_name` = '孔雀石绿'
   AND i.`lower_limit` = '0.5';

-- DEMO-* 保留确认（应 > 0，未被误删）
SELECT 'AFTER DEMO-* 样品数（保留确认）' AS metric, COUNT(*) AS cnt
  FROM `sample_info`
 WHERE `sample_no` LIKE 'DEMO-%';
