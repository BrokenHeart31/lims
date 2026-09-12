-- =============================================================================
-- 07_result_tables.sql  检验结果表建表脚本（T-601，新表规范 AGENTS 6.1）
-- 对应任务：T-601 检验数据录入 + 自动判定引擎 + S50→S60
-- 业务依据：业务说明书「六、检验业务流程之三：检验数据录入与结果判定」
--   检验员按已安排的检测单项录入检验结果，系统按项目标准库的判定规则自动判定
--   单项合格/不合格；全部录齐后样品转入「检验完成」（S60）。
-- 设计要点（依据 docs/knowledge/2026-09-12-judge-engine-research.md 第 3 节）：
--   1. **原始值 + 派生结论同库分层保存**（ALCOA+ Original/Accurate）：
--      test_value 是检验员看到的原始值（数值 或 未检出），conclusion 是引擎按规则算出的结论。
--      报告上的每个结论都能回放到「哪条规则 + 哪个原始值」。
--   2. **判定依据说明（judge_basis）落库**：人可读，用于人工复核与审计追溯。
--      引擎只产出「结论 + 依据」，绝不改写原始值。
--   3. **结论来源留痕（conclusion_source）**：1=引擎自动（jt1/jt2）/ 2=人工判定（jt3 感官项，
--      AGENTS 7.3 规则 3）。区分二者，报告上才能说明「系统判定」还是「人工判定」。
--   4. **判定依据不冗余存放**：std_value / judge_type / lower_limit / is_reference 一律取自
--      sample_item（T-401 已快照下沉，且检验时点固化）——避免两处真相不一致。
--      → 引擎只读 sample_item，禁止回溯 product_lib_item（AGENTS 7.3 / 白名单定稿 D5）。
--   5. 一个检测单项对应一行结果（uk 唯一键 sample_item_id + deleted），重复保存走覆盖式更新，
--      更新动作由审计四字段（updated_by/updated_at）留痕。
--
-- 执行前置：db/init/06_item_tables.sql（sample_item）、05_sample_tables.sql（sample_info）
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 检验结果表
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_result`;
CREATE TABLE `sample_result` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`         BIGINT       NOT NULL COMMENT '样品ID（sample_info.id）',
  `sample_item_id`    BIGINT       NOT NULL COMMENT '检测单项ID（sample_item.id）',
  `sample_no`         VARCHAR(50)  NOT NULL COMMENT '样品编号（冗余，便于查询/报告打印）',
  `item_order`        INT          NOT NULL DEFAULT 1 COMMENT '项次（冗余，报告排序用）',
  `item_name`         VARCHAR(255) NOT NULL COMMENT '检验项目名称（冗余，报告打印用）',

  `test_value`        VARCHAR(100) DEFAULT NULL COMMENT '检验结果原始值（白名单 2 形态：数值 / 未检出）',
  `conclusion`        TINYINT      NOT NULL DEFAULT 3 COMMENT '单项结论 1=合格 2=不合格 3=待判定（见 common/enums/ResultConclusion）',
  `conclusion_source` TINYINT      NOT NULL DEFAULT 1 COMMENT '结论来源 1=引擎自动判定 2=检验员人工判定（jt3 感官项）',
  `judge_basis`       VARCHAR(255) DEFAULT NULL COMMENT '判定依据说明（人可读，审计追溯用）',

  `entered_by`        VARCHAR(64)  DEFAULT NULL COMMENT '录入人工号',
  `entered_at`        DATETIME     DEFAULT NULL COMMENT '录入时间',
  `remark`            VARCHAR(255) DEFAULT NULL COMMENT '备注',

  `created_by`        VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`        DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`        VARCHAR(64)  DEFAULT NULL COMMENT '更新人（重复保存/修正时留痕）',
  `updated_at`        DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_result_sample_item` (`sample_item_id`, `deleted`),
  KEY `idx_result_sample_id` (`sample_id`),
  KEY `idx_result_conclusion` (`conclusion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检验结果（录入原始值 + 判定结论，T-601）';

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 校验 SELECT（人工核对）
-- -----------------------------------------------------------------------------
SHOW TABLES LIKE 'sample_result';
DESC `sample_result`;
-- 整体结论列（由 db/init/05_sample_tables.sql 或 db/migrations/V4 提供）
SHOW COLUMNS FROM `sample_info` LIKE 'conclusion';
