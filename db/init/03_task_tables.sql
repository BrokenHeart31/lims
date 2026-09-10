-- =============================================================================
-- 03_task_tables.sql  监抽任务表（T-201，新表规范 AGENTS 6.1）
-- 字段与前端契约 src/api/task.ts / api-spec 任务域对齐（camelCase ↔ snake_case
-- 由 MyBatis-Plus 自动映射）。
-- 字典决策（DECISIONS 2026-09-10）：task_nature/region_level/sampling_stage/status
-- 等业务字典字段以 VARCHAR 存中文字典值（源自下达文书，需原样展示打印）；
-- 样品状态机 S10→S90 才强制 TINYINT+枚举（T-301 起）。
-- =============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `supervise_task`;
CREATE TABLE `supervise_task` (
  `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_no`                   VARCHAR(50)  NOT NULL COMMENT '任务编号（唯一）',
  `task_name`                 VARCHAR(200) NOT NULL COMMENT '任务名称',
  `task_nature`               VARCHAR(20)  NOT NULL COMMENT '任务性质：监督抽检/委托抽样/委托送样',
  `task_source`               VARCHAR(100) DEFAULT NULL COMMENT '任务来源（下达单位）',
  `region_level`              VARCHAR(10)  DEFAULT NULL COMMENT '区域级别：省级/市级/区级',
  `leader`                    VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
  `batch_no`                  VARCHAR(50)  DEFAULT NULL COMMENT '批次',
  `receive_date`              DATE         DEFAULT NULL COMMENT '任务接受日期',
  `issue_date`                DATE         DEFAULT NULL COMMENT '任务下达日期',
  `complete_date`             DATE         DEFAULT NULL COMMENT '任务完成日期',
  `priority`                  VARCHAR(20)  DEFAULT NULL COMMENT '任务等级',
  `positive_rate_requirement` VARCHAR(50)  DEFAULT NULL COMMENT '阳性率要求',
  `sampling_stage`            VARCHAR(10)  DEFAULT NULL COMMENT '抽样环节：生产/流通/餐饮',
  `test_scope`                VARCHAR(500) DEFAULT NULL COMMENT '检测项目范围',
  `status`                    VARCHAR(10)  NOT NULL DEFAULT '草稿' COMMENT '任务状态：草稿/进行中/已完成/已中止',
  `remark`                    VARCHAR(500) DEFAULT NULL COMMENT '任务说明/备注',
  `created_by`                VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`                DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`                VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`                DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`                   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supervise_task_no` (`task_no`),
  KEY `idx_task_name` (`task_name`),
  KEY `idx_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监抽任务';
