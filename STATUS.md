# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- **GLM：`agent/glm`（T-401 ✅ → T-906 UI「Aurora Glass」主题 + T-907 V3 fail-loud + T-908 治理二次调整；T-905 裁决已由 Copilot 落档 `2e9f471`）**
- **Copilot：`agent/copilot`（19:45 事故修复+裁决终审已推送 ✅；20:30 **T-905 裁决定稿**：R1 采纳 / R3 否决 / 引擎只读 sample_item 追认 / T-601 单测构造数据裁定，见 DECISIONS 与 whitelist D5 节）**
- 豆包：`agent/doubao`（文件整理 + T-103 补 04 + 初步测试 ✅；T-903 已由 GLM 代做，可领 T-802）

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

## 本轮占用文件（GLM / T-906 + T-907 + T-908）
- 新增（前端主题）：`src/styles/{tokens,base,element-override}.css`、`src/components/GlassFilter.vue`
- 修改（前端）：`src/main.ts`（+EP 暗色 css-vars + 主题引入 + html.dark）、`src/App.vue`（+GlassFilter + 路由过渡）、`src/layouts/MainLayout.vue`（外壳重塑）、`src/views/login/index.vue`（极光登录页）、`src/views/dashboard/index.vue`（hero + 八阶段网格）、`src/views/sample/index.vue`（硬编码浅色改令牌）
- 修改（数据）：`db/migrations/V3__correct_product_lib_item_judge_type.sql`（派生表统一计算 + NULL 安全比较 + fail-loud 断言）
- 新增（知识）：`docs/knowledge/2026-09-11-ui-design-mineradio-research.md`
- 修改（治理）：`AGENTS.md`（2.3 重写 / 2.4 / **2.6 自裁机制新增** / **2.7 豆包分工新增** / **5.1 UI 基准新增** / 首页角色表 / 契约与审查表述）、`DECISIONS.md`、`TODO.md`、`STATUS.md`、`HANDOFF.md`
- 本地专属（gitignore，不入库）：`backend/src/main/resources/application-dev.yml`（本机 MySQL 口令覆盖）

## 上一轮占用文件（GLM / T-401 + T-903 + T-904 + T-905）
- 新增（后端）：`entity/{SampleItem,ProductLib,ProductLibItem}.java`、`mapper/{SampleItemMapper,ProductLibMapper,ProductLibItemMapper}.java`、`dto/{ItemSaveDTO,ItemConfirmDTO}.java`、`vo/{ItemMatchVO,ItemPendingVO}.java`、`service/ItemService.java`、`service/impl/ItemServiceImpl.java`、`controller/ItemController.java`、`src/test/java/com/lims/service/impl/ItemServiceImplTest.java`
- 新增（前端）：`src/api/item.ts`、`src/views/item/index.vue`
- 新增（契约/数据）：`docs/api/api-spec.md` 第 4 章（原第 4 章顺延为第 5 章）、`db/init/06_item_tables.sql`、`db/migrations/V2__fill_product_lib_name_category.sql`、`db/migrations/V3__correct_product_lib_item_judge_type.sql`
- 新增（治理/知识）：`docs/journal/README.md`、`docs/journal/2026-09-11-glm-t401-item-decompose.md`、`docs/knowledge/2026-09-11-adjudication-request-d5.md`
- 修改：`AGENTS.md`（新增 2.5 工作纪律三件套、2.2 开工五步→六步、第 3 章目录 +docs/journal/、第 12 章红字核对暂存区）、`TODO.md`、`STATUS.md`、`HANDOFF.md`、`frontend/src/router/index.ts`（+/item/decompose）、`frontend/src/layouts/MainLayout.vue`（+项目分解菜单）

## 上一轮占用文件（GLM / T-901+T-902，已提交并推送 4070ea6 → 已由 Copilot 修复为 b9df438）
- 新增：`docs/knowledge/2026-09-11-judge-engine-whitelist-draft.md`（判定引擎表达式白名单草案 v1，**已在事故中丢失**，Copilot 已重建为定稿）
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
- ✅ **T-401 全链路完成并自测通过**（2026-09-11 GLM）：
  - 契约 `docs/api/api-spec.md` 第 4 章 5 个接口（match/list/save/confirm/pending），权限 `item:decompose`（list 额外放行 `sample:query`）。
  - 建表 `db/init/06_item_tables.sql`（`sample_item`，含 8 个标准库快照下沉字段 + `uk_sample_item_order` 三列唯一键）。
  - 后端 `mvn test` **23 项全过**（状态机 7 + 导入监听 2 + **本轮新增 ItemServiceImpl 14**）。
  - 前端 `npm run build`（vue-tsc + vite）✅、`npm run lint` ✅ **0 错误 0 警告**。
  - 前端页面 `views/item/index.vue`：待分解样品列表（含分解进度 itemCount）→ 分解抽屉（自动套库初稿 + 人工增删 + 项次重排 + 覆盖式保存 + 确认流转）。
- ✅ **T-903 完成**（2026-09-11 GLM 代豆包）：`V2__fill_product_lib_name_category.sql` 从旧 `product` 表补齐，product_name 0→92、category 0→92，残留 0，幂等可重跑。
- ✅ **T-904 完成**：工作纪律三件套写入 AGENTS 2.5 节（工作日记 / 进度百分比固定口径 / 动手前先检索），2.2 开工五步→六步，`docs/journal/` 建立。
- 🔵 **T-905（D5 裁决请求 #1）已提交待裁**：`docs/knowledge/2026-09-11-adjudication-request-d5.md`。
- 🔴 **⚠️ D5 前提被实测推翻（重要，全员必读）**：`product_lib_item` 3728 行 `std_value` **100% 纯数值**（`≤数值`/`不得检出`/`不得使用`/`--` 各 0 行）；数据源旧 `lib` 表同样 100% 纯数值，**不是迁移漏迁**；`prj_detail` 里 220 条 `不得检出` 只涉及 5 个兽残项目名，且**在 `product_lib_item` 中 0 匹配**——两表不同源（`lib` 仅覆盖农残 GB 2763-2021）。因此 `V3__correct_product_lib_item_judge_type.sql` 实测为**零变更（no-op）**，定位已从「数据回填」改为「可重跑的口径校验/归一化器」。建议方案 R1（推荐，已实现）交 Copilot 裁决。
- ⚠️ **T-601 判据补充**：判定引擎只读 `sample_item`（快照下沉），**禁止回溯 `product_lib_item`**；容器字段已在 06 建表中固化。
- ✅ **T-301 全链路完成并自测通过**（2026-09-11 GLM）：后端 9 项单测 + 前端 build/lint + 运行期实测；api-spec 样品域契约 Copilot 终审通过。
- ✅ 数据库实测：`db/init/01→02→03→04→05→06` → 导入 lims.sql（customer/dept 改名 legacy）→ `V1` → `V2` → `V3` → `seed 01→02`。
- ⚠️ **权限标识以 `sample:query` 为准**（非交接留言中的 `sample:list`）：seed `sys_menu` 31/32/33 与 AGENTS 8.2 均为 `sample:import/sample:confirm/sample:query`。
- ⚠️ 前端动态路由（按 /me 菜单树生成）仍未接入，当前静态路由 + 静态菜单；建议随 T-801 一起做（**A 级，现归 GLM**）。
- ⚠️ git 沙箱：`.git/refs/heads/agent/*` 引用会被 git.exe 静默丢弃（每次 git 操作后必须 shell 回填）；push 需 `git -c http.sslVerify=false`。
- ℹ️ 本机 MySQL 实际密码 123456（非 AGENTS 约定 11111111），在 gitignore 的 application-dev.yml。

## 进度评估（距整个项目圆满完成）
**总进度：约 46%**（按 AGENTS 2.5 节固定口径：业务主干 55% + 前端 15% + 数据 10% + 质量 10% + 工程化 10%）
- **业务主干：4/9 阶段落地（55% × 4/9 ≈ 24.4%）**；**T-501 本轮进行中（保守未计入，完成后将 +6.1%）**
  - ✅ 阶段一 / 二 / 三 / **四（T-401 项目分解）**
  - 🔵 **T-501 检验任务安排**（本轮开工）
  - ✅ 阶段一（T-101/102/103/104）、阶段二（T-201）、阶段三（T-301）、**阶段四（T-401 项目分解自动套库，本轮完成）**。
  - ⬜ 剩余：**T-501**（任务自动分配 NA/XA/SA + 方法资质，S）、**T-601**（结果录入 + 自动判定引擎，S，口径已定稿）、**T-701**（审核/签发 S60→S70→S80，S）、**T-702**（CMA/CMA-CATL 报告生成 S80→S90，S）、**T-801**（在检/历史/项目库查询，A）、**T-802**（省平台上报导出，B）。
- **前端：约 11/15**——五页齐备 + **统一暗色主题体系（Aurora Glass：设计令牌 / SVG 折射玻璃 / Element Plus 暗色适配）已落地**；动态路由未接入。
- **数据：约 8/10**——01→06 建表齐备，V1/V2 迁移完成、V3 口径校验器已产出（待裁决），seed 齐备。
- **质量：约 7/10**——后端 23 项单测全过、前端 build+lint 全绿；判定引擎单测待 T-601。
- **工程化：约 8/10**——治理齐备 + 三件套制度化 + **自裁机制 / 豆包分工 / UI 基准 / 许可合规红线**均已落档；`lims-stage-delivery` 技能已建。
- 🔵 治理：T-901/T-902/T-903/T-904 ✅完成；T-905（D5 裁决请求 #1）待 Copilot 裁定。
- **剩余任务全部由 GLM 承担**（角色调整后 S+A 归 GLM）：T-401/501/601/701/702（S）+ T-801 + 动态路由（A）；B 级 T-802/T-903 归豆包。**Copilot 为把关方，非产能方**——GLM 额度是当前唯一的关键路径风险。
- **剩余以高难度 S 级为主**：自动判定引擎（AGENTS 7.3 六条规则）、报告模板合成（CMA/CMA-CATL）、任务分配资质匹配，合计约占**剩余工作量 70%**；A/B 级（T-801/802 + 动态路由 + 系统管理页）约占 30%。整体距完成约**还差 60%–65% 工作量**。
- **数据基础已就绪**：product_lib 92 / product_lib_item 3728（含 judge_type 判定类型）/ basis 859 / tester_method 已建表，T-401/T-501/T-601 的数据依赖已备齐，可直接推进。**但 lib 覆盖度有缺口**：product_name/category 全 NULL（T-903）、basis_code 仅 GB 2763-2021、judge_type 全为 1。
- **本机实测数据形态（T-902 产出，支撑 T-601）**：`product_lib_item.std_value` 100% 纯数值、`is_reference=1` 834 条、`lower_limit` 有值 1032 条；旧 `prj_detail` 463 条中 `stdValue ∈ {数值, 不得检出, --}`、`jyResult ∈ {数值, 未检出}`、`item_evaluate` 合格 441/不合格 20；**不合格口径反推为「数值结果 ≥ 检出限即检出」**。
- **可复现资产已就绪**：`.agents/skills/` 四技能 + `docs/knowledge/` 三篇（新增判定引擎白名单草案），后续 S 级任务可按模板快速实现。
