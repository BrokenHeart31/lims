-- =============================================================================
-- 01_basic_tables.sql  基础数据表建表脚本（新表规范，AGENTS 6.1）
-- 对应任务：T-103（豆包建，Copilot 终审）+ T-104 迁移目标表
-- 约定：InnoDB / utf8mb4_general_ci；id BIGINT 自增；审计四字段；deleted 逻辑删除
-- 说明：
--   - product_lib / product_lib_item 属 T-401（S 级，Copilot）领域，
--     已经 Copilot 终审定稿（2026-09-10）：增补 judge_type 判定类型字段，
--     供 T-601 自动判定引擎直接消费（AGENTS 7.3 规则 1/2/3 的分类落库）。
--   - 本文件为基线建表，先于 db/migrations/V1__import_legacy_data.sql 执行。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 判定依据表（新）：源 basisname
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `basis`;
CREATE TABLE `basis` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(100) NOT NULL COMMENT '标准号，如 GB 2763-2021',
  `name`        VARCHAR(255) NOT NULL COMMENT '标准名称，如《食品安全国家标准 食品中农药最大残留限量》',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_basis_code` (`code`),
  KEY `idx_basis_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='判定依据（标准）';

-- -----------------------------------------------------------------------------
-- 客户/受检单位表（新）：源 customer（旧）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`            VARCHAR(100) NOT NULL COMMENT '单位名称',
  `contact_person`  VARCHAR(50)  DEFAULT NULL COMMENT '联系人',
  `leader`          VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
  `contact_mobile`  VARCHAR(20)  DEFAULT NULL COMMENT '联系人手机',
  `leader_mobile`   VARCHAR(20)  DEFAULT NULL COMMENT '负责人手机',
  `address`         VARCHAR(200) DEFAULT NULL COMMENT '单位地址',
  `business_type`   VARCHAR(100) DEFAULT NULL COMMENT '主营类别',
  `bank`            VARCHAR(100) DEFAULT NULL COMMENT '开户行',
  `bank_account`    VARCHAR(50)  DEFAULT NULL COMMENT '银行账号',
  `status`          VARCHAR(20)  DEFAULT NULL COMMENT '状态',
  `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`      DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`      VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`      DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_customer_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户/受检单位';

-- -----------------------------------------------------------------------------
-- 部门表（新）：源 dept（旧，空表）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `dept`;
CREATE TABLE `dept` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dept_code`   VARCHAR(20)  NOT NULL COMMENT '部门编号',
  `dept_name`   VARCHAR(50)  NOT NULL COMMENT '部门名称',
  `leader`      VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门';

-- -----------------------------------------------------------------------------
-- 项目标准库-产品头（新）：源 lib.productId
-- 【Copilot 终审定稿】product_name 旧库无源数据，留 NULL 待 T-401 补全
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_lib`;
CREATE TABLE `product_lib` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_code` VARCHAR(20) NOT NULL COMMENT '产品/商品库编号，源 lib.productId',
  `product_name` VARCHAR(100) DEFAULT NULL COMMENT '产品名称（待 T-401 补全）',
  `category`    VARCHAR(100) DEFAULT NULL COMMENT '食品大类',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目标准库-产品';

-- -----------------------------------------------------------------------------
-- 项目标准库-检测项目明细（新）：源 lib
-- 【Copilot 终审定稿】judge_type 驱动 T-601 自动判定引擎：
--   1=限量比较（std_value 为数值，判定规则：检验值 ≤ 限量 → 合格）
--   2=不得检出/不得使用（未检出→合格，检出→不合格；低于 lower_limit 按未检出）
--   3=文本/感官（检验员人工选合格/不合格）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `product_lib_item`;
CREATE TABLE `product_lib_item` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_lib_id`  BIGINT       NOT NULL COMMENT '关联 product_lib.id',
  `item_order`      INT          NOT NULL COMMENT '顺序号，源 lib.xh',
  `item_name`       VARCHAR(255) NOT NULL COMMENT '检测项目名称，源 testItem 逗号前段',
  `unit`            VARCHAR(50)  DEFAULT NULL COMMENT '计量单位，源 testItem 逗号后段',
  `basis_code`      VARCHAR(100) DEFAULT NULL COMMENT '判定依据标准号，源 lib.basis',
  `methods`         VARCHAR(500) DEFAULT NULL COMMENT '检验方法（多个以 # 分隔，源 mathod 去尾#）',
  `std_value`       VARCHAR(50)  DEFAULT NULL COMMENT '限量值（judge_type=1 时为数值文本；参考项已去 *）',
  `judge_type`      TINYINT      NOT NULL DEFAULT 1 COMMENT '判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工',
  `is_reference`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否参考性限量 0=否 1=是（源 stdValue 以 * 结尾）',
  `lower_limit`     VARCHAR(20)  DEFAULT NULL COMMENT '最低检出限，源 num_lowerst',
  `method_note`     VARCHAR(100) DEFAULT NULL COMMENT '方法备注，源 method_note',
  `created_by`      VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`      DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`      VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`      DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_pli_product` (`product_lib_id`),
  KEY `idx_pli_basis` (`basis_code`),
  KEY `idx_pli_item_name` (`item_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目标准库-检测项目明细';

SET FOREIGN_KEY_CHECKS = 1;
