-- =============================================================================
-- 06_item_tables.sql  检验项目分解表建表脚本（T-401，新表规范 AGENTS 6.1）
-- 对应任务：T-401 检验项目分解（自动套库）+ S20→S30
--           T-501 检验任务安排（本表追加指派字段）+ S30→S40
-- 业务依据：业务说明书「五、检验业务流程之二：样品检验明细项目分解（自动套用项目库）」
--   「项目分解是根据项目检测单项标准数据库自动加载全部检测单项，可根据项目的具体要求
--     在此基础进行检测单项的增加、删减等调整。项目分解完毕后请按'确认保存'按钮，
--     该项目即可转入任务安排流程。」
-- 设计要点：
--   1. 分解结果是**样品 × 检测单项**的实例行（sample_item），必须落库而非运行时计算——
--      因为业务允许在套库结果上「增加/删减调整」，调整结果就是业务数据本身，
--      且后续 T-501 任务安排、T-601 结果录入均以本表为输入。
--   2. 套库来源是 product_lib_item（项目标准库）
--      product_lib.product_name 匹配样品 sample_info.sample_name（T-903 已补齐名称）。
--   3. 标准库字段**快照下沉**到本表（std_value/judge_type/is_reference/lower_limit/unit/
--      basis_code/methods），原因：a) 标准库会随国标更新而变更，报告须固化当时的判定依据；
--      b) 允许人工调整单项，调整后的值必须独立于标准库保存。
--      → T-601 判定引擎只读 sample_item，不回溯 product_lib_item。
--   4. 审计四字段 + deleted 逻辑删除；唯一键 (sample_id, item_order) 防重。
-- 说明：表名用 sample_item 而非 item——
--   `item` 无关键字风险，但 sample_item 更能表达「样品下的单项」语义，与 sample_info 命名一致。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 样品检验单项表（分解结果）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `sample_item`;
CREATE TABLE `sample_item` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_id`      BIGINT       NOT NULL COMMENT '样品ID（sample_info.id）',
  `sample_no`      VARCHAR(50)  NOT NULL COMMENT '样品编号（冗余，便于查询/报告打印）',
  `item_order`     INT          NOT NULL DEFAULT 1 COMMENT '项次（样品内排序，从 1 起）',
  `item_name`      VARCHAR(255) NOT NULL COMMENT '检验项目/检测单项名称',

  -- ---- 标准库来源与快照字段 ----
  `lib_item_id`    BIGINT       DEFAULT NULL COMMENT '来源标准库明细ID（product_lib_item.id）；人工新增项为 NULL',
  `unit`           VARCHAR(50)  DEFAULT NULL COMMENT '单位',
  `basis_code`     VARCHAR(100) DEFAULT NULL COMMENT '判定依据标准号',
  `methods`        VARCHAR(500) DEFAULT NULL COMMENT '检验方法（多个以 # 分隔）',
  `std_value`      VARCHAR(50)  DEFAULT NULL COMMENT '标准值（限量值文本；白名单 5 形态）',
  `judge_type`     TINYINT      NOT NULL DEFAULT 1 COMMENT '判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工',
  `is_reference`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否参考性限量（带 * ）0=否 1=是',
  `lower_limit`    VARCHAR(20)  DEFAULT NULL COMMENT '最低检出限',
  `method_note`    VARCHAR(100) DEFAULT NULL COMMENT '方法备注',

  -- ---- 分解元信息 ----
  `source_type`    TINYINT      NOT NULL DEFAULT 1 COMMENT '来源 1=标准库自动套用 2=人工新增',
  `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注（调整原因等）',

  -- ---- 任务安排字段（T-501，S30→S40）----
  -- 指派粒度为「检测单项」：同一样品不同单项方法不同，可能指派不同检验员（AGENTS 7.4）。
  `assign_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '指派状态 0=待指派 1=已指派',
  `assign_type`    TINYINT      NOT NULL DEFAULT 0 COMMENT '指派方式 0=未指派 1=分类规则(编号含NA/XA/SA) 2=方法资质 3=人工改派',
  `tester_no`      VARCHAR(32)  DEFAULT NULL COMMENT '检验员工号（sys_user.username）',
  `tester_name`    VARCHAR(64)  DEFAULT NULL COMMENT '检验员姓名（sys_user.nickname，出网冗余便于列表展示）',
  `assigned_at`    DATETIME     DEFAULT NULL COMMENT '指派时间',
  `assigned_by`    VARCHAR(64)  DEFAULT NULL COMMENT '指派操作人工号',

  `created_by`     VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
  `created_at`     DATETIME     DEFAULT NULL COMMENT '创建时间',
  `updated_by`     VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
  `updated_at`     DATETIME     DEFAULT NULL COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sample_item_order` (`sample_id`, `item_order`, `deleted`),
  KEY `idx_sample_item_sample_id` (`sample_id`),
  KEY `idx_sample_item_name` (`item_name`),
  KEY `idx_sample_item_judge_type` (`judge_type`),
  KEY `idx_sample_item_tester_no` (`tester_no`),
  KEY `idx_sample_item_assign_status` (`assign_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='样品检验单项（项目分解结果 T-401 + 任务安排 T-501）';
