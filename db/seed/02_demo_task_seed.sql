-- =============================================================================
-- 02_demo_task_seed.sql  监抽任务演示数据（配合 T-201 联调/初步测试）
-- 执行前置：db/init/03_task_tables.sql。可重复执行（按 task_no 清场）。
-- =============================================================================

SET NAMES utf8mb4;

DELETE FROM `supervise_task` WHERE `task_no` IN ('RW-SA-20260901', 'RW-NA-20260902', 'RW-XA-20260903');
INSERT INTO `supervise_task`
  (`task_no`, `task_name`, `task_nature`, `task_source`, `region_level`, `leader`,
   `batch_no`, `receive_date`, `issue_date`, `complete_date`, `priority`,
   `positive_rate_requirement`, `sampling_stage`, `test_scope`, `status`, `remark`,
   `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`) VALUES
('RW-SA-20260901', '2026 年第三季度水产品质量安全监督抽检', '监督抽检', '省农业农村厅', '省级', '张三',
 '2026-SA-03', '2026-09-01', '2026-08-28', NULL, '重点',
 '阳性率 ≤ 5%', '流通', '氯霉素、孔雀石绿、硝基呋喃类', '进行中', '覆盖南京/苏州/无锡三地批发市场',
 'seed', NOW(), 'seed', NOW(), 0),
('RW-NA-20260902', '2026 年秋季农产品（蔬菜）例行监测', '委托抽样', '市市场监管局', '市级', '李四',
 '2026-NA-11', '2026-09-02', '2026-09-01', NULL, '常规',
 NULL, '生产', '阿维菌素、毒死蜱、氧乐果', '草稿', NULL,
 'seed', NOW(), 'seed', NOW(), 0),
('RW-XA-20260903', '2026 年畜产品（猪肉）瘦肉精专项抽检', '监督抽检', '省畜牧兽医局', '省级', '王五',
 '2026-XA-07', '2026-08-20', '2026-08-15', '2026-09-05', '重点',
 '阳性率 = 0%', '流通', '克伦特罗、莱克多巴胺、沙丁胺醇', '已完成', '报告已签发归档',
 'seed', NOW(), 'seed', NOW(), 0);

SELECT `task_no`, `task_name`, `status` FROM `supervise_task`;
