# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- **GLM：`agent/glm`（本轮 T-901 治理落地 + T-902 白名单草案 🔵 进行中）**
- Copilot：`agent/copilot`（角色调整后转为**契约终审 + 规则裁决 + diff 审查**；待命：T-301 样品域契约终审、T-902 判定口径 5 条裁决）
- 豆包：`agent/doubao`（文件整理 + T-103 补 04 + 初步测试 ✅；新增 T-903 product_name 补全待领）

## ⚠️ 2026-09-11 角色调整（用户决策，全员必读）
- **S 级 + A 级执行权全部归 GLM**（GLM 与 Copilot 同级）。
- **Copilot 只保留三类不可替代工作**：① api-spec 契约（起草协助 + 终审）；② 规则裁决（判定口径/跨模块语义歧义的最终解释）；③ diff 审查（合并前代码审查）。**不再承接 S/A 实现类任务**。
- 边界一句话：**GLM 实现，Copilot 把关**。
- 已落地修订：`AGENTS.md`（首页角色表 / 0.2 / 2.1 / 2.3 / 2.4 / 7.3 / 9 / 12）、`TODO.md`（分级说明 + T-401/501/601/701/702/801 Owner）、`prompts/glm.md`、`prompts/copilot.md`（两份原为角色名交叉错写，已全文重写）、`DECISIONS.md`。
- 分支名勘误：Copilot 分支为 **`agent/copilot`**（原 AGENTS 首页写 `agent/gpt` 已修正）。

## 项目位置
- 本地仓库：`D:\lims`（远程 https://github.com/BrokenHeart31/lims.git ）
- 合并路径：`agent/xxx → develop → main`（本轮由 GLM 执行 main 固化）

## 本轮占用文件（GLM / T-901+T-902，进行中）
- 新增：`docs/knowledge/2026-09-11-judge-engine-whitelist-draft.md`（判定引擎表达式白名单草案 v1）
- 修改：`AGENTS.md`、`TODO.md`、`STATUS.md`、`DECISIONS.md`、`HANDOFF.md`、`prompts/glm.md`、`prompts/copilot.md`

## 上一轮占用文件（GLM / T-301，已提交）
- 新增：
  - 契约/数据：`docs/api/api-spec.md`（第 3 章样品域）、`db/init/05_sample_tables.sql`
  - 后端：`common/enums/{SampleStatus,SampleStatusTransition}.java`、`entity/{Sample,SampleImportBatch}.java`、`mapper/{SampleMapper,SampleImportBatchMapper}.java`、`dto/{SampleImportDTO,SampleUpdateDTO,SampleConfirmDTO}.java`、`vo/SampleImportResultVO.java`、`service/excel/SampleImportListener.java`、`service/SampleService.java`、`service/impl/SampleServiceImpl.java`、`controller/SampleController.java`、`src/test/java/com/lims/**`（2 个测试类）
  - 前端：`src/api/sample.ts`、`src/views/sample/index.vue`、`public/templates/sample_import_template.xlsx`
- 修改：`backend/pom.xml`（+EasyExcel 3.3.4）、`frontend/src/router/index.ts`（+样品登记路由）、`frontend/src/layouts/MainLayout.vue`（+样品登记菜单）、`docs/knowledge/2026-09-11-sample-statemachine-research.md`（+落地补充）

## 他人占用
- （无）

## 当前状态/阻塞
- ✅ **T-301 全链路完成并自测通过**（2026-09-11 GLM）：
  - 后端 `mvn clean compile` / `package` ✅；`mvn test` **9 项单测全过**（状态机 7 + 导入监听器 2）。
  - 前端 `npm run lint` ✅（0 问题）、`npm run build`（vue-tsc + vite）✅。
  - 运行期实测（本机 MySQL 8 + `db/init/05` + `java -jar`）：nj002 登录 → 导入模板成功（S10）→ 同文件重复导入被拒 → 分页可查 → 批量登记确认 S10→S20 → 重复确认拦截。详见 HANDOFF.md。
- ✅ 数据库实测：`db/init/01→02→03→04→05` → 导入 lims.sql（customer/dept 改名 legacy）→ `V1` → `seed 01→02`（采样单演示数据由导入模板替代）。
- ⚠️ **契约待 Copilot 终审**：api-spec 样品域由 GLM 起草（Copilot 改派 T-301 给 GLM），字段/权限标识/返回结构请终审。**注意：角色调整后「起草」归 GLM，「终审」仍归 Copilot**，流程不变。
- 🔵 **待 Copilot 裁决（T-902，阻塞 T-601 实现）**：判定引擎表达式白名单草案已出（`docs/knowledge/2026-09-11-judge-engine-whitelist-draft.md`），5 条裁决点：**D1** 低于检出限是否判合格 / **D2** `不得检出` 型是否按「≥ 检出限才算检出」/ **D3** 参考性限量（834 条）不合格是否计入整体结论 / **D4** `--` 且检出限为 NULL 时是否判待判定 / **D5** 是否需从旧 `prj_detail` 反向回填 lib 的 jt2/jt3。裁决后 T-601 即为纯编码任务。
- ⚠️ **数据缺口（T-903）**：`product_lib.product_name` / `category` **全为 NULL**，可从旧 `product` 表（`libName` → product_name、`prd_category` → category）补齐；建议在 T-401 前完成，否则项目分解无法按产品名匹配套库。
- ⚠️ **lib 数据覆盖不全**：`product_lib_item` 的 `basis_code` 唯一值仅 `GB 2763-2021`（农残一本标准），且 `judge_type` 全为 1；真实业务数据存在的 `不得检出` 型未被迁移。见 D5。
- ⚠️ **权限标识以 `sample:query` 为准**（非交接留言中的 `sample:list`）：seed `sys_menu` 31/32/33 与 AGENTS 8.2 均为 `sample:import/sample:confirm/sample:query`，`/me` 实际下发 `sample:query`，接口 `@PreAuthorize` 必须同值。
- ✅ **AGENTS.md 首页表格 Copilot 分支笔误（`agent/gpt`）已修正为 `agent/copilot`**；两份 prompts 文件角色交叉错写已重写（T-901）。
- ⚠️ 前端动态路由（按 /me 菜单树生成）仍未接入，当前静态路由 + 静态菜单；建议随 T-801 一起做（**A 级，现归 GLM**）。
- ⚠️ git 沙箱：`.git/refs/heads/agent/*` 引用会被 git.exe 静默丢弃（每次 git 操作后必须 shell 回填，本轮已回填多次）；push 需 `git -c http.sslVerify=false`。
- ℹ️ 本机 MySQL 实际密码 123456（非 AGENTS 约定 11111111），在 gitignore 的 application-dev.yml。

## 进度评估（距整个项目圆满完成）
- **七阶段主干任务：3/9 落地（约 33%）**。
  - ✅ 已完成：阶段一（T-101/102/103/104）、阶段二（T-201 监抽任务）、阶段三（T-301 样品登记）。
  - ⬜ 剩余：**T-401**（项目分解自动套库，S）、**T-501**（任务自动分配 NA/XA/SA + 方法资质，S）、**T-601**（结果录入 + 自动判定引擎，S）、**T-701**（审核/签发 S60→S70→S80，S）、**T-702**（CMA/CMA-CATL 报告生成 S80→S90，S）、**T-801**（在检/历史/项目库查询，A）、**T-802**（省平台上报导出，B）；另系统管理页（`/api/sys/*`）尚未实现。
  - 🔵 治理/T-901、T-902 本轮进行；T-903（product_name 补全）待豆包领。
- **剩余任务全部由 GLM 承担**（角色调整后 S+A 归 GLM）：T-401/501/601/701/702（S）+ T-801 + 动态路由（A）；B 级 T-802/T-903 归豆包。**Copilot 为把关方，非产能方**——GLM 额度是当前唯一的关键路径风险。
- **剩余以高难度 S 级为主**：自动判定引擎（AGENTS 7.3 六条规则）、报告模板合成（CMA/CMA-CATL）、任务分配资质匹配，合计约占**剩余工作量 70%**；A/B 级（T-801/802 + 动态路由 + 系统管理页）约占 30%。整体距完成约**还差 60%–65% 工作量**。
- **数据基础已就绪**：product_lib 92 / product_lib_item 3728（含 judge_type 判定类型）/ basis 859 / tester_method 已建表，T-401/T-501/T-601 的数据依赖已备齐，可直接推进。**但 lib 覆盖度有缺口**：product_name/category 全 NULL（T-903）、basis_code 仅 GB 2763-2021、judge_type 全为 1。
- **本机实测数据形态（T-902 产出，支撑 T-601）**：`product_lib_item.std_value` 100% 纯数值、`is_reference=1` 834 条、`lower_limit` 有值 1032 条；旧 `prj_detail` 463 条中 `stdValue ∈ {数值, 不得检出, --}`、`jyResult ∈ {数值, 未检出}`、`item_evaluate` 合格 441/不合格 20；**不合格口径反推为「数值结果 ≥ 检出限即检出」**。
- **可复现资产已就绪**：`.agents/skills/` 四技能 + `docs/knowledge/` 三篇（新增判定引擎白名单草案），后续 S 级任务可按模板快速实现。
