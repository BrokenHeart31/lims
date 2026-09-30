-- =============================================================================
-- V11__rollback_batch_and_menu.sql
-- 2026-09-30 增量：① 回退机制改造为「环节内嵌 + 可选目标步（跨级链式） + 批量」
--                ② 移除独立「流程回溯」功能区（页面 / 路由 / 侧栏菜单）
-- =============================================================================
-- 面向存量库（活库 lims，已有业务数据与已有 RBAC）；**幂等，可重复执行**。
--   · 加列 / 加索引前先查 information_schema，已存在则跳过；
--   · 菜单清理用 DELETE / UPDATE 且带 WHERE，重复执行结果一致。
-- 全新部署请用 db/init/10_rollback_tables.sql（已同步含本次新列）+ db/seed/01_rbac_seed.sql。
--
-- 一、为什么加 `batch_no` / `step_count`（不变式③的重新定义）
-- -----------------------------------------------------------------------------
-- 改造前：回退只允许「逐级」（每次一步），故不变式③是「一次回退恰好 1 条 event_type=4 流水」。
-- 改造后：用户可选**任意可达目标步**，跨级由服务端沿 ROLLBACK 白名单逐级链式执行
--        （S40→S10 = S40→S30→S20→S10）。此时「一次回退」与「一条流水」不再一一对应：
--           · `sample_rollback` 仍**整批只落 1 行**（from=起点，to=最终目标步，step_count=级数）；
--           · `sample_status_log` **每级各 1 条** event_type=4 流水，靠 `batch_no` 圈成同一批次。
--        故不变式③重定义为：「一次回退 = 1 个回退批次（sample_rollback）+ 每级各 1 条状态流水」。
--        `batch_no` 是这两张表把「一次用户操作」还原出来的**唯一关联键**（rollback_id 也能关联，
--        但 batch_no 让「批次」成为一等公民，便于按批次聚合检索与审计对账）。
-- 二、为什么删掉菜单 13 却保留权限位 131~134
-- -----------------------------------------------------------------------------
-- 回退能力下沉到各业务页面内嵌后，「流程回溯」不再是一个独立页面，侧栏不得再有入口
-- （否则出现「菜单点了 404」的死链，见 DECISIONS 2026-09-13 同源产出原则）。
-- 但 131 rollback:view / 132 rollback:execute / 133 rollback:sensitive / 134 report:void
-- 是**代码里正在使用的权限标识**（@PreAuthorize 与服务层二次校验），删掉会让 R1/R2/R3 全部 403。
-- 故：删「目录节点 13」；把 4 个按钮权限位提升为根级 + visible=0（按钮行本就不进导航树，
-- 见 SysMenuMapper 只取 menu_type IN (1,2)；visible=0 使其在菜单管理页也不作为入口出现）。
-- =============================================================================

SET NAMES utf8mb4;

-- =============================================================================
-- A. sample_status_log：新增 batch_no
-- =============================================================================
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_status_log' AND COLUMN_NAME = 'batch_no');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_status_log` ADD COLUMN `batch_no` VARCHAR(32) DEFAULT NULL
     COMMENT ''回退批次号（跨级回退的各级流水共用，一次用户操作=一个批次）'' AFTER `rollback_id`',
  'SELECT ''sample_status_log.batch_no 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_status_log' AND INDEX_NAME = 'idx_ssl_batch_no');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `sample_status_log` ADD INDEX `idx_ssl_batch_no` (`batch_no`)',
  'SELECT ''idx_ssl_batch_no 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================================================================
-- B. sample_rollback：新增 batch_no、step_count（并回填存量行的批次号）
-- =============================================================================
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_rollback' AND COLUMN_NAME = 'batch_no');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_rollback` ADD COLUMN `batch_no` VARCHAR(32) DEFAULT NULL
     COMMENT ''回退批次号（一次回退操作=一个批次）'' AFTER `sample_no`',
  'SELECT ''sample_rollback.batch_no 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_rollback' AND COLUMN_NAME = 'step_count');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_rollback` ADD COLUMN `step_count` INT NOT NULL DEFAULT 1
     COMMENT ''本次回退的级数 1=单级 >1=跨级链式'' AFTER `to_status`',
  'SELECT ''sample_rollback.step_count 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_rollback' AND INDEX_NAME = 'idx_sr_batch_no');
SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE `sample_rollback` ADD INDEX `idx_sr_batch_no` (`batch_no`)',
  'SELECT ''idx_sr_batch_no 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：存量回退记录均为「单级」（改造前只允许逐级），
--   批次号取 `RB-LEGACY-<id>`（确定性、可重跑、不与新生成格式冲突）。
UPDATE `sample_rollback`
   SET `batch_no` = CONCAT('RB-LEGACY-', `id`)
 WHERE `batch_no` IS NULL;

-- 存量流水（event_type=4/5）按 rollback_id 回填批次号，使时间线的批次聚合对存量数据同样成立。
UPDATE `sample_status_log` l
  JOIN `sample_rollback` r ON r.`id` = l.`rollback_id`
   SET l.`batch_no` = r.`batch_no`
 WHERE l.`batch_no` IS NULL AND l.`rollback_id` IS NOT NULL;

-- =============================================================================
-- C. 菜单清理：移除独立「流程回溯」目录节点，权限位提升为隐藏根级按钮
-- =============================================================================
-- C1. 先清授权行（菜单删除后残留的 sys_role_menu 会变成指向不存在菜单的孤儿勾选态）
DELETE FROM `sys_role_menu` WHERE `menu_id` = 13;

-- C2. 权限位提升为根级 + 隐藏（按钮行不进 /me 导航树；visible=0 亦不作为入口出现）
UPDATE `sys_menu`
   SET `parent_id` = 0,
       `visible`   = 0,
       `updated_by` = 'V11',
       `updated_at` = NOW()
 WHERE `id` IN (131, 132, 133, 134);

-- C3. 删除目录节点 13（独立功能区就此消失：无页面、无路由、无侧栏入口）
DELETE FROM `sys_menu` WHERE `id` = 13;

-- =============================================================================
-- D. 校验 SELECT（人工核对；fail-loud：数字与注释不一致即人工介入）
-- =============================================================================
-- 应为 1（列已就位）
SELECT 'sample_status_log.batch_no' AS item, COUNT(*) AS should_be_1
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_status_log' AND COLUMN_NAME = 'batch_no';
-- 应为 2（batch_no + step_count）
SELECT 'sample_rollback.new_cols' AS item, COUNT(*) AS should_be_2
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_rollback'
   AND COLUMN_NAME IN ('batch_no','step_count');
-- 应为 0：独立「流程回溯」菜单已不存在
SELECT 'menu_13_removed' AS item, COUNT(*) AS should_be_0 FROM `sys_menu` WHERE `id` = 13;
-- 应为 0：无指向菜单 13 的孤儿授权
SELECT 'role_menu_13_orphan' AS item, COUNT(*) AS should_be_0 FROM `sys_role_menu` WHERE `menu_id` = 13;
-- 应为 4（131~134 全部保留为根级隐藏权限位）
SELECT 'rollback_perm_rows' AS item, COUNT(*) AS should_be_4
  FROM `sys_menu` WHERE `id` IN (131,132,133,134) AND `parent_id` = 0 AND `visible` = 0;
-- 应为 0（没有任何非按钮的菜单还带 /rollback 路径）
SELECT 'rollback_path_left' AS item, COUNT(*) AS should_be_0
  FROM `sys_menu` WHERE `path` LIKE '%/rollback%';
