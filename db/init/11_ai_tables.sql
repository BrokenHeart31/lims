-- =============================================================================
-- 11_ai_tables.sql  本地 AI 助手 —— 新建表（feature A）
-- 对应设计：docs/design/2026-09-17-arch-ai-assistant-and-rollback.md §3.1.5~§3.1.6
-- 对应任务：T03 AI 助手后端（本批 T01 先落表结构）
-- 业务依据：PRD A-04/A-05/A-09 + T2
-- 设计要点：
--   1. gb_document / gb_clause / gb_import_job —— GB 标准库与**可重建的检索索引**。
--      ⚠️ 索引表**允许物理重建**（设计 §2.10）：它们是可由原始文件随时重建的派生数据，
--         不是业务留档；换版标准时物理 DELETE 旧 gb_clause 再重建，不适用「历史不消失」约束。
--   2. gb_clause.content 建 MySQL 8.0 **FULLTEXT ... WITH PARSER ngram** 全文索引
--      （实测 ngram_token_size=2、插件 ACTIVE）——秒级检索只查倒排索引，绝不全量读文件。
--   3. ai_conversation / ai_message —— 助手会话留痕（谁/何时/问了什么/命中哪些标准），
--      ai_message.citations_json 快照命中片段，可证明某次回答引用了哪一版标准条款。
-- 新表规范（AGENTS 6.1）：BIGINT AUTO_INCREMENT 主键 / snake_case / 审计四字段 /
--   deleted 逻辑删除 / utf8mb4_general_ci / InnoDB。
-- 说明：本脚本 DROP TABLE IF EXISTS 便于全新部署；存量库建表亦直接执行本脚本。
-- ⚠️⚠️ 重要（2026-09-18 增量）：本脚本含 DROP TABLE，**仅用于全新部署（空库）**。
--   存量库（活库 lims，已有数据）**禁止**执行本脚本——一旦导入 GB 知识库后再跑，
--   会把 gb_document / gb_clause / ai_message 等**整库清空**（不可逆）。
--   存量库请改用 **db/enable/2026-09-18-enable-existing-db.sql**（CREATE TABLE IF NOT EXISTS，幂等）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. gb_document —— GB 标准文档（导入通道产物）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `gb_document`;
CREATE TABLE `gb_document` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `std_no`        VARCHAR(50)  NOT NULL COMMENT '标准号（如 GB 2762-2022）',
  `std_title`     VARCHAR(255)          DEFAULT NULL COMMENT '标准名称',
  `source_file`   VARCHAR(255) NOT NULL COMMENT '来源文件名',
  `source_type`   TINYINT      NOT NULL COMMENT '来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR（PDF 须先预处理转为 TXT）',
  `ocr_derived`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否来自扫描件 OCR 0=否（文本版）1=是（数值请以系统标准库为准）',
  `checksum`      VARCHAR(64)  NOT NULL COMMENT '文件内容 sha256（幂等导入键）',
  `clause_count`  INT          NOT NULL DEFAULT 0 COMMENT '切块数',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1=已完成 2=已失效（被新版替换）',

  `created_by`    VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`    DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`    VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`    DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gb_doc_checksum` (`checksum`),
  KEY `idx_gb_doc_std_no` (`std_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='GB 标准文档（导入通道产物）';

-- -----------------------------------------------------------------------------
-- 2. gb_clause —— GB 标准条款切块（ngram 全文检索索引）
--    MATCH(content) 的列清单必须与本 FULLTEXT 索引列完全一致，否则报 1191。
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `gb_clause`;
CREATE TABLE `gb_clause` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `document_id`   BIGINT       NOT NULL COMMENT 'gb_document.id',
  `std_no`        VARCHAR(50)  NOT NULL COMMENT '标准号（冗余，检索结果直接可用）',
  `clause_no`     VARCHAR(50)           DEFAULT NULL COMMENT '条款号（如 4.2 / 表3）',
  `clause_title`  VARCHAR(255)          DEFAULT NULL COMMENT '条款标题',
  `content`       TEXT         NOT NULL COMMENT '切块正文（目标 200~500 字）',
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
  FULLTEXT KEY `ft_gbc_content` (`content`) WITH PARSER ngram,
  -- 2026-09-18 新增：条款标题的 ngram 全文索引。
  -- 用途：检索排序把「命中标题」的块排前（见 GbClauseMapper.xml ORDER BY）。
  -- 为什么需要：OCR 正文里有大量「检测方法清单」式超长块，会同时提到几十种农药名，
  -- 单靠正文相关度会让它们排在真正的条款块之前（实测：问「毒死蜱」首位是异狄氏剂块）。
  -- 条款标题才是「这条讲什么」的权威信号，故用标题相关度作为第一排序键。
  FULLTEXT KEY `ft_gbc_title` (`clause_title`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='GB 标准条款切块（ngram 全文检索索引）';

-- -----------------------------------------------------------------------------
-- 3. gb_import_job —— GB 导入任务（进度/失败可查）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `gb_import_job`;
CREATE TABLE `gb_import_job` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_name`     VARCHAR(255) NOT NULL COMMENT '文件名（或 scan 批次标记）',
  `file_path`     VARCHAR(500)          DEFAULT NULL COMMENT '解析产物路径（ai/standards/parsed/...）',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0=待处理 1=解析中 2=已完成 3=失败',
  `total_files`   INT          NOT NULL DEFAULT 0 COMMENT '批次文件总数',
  `done_files`    INT          NOT NULL DEFAULT 0 COMMENT '已完成文件数',
  `total_clauses` INT          NOT NULL DEFAULT 0 COMMENT '总切块数',
  `done_clauses`  INT          NOT NULL DEFAULT 0 COMMENT '已建索引切块数',
  `fail_count`    INT          NOT NULL DEFAULT 0 COMMENT '失败文件数',
  `error_msg`     VARCHAR(1000)         DEFAULT NULL COMMENT '失败明细（逐条「文件+原因」）',
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

-- -----------------------------------------------------------------------------
-- 4. ai_conversation —— AI 助手会话
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
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

-- -----------------------------------------------------------------------------
-- 5. ai_message —— AI 助手消息（含引用与拒答留痕，供事后审计）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `conversation_id`   BIGINT       NOT NULL COMMENT 'ai_conversation.id',
  `seq`               INT          NOT NULL DEFAULT 1 COMMENT '会话内序号',
  `role`              TINYINT      NOT NULL COMMENT '角色 1=用户 2=助手',
  `content`           TEXT                  DEFAULT NULL COMMENT '消息正文',

  `domain`            VARCHAR(16)           DEFAULT NULL COMMENT '领域判定 business / standard / other',
  `refused`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否越界拒答 0=否 1=是',
  `citations_json`    JSON                  DEFAULT NULL COMMENT '引用清单快照（标准号/条款/片段/出处/得分）',
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

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW TABLES LIKE 'gb_%';
SHOW TABLES LIKE 'ai_%';
-- ngram 解析器是否 ACTIVE（应为 1）
SELECT 'ngram_active' AS item, COUNT(*) AS cnt
  FROM information_schema.PLUGINS WHERE PLUGIN_NAME = 'ngram' AND PLUGIN_STATUS = 'ACTIVE';
