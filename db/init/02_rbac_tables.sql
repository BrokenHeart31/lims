-- =============================================================================
-- 02_rbac_tables.sql  RBAC 权限模型建表脚本（T-101，新表规范 AGENTS 6.1）
-- 模型：sys_user / sys_role / sys_menu / sys_user_role / sys_role_menu（五表）
--       + 部门复用业务表 dept（01_basic_tables.sql），本脚本补 parent_id 支撑
--         数据权限"本部门及下属部门"（AGENTS 8.3）。
-- 命名说明：user 为 MySQL 函数名，统一 sys_ 前缀（RuoYi 惯例），已记 DECISIONS。
-- 权限模型：用户→角色→菜单；menu_type=3(按钮) 的 permission 列即 AGENTS 8.2
--           权限标识（resource:action），@PreAuthorize 与 v-permission 共用。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 用户表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(32)  NOT NULL COMMENT '登录名/工号，如 nj001、njna000',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码密文（禁止出网）',
  `nickname`    VARCHAR(50)  NOT NULL COMMENT '姓名',
  `dept_id`     BIGINT       DEFAULT NULL COMMENT '所属部门 dept.id（数据权限用）',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=启用 0=停用',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户';

-- -----------------------------------------------------------------------------
-- 角色表（预置编码见 AGENTS 8.1：R100/R1/R2/R3）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code`   VARCHAR(32)  NOT NULL COMMENT '角色编码，如 R100、R3',
  `role_name`   VARCHAR(50)  NOT NULL COMMENT '角色名称，如 综合管理',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '职责描述',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色';

-- -----------------------------------------------------------------------------
-- 菜单/权限表（菜单树 + 按钮权限标识一体）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单 id，0=根',
  `title`       VARCHAR(50)  NOT NULL COMMENT '菜单/按钮标题',
  `path`        VARCHAR(200) DEFAULT NULL COMMENT '前端路由路径（目录/菜单）',
  `icon`        VARCHAR(50)  DEFAULT NULL COMMENT '图标',
  `menu_type`   TINYINT      NOT NULL COMMENT '类型 1=目录 2=菜单 3=按钮',
  `permission`  VARCHAR(100) DEFAULT NULL COMMENT '权限标识 resource:action（按钮必填）',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '同级排序',
  `visible`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示 1=是 0=否',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_sys_menu_parent` (`parent_id`),
  UNIQUE KEY `uk_sys_menu_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单/权限';

-- -----------------------------------------------------------------------------
-- 用户-角色关联
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT 'sys_user.id',
  `role_id` BIGINT NOT NULL COMMENT 'sys_role.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_ur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-角色';

-- -----------------------------------------------------------------------------
-- 角色-菜单关联（角色权限载体）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT NOT NULL COMMENT 'sys_role.id',
  `menu_id` BIGINT NOT NULL COMMENT 'sys_menu.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
  KEY `idx_rm_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色-菜单';

-- -----------------------------------------------------------------------------
-- 部门表补层级（数据权限：本部门及下属部门，AGENTS 8.3）
-- dept 业务表由 01_basic_tables.sql 建立，此处仅加列，保持向前兼容。
-- -----------------------------------------------------------------------------
ALTER TABLE `dept`
  ADD COLUMN `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父部门 id，0=顶级' AFTER `id`,
  ADD KEY `idx_dept_parent` (`parent_id`);

SET FOREIGN_KEY_CHECKS = 1;
