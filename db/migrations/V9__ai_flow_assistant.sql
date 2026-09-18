-- =============================================================================
-- V9__ai_flow_assistant.sql —— 业务全流程 AI 助手增量迁移（可重跑、含校验 SELECT）
-- 对应设计：docs/design/2026-09-18-arch-ai-flow-assistant.md §3.2
-- 对应任务：T01 基础设施与契约增量
-- 前置：db/init/05..12 + V1..V8
-- 说明：本增量**不动任何业务表**（主线零回归）；仅新增助手留痕表 + 给 GB 派生索引
--       加一个「来源是否 OCR」标记（用于引用卡片可信度标注）。
--
-- ⚠️ 本脚本**幂等**（CREATE TABLE IF NOT EXISTS + information_schema 判定 ALTER），
--    可重复执行；但**存量库首次启用**推荐直接跑
--    db/enable/2026-09-18-enable-existing-db.sql（一次性补齐 V8+V9+建表，含全部警告注释）。
-- =============================================================================
SET NAMES utf8mb4;

-- 1) 助手主动提示留痕（若 db/init/12 已建则跳过；**绝不 DROP**，避免清空留痕）
CREATE TABLE IF NOT EXISTS `ai_hint_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_no`         VARCHAR(64)  NOT NULL COMMENT '触发用户工号',
  `conversation_id` BIGINT                DEFAULT NULL COMMENT '关联会话',
  `sample_no`       VARCHAR(50)           DEFAULT NULL COMMENT '上下文样品编号',
  `page_key`        VARCHAR(64)           DEFAULT NULL COMMENT '来源页面标识',
  `item_name`       VARCHAR(100)          DEFAULT NULL COMMENT '检测项目名',
  `std_no`          VARCHAR(50)           DEFAULT NULL COMMENT '命中的标准号',
  `hint_key`        VARCHAR(200) NOT NULL COMMENT '去重键 sampleNo|itemName|stdNo',
  `hint_text`       VARCHAR(500)          DEFAULT NULL COMMENT '建议条文案',
  `context_json`    JSON                  DEFAULT NULL COMMENT '当次上下文快照',
  `clicked`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否被点开',
  `clicked_at`      DATETIME              DEFAULT NULL COMMENT '点开时间',
  `created_by`      VARCHAR(64)           DEFAULT NULL,
  `created_at`      DATETIME              DEFAULT NULL,
  `updated_by`      VARCHAR(64)           DEFAULT NULL,
  `updated_at`      DATETIME              DEFAULT NULL,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ahk_hint_key` (`hint_key`),
  KEY `idx_ahk_user_created` (`user_no`, `created_at`),
  KEY `idx_ahk_std_no` (`std_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 助手主动提示留痕';

-- 2) GB 派生索引：来源是否为扫描件 OCR（用于引用卡片可信度标注；派生索引允许原地重建）
--    db/init/11_ai_tables.sql 的 gb_document 已含该列（全新部署）；存量库走此 ALTER（幂等）。
SET @ocr_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gb_document' AND COLUMN_NAME = 'ocr_derived'
);
SET @ddl := IF(@ocr_exists = 0,
  'ALTER TABLE `gb_document`
     ADD COLUMN `ocr_derived` TINYINT NOT NULL DEFAULT 0
     COMMENT ''是否来自扫描件 OCR 0=否（文本版）1=是（数值请以系统标准库为准）''
     AFTER `source_type`',
  'SELECT ''gb_document.ocr_derived 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 校验 SELECT（人工核对，fail-loud：应然≠实然即人工介入）
SELECT 'ai_hint_log.exists' AS item, COUNT(*) AS cnt
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_hint_log';
SELECT 'gb_document.ocr_derived.type' AS item, COLUMN_TYPE AS val
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gb_document' AND COLUMN_NAME = 'ocr_derived';

-- 4) ngram 就绪自检（沿用上轮；应返回 1）
SELECT 'ngram_active' AS item, COUNT(*) AS cnt
  FROM information_schema.PLUGINS WHERE PLUGIN_NAME = 'ngram' AND PLUGIN_STATUS = 'ACTIVE';
