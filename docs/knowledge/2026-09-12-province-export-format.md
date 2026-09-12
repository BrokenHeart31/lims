# T-802 省平台上报（农/畜/水）导出格式定稿（豆包整理）

> 整理人：豆包 ｜ 日期：2026-09-12 ｜ 任务：T-802（B 级）
> 权威依据：业务说明书 `Demo-lims业务功能说明书.docx` 第十一节「自动生成"农、畜、水"省平台上报对接数据」+ 其内嵌截图 `image30.png`（系统导出数据样例）。
> 边界说明：本文档为**格式与字段映射定稿**，供 GLM 据此实现 `/api/export/*` 后端接口与前端按钮；**豆包不改 api-spec 契约、不写后端 Java**。契约落档由 GLM 在 api-spec 第 8 章补充。

## 1. 业务说明

- 检验完成（样品已出报告）后，将**检验结果汇总**按省平台要求导出为一个 Excel 文件。
- 入口：「导出数据」菜单（说明书 image29），点「导出数据」下载；文件标题栏显示为 `系统导出数据<yyyyMMddHHmmss>.xls`。
- 粒度：**一行 = 一个样品的一个检验项目**（样品 × 项目展开），同一样品的 N 个项目占 N 行；样品头信息（编号/名称/抽样日期/任务编号）在每行重复。
- 数据范围：仅导出**已完成检验、可上报**的样品。建议口径 `sample_info.status >= 80`（已签发 S80 / 已出报告 S90），未出报告的样品不导出；具体阈值由 GLM 在契约中裁定（见 §5 待裁点）。

## 2. 输出版式（对照说明书 image30）

第 1 行为合并大标题「系统导出数据」；第 2 行为表头；第 3 行起为数据。
旧系统样例把 10 个逻辑列放在 A/B/C/D/F/H/J/L/N/P，E/G/I/K/M/O 为空隔列（旧式两列合并视觉残留）。
**实现建议**：后端用 EasyExcel 直接输出 10 个连续逻辑列即可（省平台按列语义读取，不依赖隔列）；若平台校验严格要求隔列版式，再按下表补空列。

| 逻辑列 | 旧表位置 | 表头文字 | 说明 |
|---|---|---|---|
| 1 | A | 样品编号 | sample_info.sample_no |
| 2 | B | 样品名称 | sample_info.sample_name |
| 3 | C | 抽样日期 | sample_info.sampling_date，格式 `yyyy.MM.dd` |
| 4 | D | 检验依据 | sample_item.basis_code（该项判定/检测依据） |
| 5 | F | 检验项目 | sample_item.item_name |
| 6 | H | 单位 | sample_item.unit |
| 7 | J | 技术要求 | sample_item.std_value（标准值/限量要求） |
| 8 | L | 检验结果 | sample_result.test_value（原始录入值） |
| 9 | N | 单项评价 | sample_result.conclusion → 中文（见 §3） |
| 10 | P | 任务编号 | sample_info.task_no |

> 表头在旧样例中无序号列，即纯上述 10 列。参考成品：`docs/reference/province_export_sample.xlsx`（由本机样品 1 真实数据生成，7 行）。

## 3. 字段取值规则

- **单项评价映射**（与判定引擎结论字典一致，`ResultConclusion`）：
  - `1 合格` → `合格`
  - `2 不合格` → `不合格`
  - `3 待判定` → `待判定`（旧系统无此状态，本系统 fail-loud 产物；上报时如实输出，不静默改判）
  - 无结果行（`sample_result` 缺失）→ 该样品不应出现在导出范围（status>=80 已隐含录完），留空兜底。
- **检验结果**：原样输出 `test_value`（如 `0.01`、`未检出`、`色泽正常`），不做数值换算；文本/感官项（jt3）直接输出录入文本。
- **抽样日期**：`DATE_FORMAT(sampling_date, '%Y.%m.%d')`（旧样例为 `2023.1.12` 不补零，本系统统一补零 `2026.09.12`，更规范）。
- **检验依据**：取 `sample_item.basis_code`（分解时从标准库快照下沉，见 T-401）；一行一项目故每项各自带依据，与旧样例逐项目不同依据一致。
- **任务编号**：`sample_info.task_no`（旧样例 `RW-SA-20230101`，本系统为 `RW-SA-20260901`）。
- **参考项（is_reference=1）**：旧样例感官项前带 `*`（如 `*色泽`）。本系统 `is_reference=1` 项是否在「检验项目」名前加 `*` 由 GLM 裁定（见 §5）；结论照常输出。

## 4. 参考 SQL（豆包已用此句从本机库导出样例，供后端 Mapper 直接参考）

```sql
SELECT s.sample_no, s.sample_name,
       DATE_FORMAT(s.sampling_date, '%Y.%m.%d') AS sampling_date,
       i.basis_code, i.item_name, i.unit, i.std_value,
       COALESCE(r.test_value, '') AS test_value,
       CASE r.conclusion WHEN 1 THEN '合格' WHEN 2 THEN '不合格'
            WHEN 3 THEN '待判定' ELSE '' END AS conclusion_label,
       s.task_no
FROM sample_info s
JOIN sample_item i ON i.sample_id = s.id AND i.deleted = 0
LEFT JOIN sample_result r ON r.sample_item_id = i.id AND r.deleted = 0
WHERE s.deleted = 0 AND s.status >= 80
ORDER BY s.id, i.item_order;
```

本机实跑结果（样品 1，7 项）：孔雀石绿 待判定 / 氯霉素 合格 / 挥发性盐基氮 合格 / 菌落总数 合格 / 铅 合格 / 镉 不合格 / 恩诺沙星 不合格 —— 与 `docs/reference/province_export_sample.xlsx` 逐行一致。

## 5. 待 GLM 裁定 / 落档点（豆包不裁决）

1. **导出范围阈值**：`status >= 80`（含 S80 已签发即可上报）还是仅 `status = 90`（已出报告 S90）？豆包倾向 `>=80`（报告已签发即可汇总上报，S90 只是报告落盘动作）。
2. **是否按任务编号筛选导出**：说明书「可按任务编号筛选」，后端是否支持 `?taskNo=` 参数 + 默认全量已完成样品导出。
3. **参考项星号**：`is_reference=1` 项「检验项目」名前是否加 `*`（对齐旧样例感官项）。
4. **契约落档**：在 `docs/api/api-spec.md` 第 8 章 `/api/export/*` 补「农畜水省平台上报导出」端点（建议 `GET /api/export/province`，权限 `export:province`，见 AGENTS 8.2；返回 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 流，文件名 `系统导出数据<timestamp>.xlsx`）。**豆包不修改该文件，由 GLM 落档。**
5. **实现方式**：沿用 EasyExcel 3.3.4（DECISIONS 2026-09-11 已定，禁止 POI 裸 API），写一个 `ProvinceExportDTO`（`@ExcelProperty` 10 列）+ Service 流式写出。

## 6. 与既有资产的关系

- 依赖：EasyExcel（T-301 已引入，`pom.xml` 已含），无需新增依赖。
- 权限：`export:province` 已在 AGENTS 8.2 权限标识清单中预留，seed `sys_menu` 需补对应菜单项（GLM 落档时一并处理）。
- 前端：在菜单加「导出数据」入口（说明书 image29），按钮 `v-permission="'export:province'"`，直接 `window.open` 或 axios blob 下载（豆包仅给文案建议，前端实现归 GLM）。
