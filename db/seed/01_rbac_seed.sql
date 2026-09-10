-- =============================================================================
-- 01_rbac_seed.sql  RBAC 基础种子数据（T-101/T-102 配套）
-- 内容：部门 6 + 角色 4 + 用户 6 + 菜单/权限 + 角色分配 + 用户分配
-- 预置账号（密码 = 账号名，BCrypt；AGENTS 8.1 + DECISIONS 2026-09-10）：
--   nj001 综合管理(R100) / nj002 样品登记员(R1) / nj003 任务管理员(R2)
--   njna000 农残 / njxa000 畜残 / njsa000 水产（R3 共享检验员）
-- 执行前置：db/init/01_basic_tables.sql + 02_rbac_tables.sql
-- 可重复执行：先按固定 id 清场再插入。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 1. 部门（dept 业务表，含 parent_id 层级；旧 dept 0 行无迁移冲突）
-- -----------------------------------------------------------------------------
DELETE FROM `dept` WHERE `id` BETWEEN 1 AND 6;
INSERT INTO `dept` (`id`, `parent_id`, `dept_code`, `dept_name`, `leader`, `remark`,
                    `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(1, 0, 'CENTER', '食品质量检验测试中心', NULL, '顶级部门', 'seed', NOW(), 'seed', NOW(), 0),
(2, 1, 'ZH',     '综合管理部',          NULL, '审核签发/权限管理', 'seed', NOW(), 'seed', NOW(), 0),
(3, 1, 'SL',     '样品受理科',          NULL, '样品登记', 'seed', NOW(), 'seed', NOW(), 0),
(4, 1, 'NA',     '农残检验室',          NULL, '农产品检验', 'seed', NOW(), 'seed', NOW(), 0),
(5, 1, 'XA',     '畜残检验室',          NULL, '畜产品检验', 'seed', NOW(), 'seed', NOW(), 0),
(6, 1, 'SA',     '水产检验室',          NULL, '水产品检验', 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 2. 角色（AGENTS 8.1）
-- -----------------------------------------------------------------------------
DELETE FROM `sys_role` WHERE `id` BETWEEN 1 AND 4;
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`,
                        `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(1, 'R100', '综合管理',   '审核签发/权限管理/全部查询', 'seed', NOW(), 'seed', NOW(), 0),
(2, 'R1',   '样品登记员', '采样单导入/登记确认/样品查询', 'seed', NOW(), 'seed', NOW(), 0),
(3, 'R2',   '任务管理员', '监抽任务/项目分解/任务安排/资质维护', 'seed', NOW(), 'seed', NOW(), 0),
(4, 'R3',   '检验员',     '检验数据录入/结果导出（含共享检验员账号）', 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 3. 用户（密码 = 账号名的 BCrypt 密文）
-- -----------------------------------------------------------------------------
DELETE FROM `sys_user` WHERE `id` BETWEEN 1 AND 6;
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `dept_id`, `status`, `remark`,
                        `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
(1, 'nj001',   '$2a$10$4mxmwQ6AzocBr7cbGPeDluJCJqqZup7SluAgug8CbzeSJQf1b.J5m', '系统管理员',     2, 1, '综合管理',   'seed', NOW(), 'seed', NOW(), 0),
(2, 'nj002',   '$2a$10$xbFkshxf9azH0sqphxnikefL9w4QboU4mGfhzmDkmWfpjSIJDEsI2', '样品登记员',     3, 1, '样品登记',   'seed', NOW(), 'seed', NOW(), 0),
(3, 'nj003',   '$2a$10$guSus/NV6ZY2ob7JSW5XCeVpPzGOku8IHWmMz4gHmlACHxLyZ9186', '任务管理员',     2, 1, '任务管理',   'seed', NOW(), 'seed', NOW(), 0),
(4, 'njna000', '$2a$10$yvYR7.UZkcrUHWKEiZ3vxOcSvgQ2X6RRK/cq9XGLvT0EQUgDpPbaq', '农残共享检验员', 4, 1, 'NA 共享账号', 'seed', NOW(), 'seed', NOW(), 0),
(5, 'njxa000', '$2a$10$LWCE.lPqLSoWzrvKw0.xMOy81PFDX/3E23b3Px6oL1uHNjAGy.w0K', '畜残共享检验员', 5, 1, 'XA 共享账号', 'seed', NOW(), 'seed', NOW(), 0),
(6, 'njsa000', '$2a$10$rznLdCX3UAB0fiC.Nu6izehwS1NSVeM1NX3fuugwreV.e4pnt4j.i', '水产共享检验员', 6, 1, 'SA 共享账号', 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 4. 菜单/权限（menu_type：1=目录 2=菜单 3=按钮；按钮 permission=AGENTS 8.2 标识）
--    固定 id，便于 sys_role_menu 分配与后续维护。
-- -----------------------------------------------------------------------------
DELETE FROM `sys_menu`;
INSERT INTO `sys_menu` (`id`, `parent_id`, `title`, `path`, `icon`, `menu_type`, `permission`, `sort_order`, `visible`,
                        `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
-- 工作台
(1, 0, '工作台', '/dashboard', 'Monitor', 2, NULL, 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 监抽任务（T-201）
(2, 0, '监抽任务', '/task', 'List', 2, NULL, 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(21, 2, '任务查询', NULL, NULL, 3, 'task:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(22, 2, '任务新建', NULL, NULL, 3, 'task:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(23, 2, '任务编辑', NULL, NULL, 3, 'task:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(24, 2, '任务删除', NULL, NULL, 3, 'task:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 样品登记（T-301）
(3, 0, '样品登记', '/sample/register', 'Document', 2, NULL, 3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(31, 3, '采样单导入', NULL, NULL, 3, 'sample:import',  1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(32, 3, '登记确认',   NULL, NULL, 3, 'sample:confirm', 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(33, 3, '样品查询',   NULL, NULL, 3, 'sample:query',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 项目分解（T-401）
(4, 0, '项目分解', '/item/decompose', 'Files', 2, NULL, 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(41, 4, '分解确认', NULL, NULL, 3, 'item:decompose', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 任务安排（T-501）
(5, 0, '任务安排', '/assign', 'User', 2, NULL, 5, 1, 'seed', NOW(), 'seed', NOW(), 0),
(51, 5, '安排确认', NULL, NULL, 3, 'assign:confirm',  1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(52, 5, '人工改派', NULL, NULL, 3, 'assign:reassign', 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 结果录入（T-601）
(6, 0, '结果录入', '/result/entry', 'EditPen', 2, NULL, 6, 1, 'seed', NOW(), 'seed', NOW(), 0),
(61, 6, '数据录入',   NULL, NULL, 3, 'result:entry',        1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(62, 6, '结果导出',   NULL, NULL, 3, 'result:export-excel', 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 报告管理（T-701/T-702）
(7, 0, '报告管理', '/report', 'Notebook', 1, NULL, 7, 1, 'seed', NOW(), 'seed', NOW(), 0),
(71, 7, '审核签发', '/report/audit', NULL, 2, NULL, 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(711, 71, '报告审核', NULL, NULL, 3, 'report:audit', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(712, 71, '报告签发', NULL, NULL, 3, 'report:sign',  2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(72, 7, '报告生成', '/report/generate', NULL, 2, NULL, 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(721, 72, '报告生成', NULL, NULL, 3, 'report:generate', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(722, 72, '报告打印', NULL, NULL, 3, 'report:print',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 查询统计（T-801）
(8, 0, '查询统计', '/query', 'Search', 1, NULL, 8, 1, 'seed', NOW(), 'seed', NOW(), 0),
(81, 8, '在检查询', '/query/testing', NULL, 2, NULL, 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(811, 81, '在检查询', NULL, NULL, 3, 'query:testing', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(82, 8, '历史查询', '/query/history', NULL, 2, NULL, 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(821, 82, '历史查询', NULL, NULL, 3, 'query:history', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(83, 8, '项目库查询', '/query/lib', NULL, 2, NULL, 3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(831, 83, '项目库查询', NULL, NULL, 3, 'base:lib:list', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 基础数据（T-103）
(9, 0, '基础数据', '/base', 'Coin', 1, NULL, 9, 1, 'seed', NOW(), 'seed', NOW(), 0),
(91, 9, '判定依据', '/base/basis', NULL, 2, NULL, 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(911, 91, '依据查询', NULL, NULL, 3, 'base:basis:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(912, 91, '依据新建', NULL, NULL, 3, 'base:basis:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(913, 91, '依据编辑', NULL, NULL, 3, 'base:basis:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(914, 91, '依据删除', NULL, NULL, 3, 'base:basis:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(92, 9, '客户管理', '/base/customer', NULL, 2, NULL, 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(921, 92, '客户查询', NULL, NULL, 3, 'base:customer:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(922, 92, '客户新建', NULL, NULL, 3, 'base:customer:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(923, 92, '客户编辑', NULL, NULL, 3, 'base:customer:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(924, 92, '客户删除', NULL, NULL, 3, 'base:customer:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(93, 9, '方法资质', '/base/tester-method', NULL, 2, NULL, 3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(931, 93, '资质查询', NULL, NULL, 3, 'base:tester-method:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(932, 93, '资质新建', NULL, NULL, 3, 'base:tester-method:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(933, 93, '资质编辑', NULL, NULL, 3, 'base:tester-method:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(934, 93, '资质删除', NULL, NULL, 3, 'base:tester-method:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 省平台上报（T-802）
(10, 0, '省平台上报', '/export/province', 'Upload', 2, NULL, 10, 1, 'seed', NOW(), 'seed', NOW(), 0),
(101, 10, '上报导出', NULL, NULL, 3, 'export:province', 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
-- 系统管理
(11, 0, '系统管理', '/sys', 'Setting', 1, NULL, 11, 1, 'seed', NOW(), 'seed', NOW(), 0),
(111, 11, '用户管理', '/sys/user', NULL, 2, NULL, 1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1111, 111, '用户查询', NULL, NULL, 3, 'sys:user:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1112, 111, '用户新建', NULL, NULL, 3, 'sys:user:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1113, 111, '用户编辑', NULL, NULL, 3, 'sys:user:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1114, 111, '用户删除', NULL, NULL, 3, 'sys:user:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(112, 11, '角色管理', '/sys/role', NULL, 2, NULL, 2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1121, 112, '角色查询', NULL, NULL, 3, 'sys:role:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1122, 112, '角色新建', NULL, NULL, 3, 'sys:role:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1123, 112, '角色编辑', NULL, NULL, 3, 'sys:role:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1124, 112, '角色删除', NULL, NULL, 3, 'sys:role:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(113, 11, '菜单管理', '/sys/menu', NULL, 2, NULL, 3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1131, 113, '菜单查询', NULL, NULL, 3, 'sys:menu:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1132, 113, '菜单新建', NULL, NULL, 3, 'sys:menu:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1133, 113, '菜单编辑', NULL, NULL, 3, 'sys:menu:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1134, 113, '菜单删除', NULL, NULL, 3, 'sys:menu:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(114, 11, '部门管理', '/sys/dept', NULL, 2, NULL, 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1141, 114, '部门查询', NULL, NULL, 3, 'sys:dept:list',   1, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1142, 114, '部门新建', NULL, NULL, 3, 'sys:dept:add',    2, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1143, 114, '部门编辑', NULL, NULL, 3, 'sys:dept:edit',   3, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1144, 114, '部门删除', NULL, NULL, 3, 'sys:dept:remove', 4, 1, 'seed', NOW(), 'seed', NOW(), 0),
(115, 11, '日志查看', '/sys/log', NULL, 2, NULL, 5, 1, 'seed', NOW(), 'seed', NOW(), 0),
(1151, 115, '日志查看', NULL, NULL, 3, 'log:view', 1, 1, 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 5. 角色-菜单分配
--    R100：全部菜单权限；R1：样品登记域；R2：任务/分解/安排/资质域；R3：结果录入域
-- -----------------------------------------------------------------------------
DELETE FROM `sys_role_menu`;
-- R100 综合管理：全部
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`id` FROM `sys_menu` m;
-- R1 样品登记员
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 1), (2, 3), (2, 31), (2, 32), (2, 33);
-- R2 任务管理员
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 1), (3, 2), (3, 21), (3, 22), (3, 23), (3, 24),
(3, 4), (3, 41), (3, 5), (3, 51), (3, 52),
(3, 9), (3, 93), (3, 931), (3, 932), (3, 933), (3, 934);
-- R3 检验员
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(4, 1), (4, 6), (4, 61), (4, 62);

-- -----------------------------------------------------------------------------
-- 6. 用户-角色分配
-- -----------------------------------------------------------------------------
DELETE FROM `sys_user_role`;
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1),   -- nj001   → R100
(2, 2),   -- nj002   → R1
(3, 3),   -- nj003   → R2
(4, 4),   -- njna000 → R3
(5, 4),   -- njxa000 → R3
(6, 4);   -- njsa000 → R3

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SELECT 'dept'         AS tbl, COUNT(*) AS cnt FROM `dept`
UNION ALL SELECT 'sys_role',      COUNT(*) FROM `sys_role`
UNION ALL SELECT 'sys_user',      COUNT(*) FROM `sys_user`
UNION ALL SELECT 'sys_menu',      COUNT(*) FROM `sys_menu`
UNION ALL SELECT 'sys_role_menu', COUNT(*) FROM `sys_role_menu`
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM `sys_user_role`;
