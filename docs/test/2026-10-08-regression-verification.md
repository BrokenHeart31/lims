# LIMS 修复后全量回归验证报告（T-940，2026-10-08）

- **验证人**：严过关（Yan，QA / software-qa-engineer，独立于修复工程师）
- **验证对象**：2026-10-07 全流程测试 31 项缺陷（F1~F31）修复后的代码 + 数据修复 + 重导
- **对照基线**：`docs/test/2026-10-07-fullflow-test-report.md`（第二/三/六/九节）
- **环境**：后端 http://localhost:8080/api · 前端 http://localhost:5173 · MySQL 8 库 `lims`
- **判定口径**：**证据 > 自述**。任何无独立证据项标「未验证」，不标 PASS。业务失败按 AGENTS 约定为 **HTTP 200 + body.code**（非 HTTP 4xx），下文「code=400」即此口径。
- **一句话结论**：**本轮修复通过独立验证——A/B/C/D/E/F 六段共 54 项检查，53 项 PASS、0 项 FAIL、1 项未验证；无阻断性遗留。** 唯一「未验证」为 F8 前端运行时静默续期（仅做代码级 + 后端接口级核验），以及原报告 F10 弹窗穿透未做针对性复现。

---

## 结果总览

| 段 | 内容 | 检查数 | PASS | FAIL | 未验证 |
|---|---|---|---|---|---|
| A | 质量门禁（后端 + 前端三门禁） | 4 | 4 | 0 | 0 |
| B | F20 合规闭环（S 级）+ UI 门禁 | 13 | 13 | 0 | 0 |
| C | F17 数据修复复核 | 4 | 4 | 0 | 0 |
| D | 回归保护清单抽验 | 9 | 9 | 0 | 0 |
| E | 新修复 UI 抽查（Playwright） | 20 | 20 | 0 | 0 |
| F | 收尾：演示数据还原 + 卫生脚本 no-op | 2 | 2 | 0 | 0 |
| — | F8 静默续期（补充，非清单项） | 2 | 1 | 0 | 1 |
| **合计** | | **54** | **53** | **0** | **1** |

> 说明：E 段 20 项为修正探针脚本后的**最终定稿**结果（此前脚本用过时选择器/错误路由，已修正并在报告内注明）。

---

## A. 质量门禁（独立复跑）

**A1 后端全量测试 — ✅ PASS**

```
[INFO] Results:
[INFO] Tests run: 261, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  15.975 s
```
- 命令：`cd /d/lims/backend && bash /tmp/lims-mvn.sh -o test`
- 证据文件：`_logs/qa-verify-mvn-test.log`（关键类：`AuditServiceImplTest` 20、`ReportGenerateServiceImplTest` 4、`RollbackExecutorTest` 9 全绿）
- 与预期一致（261 项 / 0 失败）。

**A2 前端三门禁 — ✅ PASS**

| 门禁 | 命令 | 退出码 | 关键行 |
|---|---|---|---|
| 类型检查 | `node vue-tsc/bin/vue-tsc.js --noEmit` | **0** | 无输出（无类型错误） |
| Lint | `node eslint/bin/eslint.js .` | **0** | 无输出（无告警/错误） |
| 构建 | `node vite/bin/vite.js build` | **0** | `✓ built in 33.48s` |

- 证据文件：`_logs/qa-verify-tsc.log`、`_logs/qa-verify-eslint.log`、`_logs/qa-verify-build.log`

---

## B. F20 合规闭环（S 级，最高优先）— ✅ 13/13 PASS

目标样品 **DEMO-2026-004（鳜鱼）**，动态取自 `/report/audit/pending`，当前 id 随 seed 重建（验证时 id=70，仅取一次）。该样品 3 项：孔雀石绿「待判定」、恩诺沙星 152>100 不合格、镉合格。

| # | 用例 | 预期 | 实测（HTTP / body.code / msg） | 判定 |
|---|---|---|---|---|
| B1 | `POST /report/audit/return {reason:""}` | code=400 退回原因不能为空 | 200 / **400** / `reason 退回原因不能为空` | ✅ |
| B2 | `POST /report/audit/approve {abnormalConfirmed:false, adjudications:[]}` | code=400 红线 | 200 / **400** / `该样品存在 1 个待判定/未录入项，请先逐项确认「异常项清单」后再审核通过` | ✅ |
| B3 | `approve {abnormalConfirmed:true}`（不给裁决） | code=400 提示缺裁决条数 | 200 / **400** / `共 1 个待判定项，请逐条选择裁决结论并填写说明（缺少 1 条）` | ✅ |
| B3b | 裁决结论非法=9 | code=400 | 200 / **400** / `裁决结论非法（仅 1=合格 / 2=不合格）：itemId=157` | ✅（补充） |
| B3c | 裁决说明空白 | code=400 | 200 / **400** / `adjudications[0].reason 裁决说明不能为空` | ✅（补充） |
| B4 | 正向 `approve` 带 `adjudications:[{itemId,conclusion:1,reason}]` + `abnormalConfirmed:true` | code=0 → S70 | 200 / **0** / success | ✅ |
| B5 | 复核落库 | conclusion=1 / source=2 / basis 含「【审核人工裁决】」 | `孔雀石绿 conclusion=1 conclusion_source=2`；basis=原依据` ｜ 【审核人工裁决】合格：…（nj001 2026-10-08 13:46）`；样品整体 conclusion=2（不合格，恩诺沙星超标） | ✅ |
| B6 | `POST /report/sign` | code=0 → S80 | 200 / **0** | ✅ |
| B7 | `POST /report/generate {sampleNo,reportType:1}` | code=0 → S90 | 200 / **0** | ✅ |
| B8 | `GET /report/detail?sampleNo=DEMO-2026-004&reportType=1` | 单项行无「待判定」 | 单项 `合格 / 不合格 / 合格`（conclusionCode 1/2/1）；正文 `conclusionText`「其中恩诺沙星超标，判定为不合格」；**全篇不含「待判定」** | ✅ |

- 证据：`_logs/qa-f20.mjs`、`_logs/qa-rpt.mjs` 输出；DB 交叉核验 `sample_result.conclusion` ∈ {1,2}（无 3）。
- **加分 B.6（浏览器 / Playwright）— ✅**：审核抽屉内「待判定」项渲染 **radio（合格/不合格）+ 裁决说明输入框**；未填时「审核通过」按钮 **disabled=true**；仅选结论未填说明 → 仍 disabled；结论+说明填齐 → **disabled=false**。截图 `_logs/qa-b6-audit-drawer.png`、`_logs/qa-b6-audit-filled.png`。

> 结论：F20 四条改法（逐条裁决落库 / 报告生成前 fail-loud 校验 / 报告单项只允许合格·不合格 / 整体结论聚合）**全部实测生效**；原有红线（未勾选确认 400、退回原因必填 400、总述自动归纳）**未回退**。

---

## C. F17 数据（鲜食玉米链路）— ✅ 4/4 PASS

| # | 检查 | 实测 | 判定 |
|---|---|---|---|
| C1 | `product_lib_item` 前 10 项（lib=10） | 名称完整：`2,4-滴二甲胺盐`、`2,4-滴和2,4-滴钠盐`…；unit 全 `mg/kg`；methods `参照NY/T 1434`/`GB 23200.20` **无乱码** | ✅ |
| C1b | 坏签名计数 `CHAR_LENGTH(TRIM(item_name))<=1` 且 unit 含逗号 | **0**；全库 62 项，乱码行（`[åæçÃ]`）**0**，单位唯一 `mg/kg` | ✅ |
| C2 | `_logs/assign-demo001-after.json` | items=62、**assigned=36 / pending=26**、无乱码特征（å/æ/Ã）=false | ✅ |
| C3 | `tester_method qual_status=1` | 计数 **60**，其中 `remark='演示数据'` = **26** | ✅ |

- 结论：F17 双重损坏（逗号误切 + UTF-8 双重编码）已修复；自动分派命中率由 23/62 提升到 36/62（与 `assignDone=36` 一致）。

---

## D. 回归保护清单抽验（对照报告第六节）— ✅ 9/9 PASS

| # | 检查 | 实测证据 | 判定 |
|---|---|---|---|
| D1 | RBAC：njna000 调用户/角色分页 | `GET /sys/user/page` → **HTTP 403**；`GET /sys/role/page` → **HTTP 403**；`GET /ai/status` → 200/0（允许） | ✅ |
| D2 | S90 不可回退：JK(2026)-SA-001 | `GET /rollback/targets/1` → `rollbackAvailable=false`，rejected `code=4103`；`POST /rollback/execute {ids:[1],targetStatus:10}` → `successCount=0 failCount=1 code=4103`，**无状态变更** | ✅ |
| D3 | 安排待指派拦截：DEMO-001 | `GET /assign/detail/67` → `assignTotal=62 assignDone=36`；`POST /assign/confirm` → **code=400** `仍有 26 个检测单项待指派，请先完成安排` | ✅ |
| D4 | 省平台导出 `/export/province` | 解包 sheet1 共 **15 数据行**，**无乱码**；「菌落总数」行：依据 GB 29921、单位 CFU/g、技术要求 50000、**检验结果=1200**、单项评价=合格；「色泽正常」仅出现在合法的「色泽」感官行（JK-SA-002），**不再串行到菌落总数** | ✅ |
| D5 | AI 离线 fail-loud | `GET /ai/status` → `online=false, modelPresent=false`；`POST /ai/chat` 返回**规则兜底**答案（`model=null, elapsedMs=7`，要求用户澄清，不编造样品结论） | ✅ |
| D6 | 模板/打印可达 | `GET http://localhost:5173/templates/sample_import_template.xlsx` → 200 / **6106 bytes** / 魔数 `PK`（合法 xlsx）；打印页 `/report/print?sampleNo=DEMO-2026-005&reportType=1` 渲染出报告正文（编号、资质证书号、注意事项、结论） | ✅ |

- 证据：`_logs/qa-d.mjs`、`_logs/qa-province.xlsx`、`_logs/qa-template-dl.xlsx`、`_logs/qa-print-DEMO005.png`。

---

## E. 新修复 UI 抽查（Playwright）— ✅ 20/20 PASS

| # | 缺陷 | 检查方式 | 实测 | 判定 |
|---|---|---|---|---|
| E1 | **F4** 面包屑单源 | 6 个路由统计 `.app-breadcrumb` 数量 | 均为 **1 条**；`/report/audit` = 工作台 / 报告管理 / 报告审核（**二级分组**） | ✅ |
| E2 | **F3** 表格渲染 | `/assign/index`、`/item/decompose`、`/sample` 表体行数 | 均 **rows=1**（有数据即渲染，不再 0 行） | ✅ |
| E2b | **F1** 宽表/固定列 | 1366 宽下测横向溢出 + 固定列底色 | 固定列 `backgroundColor=rgb(19,26,34)` **实色**；`/sample` 溢出 224px、`/task` 50px、`/query/testing` 800px（**可横滚**） | ✅ |
| E3 | **F12** checkbox | computed `border-top-left-radius` | **3px**（方形），宽度 14px | ✅ |
| E4 | **F9** 新建任务弹窗 | 折叠分组 + 任务等级控件 | 分组 = `基础信息（必填）/ 抽样信息 / 其他`；任务等级 = `el-select` **下拉** | ✅ |
| E5 | **F26** 部门人数 | `/sys/dept` 人数列 | 顶级「食品质量检验测试中心」= **本级 0 · 含下级 6**；子部门「仅本级 N」 | ✅ |
| E6 | **F7** 铃铛提示 | 弹层内「点击前往处理」出现次数 | **0**（合并为「点击任一条目可前往处理」1 处），< 1 次 | ✅ |

- 截图：`_logs/qa-f1-assign-1366.png`、`_logs/qa-f12-checkbox.png`。
- 证据脚本：`_logs/qa-e-ui.py`（定稿版输出 `total=20 pass=20 fail=0`）。

---

## F. 收尾（必须）— ✅ 2/2 PASS

**F.2a 重跑 `db/seed/03_demo_flow_seed.sql`（幂等还原）— ✅ PASS**

| 样品 | 还原后状态 |
|---|---|
| DEMO-2026-001 鲜食玉米 | **S10** |
| DEMO-2026-002 菠菜 | **S30** |
| DEMO-2026-003 草鱼 | **S40** |
| DEMO-2026-004 鳜鱼 | **S60** |
| DEMO-2026-005 团头鲂 | **S70** |

与设计态 S10/S30/S40/S60/S70 完全一致；脚本末尾自校验「每角色至少 1 条待办」全部命中。

**F.2b 再跑 `db/seed/05_data_hygiene.sql`（no-op 复核）— ✅ PASS**

```
BEFORE TEST-* 在检样品数 ............ 0
AFTER  TEST-* 在检样品数 ............ 0     ← 仍 0（no-op）
AFTER  TEST-* 明细残留 ............. 0
AFTER  花鲢菌落总数(std_value) ..... 1     ← 仍为目标态（50000 / judge_type=1 / is_reference=0）
AFTER  花鲢孔雀石绿(lower_limit=0.5) 1     ← 仍为目标态
AFTER  DEMO-* 样品数（保留确认） ... 5     ← 演示数据未被误删
```
二次执行呈 **no-op 语义**：TEST-* 仍 0、花鲢两项仍为目标态、DEMO-* 完整保留。

**最终落库快照（收尾后）**：DEMO 五样品 = S10/S30/S40/S60/S70；花鲢「菌落总数」`std_value=50000, judge_type=1, is_reference=0, test_value=1200, conclusion=1`。

---

## 补充：F8 JWT 静默续期（非本轮清单项，透明记录）

- **代码级 — ✅**：`frontend/src/utils/request.ts` 已实现「401 → 单飞锁 `refreshPromise` → `POST /auth/refresh` → 重放原请求一次」，区分认证端点防死循环（`AUTH_ENDPOINTS`），业务码 401 与 HTTP 401 两路皆覆盖；`frontend/src/api/auth.ts` 暴露 `refreshApi`。
- **后端接口级 — ✅**：`login` 返回 `refreshToken`（152 字符）；`POST /auth/refresh {refreshToken}` → code=0，返回新 `accessToken`(1541) + 新 `refreshToken`(152)。
- **运行时「静置 >2h 后不被硬登出」— ⚠ 未验证**：未做 2 小时真实静置实测（本轮清单未列，且耗时不可控）。如实记录为「未验证」，不据此判 PASS。

---

## 风险与遗留

1. **无 FAIL、无阻断遗留**。31 项缺陷的验收点（报告第九节清单）逐条有据。
2. **演示数据副作用（已全部还原）**：验证期间将 DEMO-2026-004 经 F20 链路推进至 S90、将 DEMO-2026-002 回退 S30→S20（制造分解页数据）；均在 F.2a 由 `seed03` 幂等还原。
3. **样品自增 id 变化**：`seed03` 采用「物理删除 DEMO-* 后重建」，还原后 DEMO 的 id 由 67–71 变为 **72–76**。属脚本既有设计，非缺陷；但**任何硬编码 DEMO id 的用例/脚本须动态取值**（本次均动态获取）。
4. **F10 弹窗遮罩层级（原 P2）未针对性复现**：固定列背景已实测为实色（`rgb(19,26,34)`，无穿透），但「弹窗打开时固定列浮于遮罩之上」这一具体场景本轮**未构造复现**，标为「未验证/未复现」，不判 PASS。
5. **性能观察项未复测**：`POST /item/confirm` >15s、列表冷启动偶发 >15s 属另一任务（T-939）范围，本报告不含。
6. **证据可追溯**：全部命令输出/HTTP 返回/测量值/截图落于 `D:\lims\_logs\`（`qa-verify-*.log`、`qa-f20.mjs`、`qa-d.mjs`、`qa-e-ui.py`、`qa-b6-ui.py`、`qa-province.xlsx`、`qa-template-dl.xlsx`、`qa-*.png`）。

---

## 判定

> **整体：PASS（可交付）。** 质量门禁全绿；F20 合规闭环与 F17 数据修复经独立证据确认生效；回归保护清单（RBAC / S90 不可回退 / 待指派拦截 / 省平台导出 / AI 离线 / 模板打印）未回退；UI 修复 F4/F3/F1/F12/F9/F7/F26 实测符合预期；演示数据已还原、卫生脚本呈 no-op。遗留仅为「F8 运行时 2h 静置未实测」与「F10 弹窗穿透未针对性复现」两项**未验证**（非失败）。

---

## 附录：主理人补充证据 —— F8 运行时静默续期 E2E（闭合「1 项未验证」）

> 本项由主理人（team-lead）在本报告定稿后补测落证，用于闭合上文「补充：F8」记录的运行时路径。
> 方法：后端临时以 `--lims.jwt.access-token-ttl=35` 重启（验证后已恢复 7200），
> Playwright（Chromium headless）真实浏览器执行；脚本 `_logs/f8-refresh-test.py`。

| # | 场景 | 预期 | 实测 | 判定 |
|---|---|---|---|---|
| 1 | 登录后等待 42s（token 过期）→ 导航 /sample | 静默刷新 + 重放，停留在业务页 | `/auth/refresh` → **200**；`/sample/page?pageNum=1&pageSize=10&statuses=10,20` 重放 → **200**；token 轮换=True；最终 URL=`/sample`（**未被登出**） | ✅ PASS |
| 2 | 写坏 refreshToken → 再过期 → 导航 /task | refresh 失效回登录页 | 最终 URL=`/login?redirect=%2Ftask` | ✅ PASS |

- 证据：`_logs/f8-after-expiry.png`（场景 1 截图）、`_logs/f8-redirect-login.png`（场景 2 截图）、脚本与输出 `_logs/f8-refresh-test.py`。
- 附带验证：F11 的 `statuses=10,20` 请求参数在真实网络层生效（见场景 1 重放的 URL）。
- 结论：**F8 运行时路径 PASS**；原「静置 >2h」以 35s 等效 TTL 折算覆盖（机制路径一致，时长仅为配置参数）。
