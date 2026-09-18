-- =============================================================================
-- db/seed/04_rbac_ai_rollback_seed.sql
-- RBAC **增量**种子：AI 助手（目录 12）+ 流程回溯（目录 13）的权限位与角色授权。
-- =============================================================================
-- 为什么单独抽一个「增量种子」而不是改 db/seed/01_rbac_seed.sql？
--   ⚠️ `01_rbac_seed.sql` **整体不可重跑**：它用显式 id 插入、且**无** ON DUPLICATE KEY
--      UPDATE / INSERT IGNORE / REPLACE，重跑会在第一行（dept id=1）就主键冲突报错。
--   活库（lims）已有既有业务数据与既有 RBAC，**绝不能**再跑 01 全量种子。
--   故把本次增量（AI + 回溯）的权限行与授权抽成本**幂等**脚本：全部 INSERT IGNORE，
--   可安全重复执行；`sys_menu` 主键 id / `sys_role_menu` 唯一键 (role_id,menu_id) 天然去重。
--
-- 内容（与 db/seed/01_rbac_seed.sql 中 12/121~124、13/131~134 逐字一致）：
--   · 目录 12「AI 助手」+ 4 个按钮权限：121 ai:kb:import / 122 ai:kb:query
--                                           123 ai:log:view  / 124 ai:chat
--   · 目录 13「流程回溯」+ 4 个按钮权限：131 rollback:view    / 132 rollback:execute
--                                           133 rollback:sensitive / 134 report:void
--   · 角色授权：R100(角色1) 全量；R1(2)/R2(3)/R3(4) 按 seed/01 口径。
--
-- 前置：db/enable/2026-09-18-enable-existing-db.sql（无关，本脚本只动 sys_* 表）。
-- 可重复执行：是（幂等）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1) 菜单 / 权限位（INSERT IGNORE：id 已存在则跳过，不覆盖既有行）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_menu`
  (`id`, `parent_id`, `title`, `path`, `icon`, `menu_type`, `permission`, `sort_order`, `visible`,
   `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
-- AI 助手（feature A，2026-09-17 增量）
(12,  0,  'AI 助手',    '/ai/kb',    'Monitor',   1, NULL,              12, 1, 'seed', NOW(), 'seed', NOW(), 0),
(121, 12, '标准库导入', NULL,        NULL,        3, 'ai:kb:import',     1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(122, 12, '标准库查询', NULL,        NULL,        3, 'ai:kb:query',      2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(123, 12, '会话审计',   NULL,        NULL,        3, 'ai:log:view',      3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(124, 12, 'AI 助手',    NULL,        NULL,        3, 'ai:chat',          4, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 流程回溯（feature B，2026-09-17 增量）
(13,  0,  '流程回溯',   '/rollback', 'Histogram', 2, NULL,              13, 1, 'seed', NOW(), 'seed', NOW(), 0),
(131, 13, '回溯查询',   NULL,        NULL,        3, 'rollback:view',      1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(132, 13, '常规回退',   NULL,        NULL,        3, 'rollback:execute',   2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(133, 13, '敏感回退',   NULL,        NULL,        3, 'rollback:sensitive', 3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(134, 13, '作废/召回',  NULL,        NULL,        3, 'report:void',        4, 1, 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 2) 角色-菜单授权（INSERT IGNORE：唯一键 (role_id, menu_id) 去重）
--    R100(1) 已在 seed/01 通过「SELECT 1, m.id FROM sys_menu m」全量授权；此处再补一次
--    以确保本增量新增的 12/13 对 R100 亦可见（若 01 已跑过则 R100 已有，IGNORE 跳过）。
-- -----------------------------------------------------------------------------
-- R100 综合管理：本增量目录/权限全量
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 12), (1, 121), (1, 122), (1, 123), (1, 124),
(1, 13), (1, 131), (1, 132), (1, 133), (1, 134);

-- R1 样品登记员：AI 助手 + 标准库查询 + 流程回溯（可纠错本阶段）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 12), (2, 121), (2, 122), (2, 124),
(2, 13), (2, 131), (2, 132);

-- R2 任务管理员（业务管理员）：AI 全量（含会话审计）+ 回溯全量（含敏感/作废）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 12), (3, 121), (3, 122), (3, 123), (3, 124),
(3, 13), (3, 131), (3, 132), (3, 133), (3, 134);

-- R3 检验员：AI 助手查标准 + 流程回溯（可回退修改）
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(4, 12), (4, 122), (4, 124),
(4, 13), (4, 131), (4, 132);

-- -----------------------------------------------------------------------------
-- 3) 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SELECT id, parent_id AS pid, title, permission, menu_type
  FROM `sys_menu` WHERE id IN (12,121,122,123,124,13,131,132,133,134) ORDER BY id;
SELECT role_id, COUNT(*) AS menu_cnt
  FROM `sys_role_menu` WHERE menu_id IN (12,121,122,123,124,13,131,132,133,134)
  GROUP BY role_id ORDER BY role_id;
SELECT 'ai_chat_roles' AS item, COUNT(*) AS cnt
  FROM `sys_role_menu` rm JOIN `sys_menu` m ON m.id = rm.menu_id
 WHERE m.permission = 'ai:chat';
