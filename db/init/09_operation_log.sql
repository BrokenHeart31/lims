-- =============================================================================
-- 09_operation_log.sql —— 系统操作日志（审计流水）
-- -----------------------------------------------------------------------------
-- 定位：记录「谁 · 何时 · 对哪个业务接口 · 做了什么 · 结果如何」。
-- 写入方：`config/OperationLogInterceptor`（HandlerInterceptor），
--        只记录**写请求**（POST/PUT/DELETE），GET 查询不入库，避免日志被浏览行为淹没。
--
-- 为什么不是 AOP 切面：
--   本机离线 Maven 仓库无 `spring-boot-starter-aop` / `aspectjweaver`，无法引入依赖；
--   而 Spring MVC 的 HandlerInterceptor 属于 spring-webmvc（已在依赖内），
--   能在**不改动任何业务 Service** 的前提下完成同样的集中式记录（0 侵入）。
--
-- 为什么只追加、不提供修改/删除接口：
--   审计流水的价值在于不可篡改。表结构保留 `deleted` 以符合 AGENTS 6.1 新表规范，
--   但业务代码不提供任何 UPDATE/DELETE 入口。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `module`        VARCHAR(32)  NOT NULL COMMENT '业务模块（由请求路径前缀推导，如「结果录入」）',
  `http_method`   VARCHAR(8)   NOT NULL COMMENT 'HTTP 方法：POST/PUT/DELETE',
  `uri`           VARCHAR(255) NOT NULL COMMENT '请求路径（相对 context-path /api，不含查询串）',
  `summary`       VARCHAR(255) NOT NULL COMMENT '人类可读摘要（模块 · 动作 路径）',
  `operator`      VARCHAR(64)  NOT NULL COMMENT '操作人工号',
  `operator_name` VARCHAR(64)      NULL COMMENT '操作人姓名（冗余存储，避免列表页联表）',
  `ip`            VARCHAR(64)      NULL COMMENT '客户端 IP（经 X-Forwarded-For 首个地址回退到 RemoteAddr）',
  `result`        TINYINT      NOT NULL DEFAULT 1 COMMENT '结果：1=成功 0=失败',
  `status_code`   INT          NOT NULL DEFAULT 200 COMMENT 'HTTP 状态码',
  `duration_ms`   BIGINT       NOT NULL DEFAULT 0 COMMENT '处理耗时（毫秒）',
  `created_by`    VARCHAR(64)      NULL COMMENT '创建人（审计四字段）',
  `created_at`    DATETIME         NULL COMMENT '创建时间（即操作发生时间）',
  `updated_by`    VARCHAR(64)      NULL COMMENT '更新人（审计四字段）',
  `updated_at`    DATETIME         NULL COMMENT '更新时间（审计四字段）',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  KEY `idx_oplog_operator_id` (`operator`, `id`),
  KEY `idx_oplog_created_at` (`created_at`),
  KEY `idx_oplog_module` (`module`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统操作日志（只追加，供用户菜单「操作日志」消费）';
