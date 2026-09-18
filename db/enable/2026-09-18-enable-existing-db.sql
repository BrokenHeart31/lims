-- =============================================================================
-- db/enable/2026-09-18-enable-existing-db.sql
-- 存量库启用脚本 —— 把「昨天只建在临时库验证过、活库尚未落地」的增量结构一次性补齐。
-- =============================================================================
-- ⚠️⚠️⚠️ 为什么需要本脚本、以及它与 db/init/10、11 的区别（务必先读）⚠️⚠️⚠️
--
--   · `db/init/10_rollback_tables.sql` 与 `db/init/11_ai_tables.sql` 开头是
--     **`DROP TABLE IF EXISTS`** —— 它们面向**全新部署**（空库），语义是「清场重建」。
--   · 现在（表还不存在）直接跑它们**不安全但仍会成功**；然而**一旦导入 GB 知识库之后，
--     再跑一次就会把 `gb_document` / `gb_clause` / `ai_message` 等**整库清空**，不可逆！
--   · 因此本脚本 = **「只建缺失的表」的幂等版本**：
--       - 全部建表用 `CREATE TABLE IF NOT EXISTS`（表已存在则原样跳过，**绝不清空数据**）；
--       - 全部改列用 `information_schema` 判定后再执行（可重复执行）；
--       - 本脚本**可以安全地重复执行任意次**。
--   · 本脚本是「存量库启用」的唯一入口；`db/init/*` 仅用于**从零初始化**新库。
--
--   适用范围：活库 `lims`（MySQL 8.0.45）已存在既有业务数据（样品等演示数据必须保住），
--   仅缺少下列增量结构。执行后应用 `db/seed/02_ai_flow_rbac_seed.sql` 补权限种子。
--
--   前置：db/init/01..09（既有）已执行。
--   产出：新建 10 张表 + 改 3 列 + 1 列新增；末尾校验 SELECT 供人工核对。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- A. 回退机制 4 张表（feature B；等价于 db/init/10，但幂等）
-- =============================================================================

-- A1. sample_status_log —— 统一状态流水表（追加型）
CREATE TABLE IF NOT EXISTS `sample_status_log` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`         BIGINT       NOT NULL COMMENT '样品ID（sample_info.id）',
  `sample_no`         VARCHAR(50)  NOT NULL COMMENT '样品编号（冗余，便于查询）',
  `event_type`        TINYINT      NOT NULL COMMENT '事件类型 1=正向推进 2=审核退回 3=签发 4=回退 5=恢复 6=作废/召回 7=报告生成',
  `from_status`       TINYINT      NOT NULL COMMENT '变更前状态 code',
  `to_status`         TINYINT      NOT NULL COMMENT '变更后状态 code',
  `action_label`      VARCHAR(32)  NOT NULL COMMENT '人类可读动作',
  `reason`            VARCHAR(500)          DEFAULT NULL COMMENT '原因（回退/退回/作废必填）',
  `rollback_id`       BIGINT                DEFAULT NULL COMMENT '关联 sample_rollback.id',
  `data_disposition`  VARCHAR(500)          DEFAULT NULL COMMENT '下游数据处置摘要',
  `source`            VARCHAR(32)           DEFAULT NULL COMMENT '入口/来源',
  `operated_by`       VARCHAR(64)           DEFAULT NULL COMMENT '操作人工号',
  `operated_at`       DATETIME              DEFAULT NULL COMMENT '操作时间',
  `created_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`        DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`        DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是（业务上不使用删除）',
  PRIMARY KEY (`id`),
  KEY `idx_ssl_sample_id_id` (`sample_id`, `id`),
  KEY `idx_ssl_operated_at`  (`operated_at`),
  KEY `idx_ssl_event_type`   (`event_type`),
  KEY `idx_ssl_sample_no`    (`sample_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='样品状态流水（统一正向+逆向，追加不改写）';

-- A2. sample_rollback —— 回退动作记录
CREATE TABLE IF NOT EXISTS `sample_rollback` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`             BIGINT       NOT NULL COMMENT '样品ID',
  `sample_no`             VARCHAR(50)  NOT NULL COMMENT '样品编号',
  `from_status`           TINYINT      NOT NULL COMMENT '回退前状态 code',
  `to_status`             TINYINT      NOT NULL COMMENT '回退后状态 code',
  `edge_group`            TINYINT      NOT NULL COMMENT '回退分组 1=常规 2=敏感',
  `reason`                VARCHAR(500) NOT NULL COMMENT '回退原因（必填）',
  `second_confirmed`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否完成二次确认 0=否 1=是',
  `invalidated_summary`   VARCHAR(1000)         DEFAULT NULL COMMENT '下游失效清单摘要（JSON 文本）',
  `affected_item_count`   INT          NOT NULL DEFAULT 0 COMMENT '失效的 sample_item 数',
  `affected_result_count` INT          NOT NULL DEFAULT 0 COMMENT '失效的 sample_result 数',
  `restored_sample_json`  JSON                  DEFAULT NULL COMMENT '被回退覆盖的 sample_info 字段快照',
  `can_recover`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可再撤销 0=否 1=是',
  `recovered`             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已被恢复 0=否 1=是',
  `recover_by`            VARCHAR(64)           DEFAULT NULL COMMENT '恢复操作人',
  `recover_at`            DATETIME              DEFAULT NULL COMMENT '恢复时间',
  `operated_by`           VARCHAR(64)           DEFAULT NULL COMMENT '回退操作人',
  `operated_at`           DATETIME              DEFAULT NULL COMMENT '回退时间',
  `created_by`            VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`            DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`            VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`            DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是（不使用）',
  PRIMARY KEY (`id`),
  KEY `idx_sr_sample_id_id` (`sample_id`, `id`),
  KEY `idx_sr_operated_at`  (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='样品回退记录（可恢复状态机）';

-- A3. sample_data_archive —— 失效/修订留档（只增不删）
CREATE TABLE IF NOT EXISTS `sample_data_archive` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`       BIGINT       NOT NULL COMMENT '样品ID',
  `sample_no`       VARCHAR(50)           DEFAULT NULL COMMENT '样品编号',
  `table_name`      VARCHAR(32)  NOT NULL COMMENT '来源表：sample_item / sample_result / sample_info',
  `row_id`          BIGINT       NOT NULL COMMENT '来源表主键 id',
  `rollback_id`     BIGINT                DEFAULT NULL COMMENT '触发留档的回退ID',
  `archive_reason`  TINYINT      NOT NULL COMMENT '留档原因 1=回退失效 2=保存前修订留档 3=手动留档',
  `snapshot_json`   JSON         NOT NULL COMMENT '整行快照',
  `created_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`      DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`      DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是（不使用）',
  PRIMARY KEY (`id`),
  KEY `idx_sda_sample_id_id` (`sample_id`, `id`),
  KEY `idx_sda_row`          (`table_name`, `row_id`),
  KEY `idx_sda_rollback`     (`rollback_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='下游数据留档快照（取证，只增不删）';

-- A4. report_void —— 报告作废/召回标注
CREATE TABLE IF NOT EXISTS `report_void` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`         BIGINT       NOT NULL COMMENT '样品ID',
  `sample_no`         VARCHAR(50)  NOT NULL COMMENT '样品编号',
  `void_type`         TINYINT      NOT NULL COMMENT '类型 1=作废 2=召回',
  `status_at_void`    TINYINT      NOT NULL COMMENT '操作时样品状态（80/90）',
  `reason`            VARCHAR(500) NOT NULL COMMENT '强理由（必填）',
  `second_confirmed`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否二次确认 0=否 1=是',
  `operated_by`       VARCHAR(64)           DEFAULT NULL COMMENT '操作人工号',
  `operated_at`       DATETIME              DEFAULT NULL COMMENT '操作时间',
  `created_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`        DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`        DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是（不使用）',
  PRIMARY KEY (`id`),
  KEY `idx_rv_sample_id_id` (`sample_id`, `id`),
  KEY `idx_rv_operated_at`  (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报告作废/召回记录（S80/S90 专用治理动作）';

-- =============================================================================
-- B. AI 助手 5 张表（feature A；等价于 db/init/11，但幂等）
--    ⚠️ gb_document 建表语句**同步含 ocr_derived 列**（全新部署口径，与 V9 对齐）
-- =============================================================================

-- B1. gb_document
CREATE TABLE IF NOT EXISTS `gb_document` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `std_no`        VARCHAR(50)  NOT NULL COMMENT '标准号（如 GB 2762-2022）',
  `std_title`     VARCHAR(255)          DEFAULT NULL COMMENT '标准名称',
  `source_file`   VARCHAR(255) NOT NULL COMMENT '来源文件名',
  `source_type`   TINYINT      NOT NULL COMMENT '来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR',
  `ocr_derived`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否来自扫描件 OCR 0=否（文本版）1=是（数值请以系统标准库为准）',
  `checksum`      VARCHAR(64)  NOT NULL COMMENT '文件内容 sha256（幂等导入键）',
  `clause_count`  INT          NOT NULL DEFAULT 0 COMMENT '切块数',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=已完成 2=已失效',
  `created_by`    VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`    DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`    VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`    DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gb_doc_checksum` (`checksum`),
  KEY `idx_gb_doc_std_no` (`std_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='GB 标准文档（导入通道产物）';

-- B2. gb_clause（ngram 全文索引：MATCH 列必须与本 FULLTEXT 索引列完全一致，否则 1191）
CREATE TABLE IF NOT EXISTS `gb_clause` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `document_id`   BIGINT       NOT NULL COMMENT 'gb_document.id',
  `std_no`        VARCHAR(50)  NOT NULL COMMENT '标准号（冗余）',
  `clause_no`     VARCHAR(50)           DEFAULT NULL COMMENT '条款号（如 4.2 / 表3）',
  `clause_title`  VARCHAR(255)          DEFAULT NULL COMMENT '条款标题',
  `content`       TEXT         NOT NULL COMMENT '切块正文',
  `content_len`   INT          NOT NULL DEFAULT 0 COMMENT '正文字符数',
  `page_no`       INT                   DEFAULT NULL COMMENT '近似页码',
  `chunk_order`   INT          NOT NULL DEFAULT 1 COMMENT '块序（文档内）',
  `created_by`    VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`    DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`    VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`    DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_gbc_doc` (`document_id`, `chunk_order`),
  KEY `idx_gbc_std_no` (`std_no`),
  FULLTEXT KEY `ft_gbc_content` (`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='GB 标准条款切块（ngram 全文检索索引）';

-- B3. gb_import_job
CREATE TABLE IF NOT EXISTS `gb_import_job` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '文件名（或 scan 批次标记）',
  `file_path`     VARCHAR(500)          DEFAULT NULL COMMENT '解析产物路径',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0=待处理 1=解析中 2=已完成 3=失败',
  `total_files`   INT          NOT NULL DEFAULT 0 COMMENT '批次文件总数',
  `done_files`    INT          NOT NULL DEFAULT 0 COMMENT '已完成文件数',
  `total_clauses` INT          NOT NULL DEFAULT 0 COMMENT '总切块数',
  `done_clauses`  INT          NOT NULL DEFAULT 0 COMMENT '已建索引切块数',
  `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '失败文件数',
  `error_msg`     VARCHAR(1000)         DEFAULT NULL COMMENT '失败明细',
  `started_at`    DATETIME              DEFAULT NULL COMMENT '开始时间',
  `finished_at`   DATETIME              DEFAULT NULL COMMENT '结束时间',
  `created_by`    VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`    DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`    VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`    DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_gbj_status_id` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='GB 导入任务（进度/失败可查）';

-- B4. ai_conversation
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`         VARCHAR(255)          DEFAULT NULL COMMENT '会话标题（首问截断）',
  `user_no`       VARCHAR(64)  NOT NULL COMMENT '所属用户工号',
  `model`         VARCHAR(64)           DEFAULT NULL COMMENT '使用的模型名',
  `created_by`    VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`    DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`    VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`    DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_aic_user_no_id` (`user_no`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 助手会话';

-- B5. ai_message
CREATE TABLE IF NOT EXISTS `ai_message` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id`   BIGINT       NOT NULL COMMENT 'ai_conversation.id',
  `seq`               INT          NOT NULL DEFAULT 1 COMMENT '会话内序号',
  `role`              TINYINT      NOT NULL COMMENT '角色 1=用户 2=助手',
  `content`           TEXT                  DEFAULT NULL COMMENT '消息正文',
  `domain`            VARCHAR(16)           DEFAULT NULL COMMENT '领域判定 business / standard / other',
  `refused`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否越界拒答 0=否 1=是',
  `citations_json`    JSON                  DEFAULT NULL COMMENT '引用清单快照',
  `retrieved_count`   INT          NOT NULL DEFAULT 0 COMMENT '检索命中数',
  `model`             VARCHAR(64)           DEFAULT NULL COMMENT '模型名',
  `elapsed_ms`        INT          NOT NULL DEFAULT 0 COMMENT '本次耗时（毫秒）',
  `created_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`        DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`        DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
  PRIMARY KEY (`id`),
  KEY `idx_aim_conv_seq` (`conversation_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 助手消息（含引用与拒答留痕，供事后审计）';

-- =============================================================================
-- C. AI 助手主动提示留痕（feature 增量 ai_flow_assistant；等价于 db/init/12，但幂等）
-- =============================================================================
CREATE TABLE IF NOT EXISTS `ai_hint_log` (
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

-- =============================================================================
-- D. 既有表增量（等价于 db/migrations/V8 + V9 的 ALTER；全部幂等）
-- =============================================================================

-- D1. sample_info.void_status（作废/召回标记，不改 status）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_info' AND COLUMN_NAME = 'void_status');
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE `sample_info` ADD COLUMN `void_status` TINYINT NOT NULL DEFAULT 0
     COMMENT ''作废/召回标记 0=正常 1=已作废 2=已召回（S80/S90 专用治理动作，不改 status）''
     AFTER `report_generated_by`, ADD KEY `idx_sample_void_status` (`void_status`)',
  'SELECT ''sample_info.void_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- D2. sample_item.deleted → BIGINT（失效标记 = 行自身 id；禁止 deleted=1 判定）
SET @item_type := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_item' AND COLUMN_NAME = 'deleted');
SET @ddl := IF(@item_type IS NOT NULL AND @item_type LIKE 'tinyint%',
  'ALTER TABLE `sample_item` MODIFY COLUMN `deleted` BIGINT NOT NULL DEFAULT 0
     COMMENT ''失效标记 0=有效 非0=该行自身id（已失效）''',
  'SELECT ''sample_item.deleted 已是 BIGINT，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- D3. sample_result.deleted → BIGINT
SET @result_type := (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_result' AND COLUMN_NAME = 'deleted');
SET @ddl := IF(@result_type IS NOT NULL AND @result_type LIKE 'tinyint%',
  'ALTER TABLE `sample_result` MODIFY COLUMN `deleted` BIGINT NOT NULL DEFAULT 0
     COMMENT ''失效标记 0=有效 非0=该行自身id（已失效）''',
  'SELECT ''sample_result.deleted 已是 BIGINT，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- D4. gb_document.ocr_derived（来源是否扫描件 OCR；派生索引允许原地重建）
SET @ocr_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gb_document' AND COLUMN_NAME = 'ocr_derived');
SET @ddl := IF(@ocr_exists = 0,
  'ALTER TABLE `gb_document` ADD COLUMN `ocr_derived` TINYINT NOT NULL DEFAULT 0
     COMMENT ''是否来自扫描件 OCR 0=否（文本版）1=是（数值请以系统标准库为准）'' AFTER `source_type`',
  'SELECT ''gb_document.ocr_derived 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- E. 校验 SELECT（人工核对，fail-loud：应然 ≠ 实然即人工介入）
-- =============================================================================
SELECT 'tables_should_be_10' AS item, COUNT(*) AS cnt
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('sample_status_log','sample_rollback','sample_data_archive','report_void',
                      'gb_document','gb_clause','gb_import_job','ai_conversation','ai_message','ai_hint_log');
SELECT 'sample_info.void_status' AS item, COUNT(*) AS should_be_1
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_info' AND COLUMN_NAME = 'void_status';
SELECT 'sample_item.deleted.type' AS item, COLUMN_TYPE AS val
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_item' AND COLUMN_NAME = 'deleted';
SELECT 'sample_result.deleted.type' AS item, COLUMN_TYPE AS val
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sample_result' AND COLUMN_NAME = 'deleted';
SELECT 'gb_document.ocr_derived.type' AS item, COLUMN_TYPE AS val
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gb_document' AND COLUMN_NAME = 'ocr_derived';
-- ngram 全文解析器就绪（应返回 1）
SELECT 'ngram_active' AS item, COUNT(*) AS should_be_1
  FROM information_schema.PLUGINS WHERE PLUGIN_NAME = 'ngram' AND PLUGIN_STATUS = 'ACTIVE';
