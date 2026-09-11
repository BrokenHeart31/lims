# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- **GLM：`agent/glm`（T-901 + T-902 草案已推送；本地引用 = f21fb4d）**
- **Copilot：`agent/copilot`（本轮：①修复 4070ea6 误删事故 `e476cf6`；②T-902 五条口径裁决定稿 ✅；③api-spec 样品域终审通过 ✅——**已全量推送 GitHub**：远程 agent/copilot=agent/glm=develop=main=`b9df438`，残缺树解除）**
- 豆包：`agent/doubao`（文件整理 + T-103 补 04 + 初步测试 ✅；T-903 product_name 补全待领）

## 🔴 4070ea6 误删事故与修复（全员必读）
- **事故**：GLM 的 `4070ea6`（角色调整落地）提交把工作区异常状态一并提交——**误删 backend/db/docs/frontend 共 118 个文件**，并把 4 个垃圾文件（空 .gitkeep 被改成中文碎片文件名，系 shell 误解析产物）提交到仓库根目录；后续 8a8f5eb/f21fb4d 继承残缺树，且**已推送远程 agent/glm=develop=main=f21fb4d**，即 GitHub 上 main 当前也是残缺树。
- **修复**：Copilot 在 `agent/copilot` 以修复提交 **`e476cf6`** 前滚恢复（从 fbe8062 取回 118 个文件 + 移除 4 个垃圾文件），不重写历史。**✅ 已推送：远程 agent/copilot=agent/glm=develop=main=`b9df438`（2026-09-11 19:45，ls-remote 核对），远程已恢复完整**。
- **附带损失**：T-902 草案 `docs/knowledge/2026-09-11-judge-engine-whitelist-draft.md` 从未入库且已不可恢复；Copilot 已依据 HANDOFF 16:10 摘要 + AGENTS 7.3 重建为**定稿** `docs/knowledge/2026-09-11-judge-engine-whitelist.md`（含裁决）。

## ⚠️ 2026-09-11 角色调整（用户决策，全员必读）
- **S 级 + A 级执行权全部归 GLM**（GLM 与 Copilot 同级）。
- **Copilot 只保留三类不可替代工作**：① api-spec 契约（起草协助 + 终审）；② 规则裁决（判定口径/跨模块语义歧义的最终解释）；③ diff 审查（合并前代码审查）。**不再承接 S/A 实现类任务**。
- 边界一句话：**GLM 实现，Copilot 把关**。
- 已落地修订：`AGENTS.md`（首页角色表 / 0.2 / 2.1 / 2.3 / 2.4 / 7.3 / 9 / 12）、`TODO.md`（分级说明 + T-401/501/601/701/702/801 Owner）、`prompts/glm.md`、`prompts/copilot.md`（两份原为角色名交叉错写，已全文重写）、`DECISIONS.md`。
- 分支名勘误：Copilot 分支为 **`agent/copilot`**（原 AGENTS 首页写 `agent/gpt` 已修正）。

## 项目位置
- 本地仓库：`D:\lims`（远程 https://github.com/BrokenHeart31/lims.git ）
- 合并路径：`agent/xxx → develop → main`（本轮由 GLM 执行 main 固化）

## 本轮占用文件（GLM / T-901+T-902，已提交并推送 4070ea6）
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
- ✅ **api-spec 样品域契约终审通过**（2026-09-11 Copilot）：5 接口路径/方法/权限标识（sample:import/confirm/query）与 SampleController、seed sys_menu 31/32/33、前端 api/sample.ts 逐字段一致；唯一调整 3.1 审计字段行补 `updatedBy`。
- ✅ **T-902 五条口径裁决定稿**（2026-09-11 Copilot，T-601 开工闸门已开）：**D1 采纳**（低于检出限视同未检出）/ **D2 采纳**（不得检出型按 ≥ 检出限才算检出，检出限 NULL + 数值 → 待判定）/ **D3 采纳含补充**（参考项计算并标注展示但不计入整体结论；全参考项 → 整体待判定）/ **D4 采纳补全矩阵**（`--`：未检出→合格，数值且无法视同 → 待判定）/ **D5 采纳**（T-401 一次性订正 judge_type，引擎只读 product_lib_item）。全文：docs/knowledge/2026-09-11-judge-engine-whitelist.md。说明书两处矛盾样例确认为旧数据瑕疵。
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
