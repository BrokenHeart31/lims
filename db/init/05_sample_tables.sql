-- =============================================================================
-- 05_sample_tables.sql  样品登记表（T-301，新表规范 AGENTS 6.1）
-- 业务来源：业务说明书「四、检验业务流程之一：导入采样单Excel」
--           + 「样品登记信息维护与确认」
-- 字段与采样单 Excel 列头一一对应（22 列，数据自第 3 行起，A1 为文件标记）：
--   样品编号/样品名称/受检单位/抽样地址/收款人/费用/样品数量/项目名称/日期/
--   备注/采样者/生产单位/抽样基数/样品状态/规格型号/商标/样品等级/
--   原编号或生产日期/检验类别/要求完成日期/任务编号/任务批号
--
-- ⚠️ 表名决策（2026-09-11 GLM，已记 DECISIONS.md）：
--   样品表取名 **sample_info** 而非 sample —— `SAMPLE` 是 SQL 关键字（TABLESAMPLE），
--   与 MyBatis-Plus 分页插件所用的 JSqlParser 冲突，会导致 count SQL 优化失败
--   （实测 WARN: Encountered unexpected token "FROM"）。与本项目 sys_user（避开 user
--   关键字）同一处理原则；接口路径 /api/sample/* 与权限标识 sample:* 不受影响。
--
-- ⚠️ 状态字段决策（AGENTS 7.2 + DECISIONS 2026-09-10）：
--   status 强制 TINYINT，取值 = common/enums/SampleStatus 的 code
--   （S10=10 已登记 … S90=90 已出报告），禁止魔法数字、禁止私增/跳态。
--   导入落库即 S10；登记确认 S10→S20 必须经 SampleStatusTransition.assertTransition。
--
-- 执行前置：db/init/03_task_tables.sql（task_no 语义关联 supervise_task.task_no）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 样品登记表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_info`;
CREATE TABLE `sample_info` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_no`             VARCHAR(50)  NOT NULL COMMENT '样品编号（唯一，如 JK(2023)-SA-001）',
  `sample_name`           VARCHAR(255) DEFAULT NULL COMMENT '样品名称',
  `client_name`           VARCHAR(255) DEFAULT NULL COMMENT '受检单位',
  `sampling_address`      VARCHAR(255) DEFAULT NULL COMMENT '抽样地址',
  `payee`                 VARCHAR(50)  DEFAULT NULL COMMENT '收款人',
  `fee`                   DECIMAL(10,2) DEFAULT NULL COMMENT '费用',
  `sample_quantity`       VARCHAR(50)  DEFAULT NULL COMMENT '样品数量（文本，如 3kg）',
  `project_name`          VARCHAR(100) DEFAULT NULL COMMENT '项目名称（如 市级例行）',
  `sampling_date`         DATE         DEFAULT NULL COMMENT '采样日期（原「日期」列）',
  `remark`                VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `sampler`               VARCHAR(50)  DEFAULT NULL COMMENT '采样者',
  `manufacturer`          VARCHAR(255) DEFAULT NULL COMMENT '生产单位',
  `sampling_base`         VARCHAR(50)  DEFAULT NULL COMMENT '抽样基数',
  `sample_state`          VARCHAR(50)  DEFAULT NULL COMMENT '样品状态（如 鲜活）',
  `spec`                  VARCHAR(100) DEFAULT NULL COMMENT '规格型号',
  `brand`                 VARCHAR(100) DEFAULT NULL COMMENT '商标',
  `grade`                 VARCHAR(50)  DEFAULT NULL COMMENT '样品等级',
  `original_no`           VARCHAR(100) DEFAULT NULL COMMENT '原编号或生产日期',
  `inspect_type`          VARCHAR(50)  DEFAULT NULL COMMENT '检验类别（如 监督抽检）',
  `require_complete_date` DATE         DEFAULT NULL COMMENT '要求完成日期',
  `task_no`               VARCHAR(50)  DEFAULT NULL COMMENT '关联监抽任务编号（supervise_task.task_no）',
  `task_batch_no`         VARCHAR(50)  DEFAULT NULL COMMENT '任务批号',
  `status`                TINYINT      NOT NULL DEFAULT 10 COMMENT '样品状态机 code（S10=10…S90=90，见 common/enums/SampleStatus）',
  `confirmed_by`          VARCHAR(64)  DEFAULT NULL COMMENT '登记确认人（S10→S20 时写入）',
  `confirmed_at`          DATETIME     DEFAULT NULL COMMENT '登记确认时间',
  `created_by`            VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`            DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`            VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`            DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sample_no` (`sample_no`),
  KEY `idx_sample_task_no` (`task_no`),
  KEY `idx_sample_status` (`status`),
  KEY `idx_sample_name` (`sample_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='样品登记';

-- -----------------------------------------------------------------------------
-- 采样单导入批次表
-- 业务来源：说明书「① 采样单Excel后缀名为 *.xls  ② A1列自定义Excel文件标记，
--           防止重复导入」。导入成功后登记 A1 标记，再次导入同一文件时整文件拒绝。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_import_batch`;
CREATE TABLE `sample_import_batch` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_marker`   VARCHAR(100) NOT NULL COMMENT '采样单 A1 单元格文件标记（防重复导入）',
  `file_name`     VARCHAR(255) DEFAULT NULL COMMENT '上传文件名（仅追溯用）',
  `success_count` INT          NOT NULL DEFAULT 0 COMMENT '本批成功导入行数',
  `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '本批失败行数',
  `created_by`    VARCHAR(64)  DEFAULT NULL COMMENT '导入人',
  `created_at`    DATETIME     DEFAULT NULL COMMENT '导入时间',
  `updated_by`    VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`    DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_batch_marker` (`file_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='采样单导入批次';

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW TABLES LIKE 'sample%';
