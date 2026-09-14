-- =============================================================================
-- 03_demo_flow_seed.sql —— 全角色可自测演示数据
-- -----------------------------------------------------------------------------
-- 背景（2026-09-14 用户实测反馈）：
--   「登 R3 检验员，录入数据也没有录入的地方，录不了」——根因**不是代码缺陷**，
--   而是库存数据里**没有任何样品处于「待录入」状态**：样品 1 已走完全程（S90），
--   样品 2 停在 S20 且项目库无「河蟹」条目、套库无结果。检验员打开「结果录入」
--   看到的是空列表，因此无从下手。同理，登记员/任务管理员也没有属于自己的待办。
--
-- 本脚本提供一条**每个角色都能立刻上手**的演示链路：
--   DEMO-2026-001  S10 已登记          → R1 样品登记员：登记确认
--   DEMO-2026-002  S30 已分解（待安排） → R2 任务管理员：自动分配 + 安排确认
--   DEMO-2026-003  S40 已安排          → R3 检验员：录入检测数据 → 自动判定 → 提交
--   DEMO-2026-004  S60 检验完成        → R100：报告审核（含 1 个「待判定」项，可验放行红线）
--   DEMO-2026-005  S70 已审核          → R100：签发 → 生成报告
-- 另外补齐「河蟹」项目标准库条目，使既有样品 2（S20）可以正常自动套库。
--
-- 幂等：本脚本先物理删除 `sample_no LIKE 'DEMO-%'` 的样品及其明细/结果，
--       再重新插入；项目库按 product_code 判重。可反复执行。
-- ⚠️ 只清理 DEMO- 前缀，不动样品 1 / 样品 2 等既有数据。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 清理上一次的演示数据（仅 DEMO- 前缀）
-- -----------------------------------------------------------------------------
DELETE r FROM `sample_result` r
  JOIN `sample_item` i ON i.id = r.sample_item_id
 WHERE i.sample_no LIKE 'DEMO-%';

DELETE FROM `sample_item` WHERE sample_no LIKE 'DEMO-%';
DELETE FROM `sample_info` WHERE sample_no LIKE 'DEMO-%';

-- -----------------------------------------------------------------------------
-- 1. 补齐水产类项目标准库（使「河蟹」可自动套库；既有样品 2 因此可以分解）
-- -----------------------------------------------------------------------------
INSERT INTO `product_lib` (`product_code`, `product_name`, `category`, `remark`,
                           created_by, created_at, updated_by, updated_at, deleted)
SELECT 'nasx001', '河蟹', '水产', '演示数据：使既有样品 2（河蟹）可自动套库',
       'seed', NOW(), 'seed', NOW(), 0
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM `product_lib` WHERE product_code = 'nasx001' AND deleted = 0) t);

SET @lib_id := (SELECT id FROM `product_lib` WHERE product_code = 'nasx001' AND deleted = 0 LIMIT 1);

DELETE FROM `product_lib_item` WHERE product_lib_id = @lib_id;

INSERT INTO `product_lib_item`
  (`product_lib_id`, `item_order`, `item_name`, `unit`, `basis_code`, `methods`,
   `std_value`, `judge_type`, `is_reference`, `lower_limit`, `method_note`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  (@lib_id, 1, '孔雀石绿',        'μg/kg',     'GB 31658.5', 'GB 31658.5', '不得检出', 2, 0, '0.5', NULL, 'seed', NOW(), 'seed', NOW(), 0),
  (@lib_id, 2, '氯霉素',          'μg/kg',     'GB 31658',   'GB 31658',   '不得检出', 2, 0, '0.1', NULL, 'seed', NOW(), 'seed', NOW(), 0),
  (@lib_id, 3, '镉（以Cd计）',    'mg/kg',     'GB 2762-2017', 'GB 5009.15', '0.5', 1, 0, NULL, NULL, 'seed', NOW(), 'seed', NOW(), 0),
  (@lib_id, 4, '挥发性盐基氮',    'mg/100g',   'GB 2733',    'GB 5009.228', '20',   1, 0, NULL, NULL, 'seed', NOW(), 'seed', NOW(), 0),
  (@lib_id, 5, '色泽',            '/',         'GB 2733',    '感官检验',    '--',   3, 1, NULL, '具有水产品应有色泽', 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 2. 方法-检验员资质（便于演示「按方法资质自动分配」与「方法资质」维护页）
-- -----------------------------------------------------------------------------
DELETE FROM `tester_method` WHERE remark = '演示数据';

INSERT INTO `tester_method` (`method_name`, `method_no`, `tester_no`, `qual_status`, `remark`,
                             created_by, created_at, updated_by, updated_at, deleted)
VALUES
  ('GB 31658.5', 'M-001', 'njsa000', 1, '演示数据', 'seed', NOW(), 'seed', NOW(), 0),
  ('GB 31658',   'M-002', 'njsa000', 1, '演示数据', 'seed', NOW(), 'seed', NOW(), 0),
  ('GB 2762-2017', 'M-003', 'njna000', 1, '演示数据', 'seed', NOW(), 'seed', NOW(), 0),
  ('GB 2733',    'M-004', 'njxa000', 1, '演示数据', 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 3. 演示样品 1：S10 已登记 → 给 R1 样品登记员做「登记确认」
-- -----------------------------------------------------------------------------
INSERT INTO `sample_info`
  (`sample_no`, `sample_name`, `client_name`, `sampling_address`, `sample_quantity`,
   `sampling_date`, `sampler`, `sample_state`, `original_no`, `inspect_type`,
   `task_no`, `status`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  ('DEMO-2026-001', '鲜食玉米', '南通惠民生鲜超市', '南通市崇川区人民中路 88 号', '2kg',
   CURDATE(), '徐汇宏', '完好', 'TZ（2026）-11', '监督抽检',
   'RW-NA-20260902', 10,
   'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 4. 演示样品 2：S30 已分解待安排 → 给 R2 任务管理员做「自动分配 + 安排确认」
--    （未分配：assign_status=0）
-- -----------------------------------------------------------------------------
INSERT INTO `sample_info`
  (`sample_no`, `sample_name`, `client_name`, `sampling_address`, `sample_quantity`,
   `sampling_date`, `sampler`, `sample_state`, `original_no`, `inspect_type`,
   `task_no`, `status`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  ('DEMO-2026-002', '菠菜', '通州绿源蔬菜合作社', '南通市通州区金沙镇', '1.5kg',
   CURDATE(), '徐汇宏', '新鲜', 'TZ（2026）-12', '监督抽检',
   'RW-NA-20260902', 30,
   'seed', NOW(), 'seed', NOW(), 0);

SET @s2 := (SELECT id FROM `sample_info` WHERE sample_no = 'DEMO-2026-002' LIMIT 1);

INSERT INTO `sample_item`
  (`sample_id`, `sample_no`, `item_order`, `item_name`, `unit`, `basis_code`, `methods`,
   `std_value`, `judge_type`, `is_reference`, `lower_limit`, `method_note`,
   `source_type`, `assign_status`, `assign_type`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  (@s2, 'DEMO-2026-002', 1, '毒死蜱',   'mg/kg', 'GB 2763-2021', 'GB 23200.113', '0.02', 1, 0, NULL, NULL, 1, 0, 0, 'seed', NOW(), 'seed', NOW(), 0),
  (@s2, 'DEMO-2026-002', 2, '氧乐果',   'mg/kg', 'GB 2763-2021', 'GB 23200.113', '0.02', 1, 0, NULL, NULL, 1, 0, 0, 'seed', NOW(), 'seed', NOW(), 0),
  (@s2, 'DEMO-2026-002', 3, '腐霉利',   'mg/kg', 'GB 2763-2021', 'GB 23200.113', '0.1',  1, 0, NULL, NULL, 1, 0, 0, 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 5. 演示样品 3：S40 已安排 → 给 R3 检验员做「录入检测数据」（本轮用户最关心的场景）
--    4 个单项均已分配：njsa000 ×2 / njna000 ×1 / njxa000 ×1
-- -----------------------------------------------------------------------------
INSERT INTO `sample_info`
  (`sample_no`, `sample_name`, `client_name`, `sampling_address`, `sample_quantity`,
   `sampling_date`, `sampler`, `sample_state`, `original_no`, `inspect_type`,
   `task_no`, `status`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  ('DEMO-2026-003', '草鱼', '南通润发生态园', '南通市通州区平潮镇 5 组', '3kg',
   CURDATE(), '徐汇宏', '鲜活', 'TZ（2026）-13', '监督抽检',
   'RW-SA-20260901', 40,
   'seed', NOW(), 'seed', NOW(), 0);

SET @s3 := (SELECT id FROM `sample_info` WHERE sample_no = 'DEMO-2026-003' LIMIT 1);

INSERT INTO `sample_item`
  (`sample_id`, `sample_no`, `item_order`, `item_name`, `unit`, `basis_code`, `methods`,
   `std_value`, `judge_type`, `is_reference`, `lower_limit`, `method_note`,
   `source_type`, `assign_status`, `assign_type`, `tester_no`, `tester_name`, `assigned_at`, `assigned_by`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  (@s3, 'DEMO-2026-003', 1, '孔雀石绿',     'μg/kg', 'GB 31658.5', 'GB 31658.5', '不得检出', 2, 0, '0.5', NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s3, 'DEMO-2026-003', 2, '恩诺沙星',     'μg/kg', 'GB 31650-2019', 'GB 31658.5', '100', 1, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s3, 'DEMO-2026-003', 3, '镉（以Cd计）', 'mg/kg', 'GB 2762-2017', 'GB 5009.15', '0.1', 1, 0, NULL, NULL, 1, 1, 1, 'njna000', '农残共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s3, 'DEMO-2026-003', 4, '色泽',         '/',     'GB 2733',   '感官检验',    '--',  3, 1, NULL, '具有水产品应有色泽', 1, 1, 1, 'njxa000', '畜残共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0);

-- -----------------------------------------------------------------------------
-- 6. 演示样品 4：S60 检验完成 → 给 R100 做「报告审核」
--    刻意保留 1 个「待判定」项（孔雀石绿未维护检出限），用于验证放行红线：
--    不勾选「已确认异常项」时，审核通过必须被拒绝。
-- -----------------------------------------------------------------------------
INSERT INTO `sample_info`
  (`sample_no`, `sample_name`, `client_name`, `sampling_address`, `sample_quantity`,
   `sampling_date`, `sampler`, `sample_state`, `original_no`, `inspect_type`,
   `task_no`, `status`, `conclusion`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  ('DEMO-2026-004', '鳜鱼', '海门水产品批发市场', '南通市海门区水产路 3 号', '2kg',
   CURDATE(), '徐汇宏', '鲜活', 'TZ（2026）-14', '监督抽检',
   'RW-SA-20260901', 60, 3,
   'seed', NOW(), 'seed', NOW(), 0);

SET @s4 := (SELECT id FROM `sample_info` WHERE sample_no = 'DEMO-2026-004' LIMIT 1);

INSERT INTO `sample_item`
  (`sample_id`, `sample_no`, `item_order`, `item_name`, `unit`, `basis_code`, `methods`,
   `std_value`, `judge_type`, `is_reference`, `lower_limit`, `method_note`,
   `source_type`, `assign_status`, `assign_type`, `tester_no`, `tester_name`, `assigned_at`, `assigned_by`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  (@s4, 'DEMO-2026-004', 1, '孔雀石绿', 'μg/kg', 'GB 31658.5',    'GB 31658.5', '不得检出', 2, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s4, 'DEMO-2026-004', 2, '恩诺沙星', 'μg/kg', 'GB 31650-2019', 'GB 31658.5', '100', 1, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s4, 'DEMO-2026-004', 3, '镉（以Cd计）', 'mg/kg', 'GB 2762-2017', 'GB 5009.15', '0.1', 1, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0);

INSERT INTO `sample_result`
  (`sample_id`, `sample_item_id`, `sample_no`, `item_order`, `item_name`,
   `test_value`, `conclusion`, `conclusion_source`, `judge_basis`, `entered_by`, `entered_at`,
   created_by, created_at, updated_by, updated_at, deleted)
SELECT i.sample_id, i.id, i.sample_no, i.item_order, i.item_name,
       '0.02', 3, 1, '标准值「不得检出」但未维护最低检出限，无法判断是否检出，需人工判定（演示数据：用于验证审核放行红线）',
       'njsa000', NOW(), 'seed', NOW(), 'seed', NOW(), 0
  FROM `sample_item` i
 WHERE i.sample_no = 'DEMO-2026-004' AND i.item_order = 1;

INSERT INTO `sample_result`
  (`sample_id`, `sample_item_id`, `sample_no`, `item_order`, `item_name`,
   `test_value`, `conclusion`, `conclusion_source`, `judge_basis`, `entered_by`, `entered_at`,
   created_by, created_at, updated_by, updated_at, deleted)
SELECT i.sample_id, i.id, i.sample_no, i.item_order, i.item_name,
       '152', 2, 1, '实测 152 > 标准值 100，判定不合格',
       'njsa000', NOW(), 'seed', NOW(), 'seed', NOW(), 0
  FROM `sample_item` i
 WHERE i.sample_no = 'DEMO-2026-004' AND i.item_order = 2;

INSERT INTO `sample_result`
  (`sample_id`, `sample_item_id`, `sample_no`, `item_order`, `item_name`,
   `test_value`, `conclusion`, `conclusion_source`, `judge_basis`, `entered_by`, `entered_at`,
   created_by, created_at, updated_by, updated_at, deleted)
SELECT i.sample_id, i.id, i.sample_no, i.item_order, i.item_name,
       '0.02', 1, 1, '实测 0.02 ≤ 标准值 0.1，判定合格',
       'njsa000', NOW(), 'seed', NOW(), 'seed', NOW(), 0
  FROM `sample_item` i
 WHERE i.sample_no = 'DEMO-2026-004' AND i.item_order = 3;

-- -----------------------------------------------------------------------------
-- 7. 演示样品 5：S70 已审核 → 给 R100 做「签发 → 生成报告」
--    全部合格，整体结论 = 合格
-- -----------------------------------------------------------------------------
INSERT INTO `sample_info`
  (`sample_no`, `sample_name`, `client_name`, `sampling_address`, `sample_quantity`,
   `sampling_date`, `sampler`, `sample_state`, `original_no`, `inspect_type`,
   `task_no`, `status`, `conclusion`, `audit_by`, `audit_at`, `audit_opinion`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  ('DEMO-2026-005', '团头鲂', '如东县水产养殖基地', '南通市如东县洋口镇', '2.5kg',
   CURDATE(), '徐汇宏', '鲜活', 'TZ（2026）-15', '监督抽检',
   'RW-SA-20260901', 70, 1, 'nj001', NOW(), '数据完整、判定依据充分，同意签发（演示数据）',
   'seed', NOW(), 'seed', NOW(), 0);

SET @s5 := (SELECT id FROM `sample_info` WHERE sample_no = 'DEMO-2026-005' LIMIT 1);

INSERT INTO `sample_item`
  (`sample_id`, `sample_no`, `item_order`, `item_name`, `unit`, `basis_code`, `methods`,
   `std_value`, `judge_type`, `is_reference`, `lower_limit`, `method_note`,
   `source_type`, `assign_status`, `assign_type`, `tester_no`, `tester_name`, `assigned_at`, `assigned_by`,
   created_by, created_at, updated_by, updated_at, deleted)
VALUES
  (@s5, 'DEMO-2026-005', 1, '恩诺沙星', 'μg/kg', 'GB 31650-2019', 'GB 31658.5', '100', 1, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s5, 'DEMO-2026-005', 2, '镉（以Cd计）', 'mg/kg', 'GB 2762-2017', 'GB 5009.15', '0.1', 1, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0),
  (@s5, 'DEMO-2026-005', 3, '挥发性盐基氮', 'mg/100g', 'GB 2733', 'GB 5009.228', '20', 1, 0, NULL, NULL, 1, 1, 1, 'njsa000', '水产共享检验员', NOW(), 'seed', 'seed', NOW(), 'seed', NOW(), 0);

INSERT INTO `sample_result`
  (`sample_id`, `sample_item_id`, `sample_no`, `item_order`, `item_name`,
   `test_value`, `conclusion`, `conclusion_source`, `judge_basis`, `entered_by`, `entered_at`,
   created_by, created_at, updated_by, updated_at, deleted)
SELECT i.sample_id, i.id, i.sample_no, i.item_order, i.item_name,
       CASE i.item_order WHEN 1 THEN '35' WHEN 2 THEN '0.03' ELSE '8' END,
       1, 1,
       CASE i.item_order WHEN 1 THEN '实测 35 ≤ 标准值 100，判定合格'
                         WHEN 2 THEN '实测 0.03 ≤ 标准值 0.1，判定合格'
                         ELSE '实测 8 ≤ 标准值 20，判定合格' END,
       'njsa000', NOW(), 'seed', NOW(), 'seed', NOW(), 0
  FROM `sample_item` i
 WHERE i.sample_no = 'DEMO-2026-005';

-- -----------------------------------------------------------------------------
-- 8. 校验：每个角色应有至少 1 条待办
-- -----------------------------------------------------------------------------
SELECT '登记待确认 (S10)' AS todo, COUNT(*) AS cnt FROM sample_info WHERE deleted = 0 AND status = 10
UNION ALL SELECT '待分解 (S20)', COUNT(*) FROM sample_info WHERE deleted = 0 AND status = 20
UNION ALL SELECT '待安排 (S30)', COUNT(*) FROM sample_info WHERE deleted = 0 AND status = 30
UNION ALL SELECT '待录入 (S40/S50)', COUNT(*) FROM sample_info WHERE deleted = 0 AND status IN (40, 50)
UNION ALL SELECT '待审核 (S60)', COUNT(*) FROM sample_info WHERE deleted = 0 AND status = 60
UNION ALL SELECT '待签发 (S70)', COUNT(*) FROM sample_info WHERE deleted = 0 AND status = 70;
