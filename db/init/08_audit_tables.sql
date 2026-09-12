-- =============================================================================
-- 08_audit_tables.sql  检验报告审核签发流水表（T-701，新表规范 AGENTS 6.1）
-- 对应任务：T-701 审核/签发（S60→S70→S80，含「审核退回 → S50」）
-- 业务依据：业务说明书「八、检验业务流程之五：检验报告审核签发」——
--   「样品检测单项的检测数据全部录入系统后，样品即转入签发流程。经审核无误中心领导即可签发。」
--   + 报告页脚要求「报告无制表、审核、批准人签字无效」→ 审核人/签发人必须可追溯。
--   + AGENTS 7.2「（退回）审核退回 → S50 并通知检验员」。
-- 设计要点：
--   1. 本表是**流水（事件）表**：每一次审核通过 / 审核退回 / 签发都追加一行，永不改写——
--      这是 ALCOA+ 的 Audit Trail 载体（谁、何时、从什么状态到什么状态、什么意见）。
--   2. `sample_info` 上另存**当前有效**的审核人/签发人字段（audit_by/audit_at/sign_by/sign_at），
--      供报告打印直接取用，避免报告合成时反查流水；与 confirmed_by/confirmed_at 同一先例。
--   3. `abnormal_confirmed` 记录「放行前是否已确认异常项清单」——
--      落实 T-701 放行红线（存在待判定/未录入项时必须显式确认，不作为静默放行依据）。
-- 说明：表名用 sample_audit_log 而非 audit —— 明确「样品的」审核流水，避免与系统操作日志混淆。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 样品审核签发流水表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_audit_log`;
CREATE TABLE `sample_audit_log` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`          BIGINT       NOT NULL COMMENT '样品ID（sample_info.id）',
  `sample_no`          VARCHAR(50)  NOT NULL COMMENT '样品编号（冗余，便于查询/报告打印）',

  `action`             TINYINT      NOT NULL COMMENT '动作 1=审核通过 2=审核退回 3=签发（见 common/enums/AuditAction）',
  `from_status`        TINYINT      NOT NULL COMMENT '动作前样品状态 code',
  `to_status`          TINYINT      NOT NULL COMMENT '动作后样品状态 code',

  `opinion`            VARCHAR(500) DEFAULT NULL COMMENT '意见/退回原因（退回时必填）',
  `abnormal_confirmed` TINYINT      NOT NULL DEFAULT 0 COMMENT '放行前是否已确认异常项清单（待判定/未录入）0=否 1=是',

  `operated_by`        VARCHAR(64)  DEFAULT NULL COMMENT '操作人工号',
  `operated_at`        DATETIME     DEFAULT NULL COMMENT '操作时间',

  `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`         DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`         VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`         DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',

  PRIMARY KEY (`id`),
  KEY `idx_audit_log_sample_id` (`sample_id`),
  KEY `idx_audit_log_action` (`action`),
  KEY `idx_audit_log_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='样品审核签发流水（T-701）';

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW TABLES LIKE 'sample_audit_log';
SHOW COLUMNS FROM `sample_audit_log`;
