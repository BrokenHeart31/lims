-- =============================================================================
-- 04_tester_method.sql  检验方法-检验员资质表（T-103 补尾，新表规范 AGENTS 6.1）
-- 对应任务：T-103（豆包建，待 Copilot 终审）
-- 业务背景：AGENTS 7.4 任务安排自动分配规则——"其余项目按'检验方法—检验员资质'
--           自动匹配可执行人，允许人工改派（仅列出有资质者）"。本表即 T-501 的
--           资质匹配数据源：一张资质记录 = 某检验员对某检验方法具备资质。
-- 关联说明：
--   - tester_no 对应 sys_user.username（02_rbac_tables.sql，如 njna000/njxa000/njsa000）
--   - method_name / method_no 对应 product_lib_item.methods 中的方法条目
--     （method_no 为方法标准号，如 GB 5009.268-2016）
-- 约束设计：同一检验员对同一方法标准号仅一条资质记录（uk_tester_method）；
--           资质状态 qual_status 为 TINYINT 字典（1=有效 0=失效），供分配时过滤。
-- 执行前置：db/init/01_basic_tables.sql + 02_rbac_tables.sql（无硬依赖，仅关联语义）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `tester_method`;
CREATE TABLE `tester_method` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `method_name` VARCHAR(255) NOT NULL COMMENT '检验方法名称，如 食品中兽药最大残留限量测定',
  `method_no`   VARCHAR(100) NOT NULL COMMENT '方法标准号，如 GB 5009.268-2016',
  `tester_no`   VARCHAR(32)  NOT NULL COMMENT '检验员工号（sys_user.username，如 njna000）',
  `qual_status` TINYINT      NOT NULL DEFAULT 1 COMMENT '资质状态 1=有效 0=失效',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注（如资质取得日期/授权范围）',
  `created_by`  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`  DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`  DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tester_method` (`method_no`, `tester_no`),
  KEY `idx_tm_method_no` (`method_no`),
  KEY `idx_tm_tester_no` (`tester_no`),
  KEY `idx_tm_qual_status` (`qual_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检验方法-检验员资质';

SET FOREIGN_KEY_CHECKS = 1;
