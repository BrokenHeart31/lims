-- =============================================================================
-- 12_ai_hint_log.sql  AI 助手主动提示留痕 —— 新建表（增量 ai_flow_assistant）
-- 对应设计：docs/design/2026-09-18-arch-ai-flow-assistant.md §3.1
-- 对应任务：T01 基础设施与契约增量（本脚本面向**全新部署**；存量库走 db/migrations/V9）
-- 业务依据：PRD C-12「助手会话 / 建议条留痕可审计」+ G1（主动有度，误报率可复盘）
-- 定位：把「助手何时 / 对谁 / 在什么上下文 / 主动提示了什么 / 是否被点开」记成**追加型事件**，
--       供事后审计与护栏 / 触发质量复盘（与 ai_message 互补：前者记主动提示，后者记问答）。
-- ⚠️ 只追加、不改写；无 UPDATE/DELETE 入口（点击回填 clicked/clicked_at 属状态推进，非「改写历史」）。
-- ⚠️ 本脚本 DROP TABLE IF EXISTS 便于**全新部署**；**存量库禁用本脚本**（会清空留痕），
--    请改用 db/enable/2026-09-18-enable-existing-db.sql（幂等，只建缺失表）。
-- 新表规范（AGENTS 6.1）：BIGINT AUTO_INCREMENT 主键 / snake_case / 审计四字段 /
--   deleted 逻辑删除 / utf8mb4_general_ci / InnoDB。
-- =============================================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `ai_hint_log`;
CREATE TABLE `ai_hint_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_no`         VARCHAR(64)  NOT NULL COMMENT '触发用户工号',
  `conversation_id` BIGINT                DEFAULT NULL COMMENT '关联会话（若已开对话）',
  `sample_no`       VARCHAR(50)           DEFAULT NULL COMMENT '上下文样品编号',
  `page_key`        VARCHAR(64)           DEFAULT NULL COMMENT '来源页面标识（如 result-entry）',
  `item_name`       VARCHAR(100)          DEFAULT NULL COMMENT '检测项目名（触发键之一）',
  `std_no`          VARCHAR(50)           DEFAULT NULL COMMENT '命中的标准号',
  `hint_key`        VARCHAR(200) NOT NULL COMMENT '去重键 sampleNo|itemName|stdNo',
  `hint_text`       VARCHAR(500)          DEFAULT NULL COMMENT '建议条文案（一行）',
  `context_json`    JSON                  DEFAULT NULL COMMENT '当次上下文快照（供复盘）',
  `clicked`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否被点开 0=否 1=是',
  `clicked_at`      DATETIME              DEFAULT NULL COMMENT '点开时间',

  `created_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`      DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`      DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是（不使用）',

  PRIMARY KEY (`id`),
  KEY `idx_ahk_hint_key` (`hint_key`),
  KEY `idx_ahk_user_created` (`user_no`, `created_at`),
  KEY `idx_ahk_std_no` (`std_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 助手主动提示留痕（追加型，可审计）';

-- 校验 SELECT（人工核对）
SELECT 'ai_hint_log.exists' AS item, COUNT(*) AS cnt
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_hint_log';
