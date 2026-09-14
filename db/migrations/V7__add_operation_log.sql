-- =============================================================================
-- V7 —— 新增 `sys_operation_log`（系统操作日志）
-- -----------------------------------------------------------------------------
-- 背景：用户菜单中的「操作日志」此前是空壳（对话框只显示「待后端接入」）。
-- 本迁移补齐存储层；配合 `config/OperationLogInterceptor` 记录写请求。
--
-- 幂等：`CREATE TABLE IF NOT EXISTS`，可重复执行。
-- 已存在的库执行本脚本即完成升级；全新部署由 `db/init/09_operation_log.sql` 建表。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `module`        VARCHAR(32)  NOT NULL COMMENT '业务模块（由请求路径前缀推导）',
  `http_method`   VARCHAR(8)   NOT NULL COMMENT 'HTTP 方法：POST/PUT/DELETE',
  `uri`           VARCHAR(255) NOT NULL COMMENT '请求路径（相对 /api，不含查询串）',
  `summary`       VARCHAR(255) NOT NULL COMMENT '人类可读摘要',
  `operator`      VARCHAR(64)  NOT NULL COMMENT '操作人工号',
  `operator_name` VARCHAR(64)      NULL COMMENT '操作人姓名（冗余）',
  `ip`            VARCHAR(64)      NULL COMMENT '客户端 IP',
  `result`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
  `status_code`   INT          NOT NULL DEFAULT 200 COMMENT 'HTTP 状态码',
  `duration_ms`   BIGINT       NOT NULL DEFAULT 0 COMMENT '处理耗时（毫秒）',
  `created_by`    VARCHAR(64)      NULL,
  `created_at`    DATETIME         NULL,
  `updated_by`    VARCHAR(64)      NULL,
  `updated_at`    DATETIME         NULL,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_oplog_operator_id` (`operator`, `id`),
  KEY `idx_oplog_created_at` (`created_at`),
  KEY `idx_oplog_module` (`module`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统操作日志（只追加）';

-- 升级校验：应返回 1 行
SELECT COUNT(*) AS tbl_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_operation_log';
