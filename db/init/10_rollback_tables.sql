-- =============================================================================
-- 10_rollback_tables.sql  全流程逐步回退机制 —— 新建表（feature B）
-- 对应设计：docs/design/2026-09-17-arch-ai-assistant-and-rollback.md §3.1.1~§3.1.4
-- 对应任务：T02 回退机制后端
-- 业务依据：PRD B-01~B-08 + T4/T5/T6
-- 设计要点：
--   1. **统一状态流水表 sample_status_log**：把「样品状态变更」的所有事件
--      （正向/退回/签发/回退/恢复/作废/报告）收敛成一条可精确回答
--      「谁/何时/从哪到哪/为何/入口」的**追加型**事件流。
--      ⚠️ 本表的诞生推翻了 DECISIONS 2026-09-13「不新建状态流水表、用既有字段近似推导」的旧自裁：
--         回退要求「从哪到哪」可精确查询，近似推导已不成立（见 DECISIONS 2026-09-17）。
--      ⚠️ 只追加、永不改写；无 UPDATE/DELETE 入口（检验机构审计要求）。
--   2. **回退记录 sample_rollback**：谁/何时/从哪到哪/原因/是否可再撤销。
--   3. **留档快照 sample_data_archive**：失效前整行 pre-image，取证用，只增不删。
--   4. **报告作废/召回 report_void**：S80/S90 专用治理动作，**不改 status**，只写标记。
-- 新表规范（AGENTS 6.1）：BIGINT AUTO_INCREMENT 主键 / snake_case / 审计四字段 /
--   deleted 逻辑删除 / utf8mb4_general_ci / InnoDB。
-- 说明：本脚本 DROP TABLE IF EXISTS 便于全新部署；存量库走 db/migrations/V8。
-- ⚠️⚠️ 重要（2026-09-18 增量）：本脚本含 DROP TABLE，**仅用于全新部署（空库）**。
--   存量库（活库 lims，已有数据）**禁止**执行本脚本——会清空已有留痕/流水（不可逆）。
--   存量库请改用 **db/enable/2026-09-18-enable-existing-db.sql**（CREATE TABLE IF NOT EXISTS，幂等）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. sample_status_log —— 统一状态流水表（本次核心新增）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_status_log`;
CREATE TABLE `sample_status_log` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`         BIGINT       NOT NULL COMMENT '样品ID（sample_info.id）',
  `sample_no`         VARCHAR(50)  NOT NULL COMMENT '样品编号（冗余，便于查询）',

  `event_type`        TINYINT      NOT NULL COMMENT '事件类型 1=正向推进 2=审核退回 3=签发 4=回退 5=恢复 6=作废/召回 7=报告生成（见 common/enums/StatusEventType）',
  `from_status`       TINYINT      NOT NULL COMMENT '变更前状态 code',
  `to_status`         TINYINT      NOT NULL COMMENT '变更后状态 code',
  `action_label`      VARCHAR(32)  NOT NULL COMMENT '人类可读动作（如「登记确认」「审核退回」「回退至已安排」）',

  `reason`            VARCHAR(500)          DEFAULT NULL COMMENT '原因（回退/退回/作废必填，正向可空）',
  `rollback_id`       BIGINT                DEFAULT NULL COMMENT '关联 sample_rollback.id（回退/恢复事件）',
  `data_disposition`  VARCHAR(500)          DEFAULT NULL COMMENT '下游数据处置摘要（如「失效 12 项结果、3 项明细」）',
  `source`            VARCHAR(32)           DEFAULT NULL COMMENT '入口/来源：SAMPLE/ITEM/ASSIGN/RESULT/AUDIT/REPORT/ROLLBACK_PANEL',

  `operated_by`       VARCHAR(64)           DEFAULT NULL COMMENT '操作人工号',
  `operated_at`       DATETIME              DEFAULT NULL COMMENT '操作时间',

  `created_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
  `created_at`        DATETIME              DEFAULT NULL COMMENT '创建时间',
  `updated_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
  `updated_at`        DATETIME              DEFAULT NULL COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是（业务上不使用删除）',

  PRIMARY KEY (`id`),
  KEY `idx_ssl_sample_id_id` (`sample_id`, `id`),   -- 支撑「按样品查全链路事件」（时间线）
  KEY `idx_ssl_operated_at`  (`operated_at`),        -- 支撑跨样品按时间检索
  KEY `idx_ssl_event_type`   (`event_type`),
  KEY `idx_ssl_sample_no`    (`sample_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='样品状态流水（统一正向+逆向，追加不改写）';

-- -----------------------------------------------------------------------------
-- 2. sample_rollback —— 回退动作记录（可恢复状态机）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_rollback`;
CREATE TABLE `sample_rollback` (
  `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`             BIGINT       NOT NULL COMMENT '样品ID',
  `sample_no`             VARCHAR(50)  NOT NULL COMMENT '样品编号',
  `from_status`           TINYINT      NOT NULL COMMENT '回退前状态 code',
  `to_status`             TINYINT      NOT NULL COMMENT '回退后状态 code',
  `edge_group`            TINYINT      NOT NULL COMMENT '回退分组 1=常规 2=敏感（见 RollbackGroup）',

  `reason`                VARCHAR(500) NOT NULL COMMENT '回退原因（必填）',
  `second_confirmed`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否完成二次确认 0=否 1=是',
  `invalidated_summary`   VARCHAR(1000)         DEFAULT NULL COMMENT '下游失效清单摘要（JSON 文本）',
  `affected_item_count`   INT          NOT NULL DEFAULT 0 COMMENT '失效的 sample_item 数',
  `affected_result_count` INT          NOT NULL DEFAULT 0 COMMENT '失效的 sample_result 数',
  `restored_sample_json`  JSON                  DEFAULT NULL COMMENT '被回退覆盖的 sample_info 字段快照（用于恢复）',

  `can_recover`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可再撤销 0=否（已产生新下游数据）1=是',
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

-- -----------------------------------------------------------------------------
-- 3. sample_data_archive —— 失效/修订留档（取证，只增不删）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_data_archive`;
CREATE TABLE `sample_data_archive` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`       BIGINT       NOT NULL COMMENT '样品ID',
  `sample_no`       VARCHAR(50)           DEFAULT NULL COMMENT '样品编号',
  `table_name`      VARCHAR(32)  NOT NULL COMMENT '来源表：sample_item / sample_result / sample_info',
  `row_id`          BIGINT       NOT NULL COMMENT '来源表主键 id',
  `rollback_id`     BIGINT                DEFAULT NULL COMMENT '触发留档的回退ID；NULL=保存前修订留档',
  `archive_reason`  TINYINT      NOT NULL COMMENT '留档原因 1=回退失效 2=保存前修订留档 3=手动留档',
  `snapshot_json`   JSON         NOT NULL COMMENT '整行快照（含失效前的 deleted 原值）',

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

-- -----------------------------------------------------------------------------
-- 4. report_void —— 已签发 / 已出报告 的作废、召回标注
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `report_void`;
CREATE TABLE `report_void` (
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

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW TABLES LIKE 'sample_status_log';
SHOW TABLES LIKE 'sample_rollback';
SHOW TABLES LIKE 'sample_data_archive';
SHOW TABLES LIKE 'report_void';
