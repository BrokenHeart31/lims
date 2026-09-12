# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- **GLM：`agent/glm`（T-401 ✅ / T-906~908 ✅ / T-501 ✅ / T-601 ✅ / T-701 + T-911 + T-912 ✅ / **T-913 UI 重整 🟢 待提交**）**
- **Copilot：`agent/copilot`（`d1910dc`：T-601 复核终审通过 + T-701 只读铺垫；无阻塞项，配额剩余 1 次且不得阻塞）**
- 豆包：`agent/doubao`（2026-09-12 豆包轮：T-802 格式定稿+样例已交付于 docs/，项目已启动供测试；后端导出端点仍归 GLM）
- **同步口径（待 T-913 提交后刷新）**：`agent/glm` = `develop` = `main` = **`e416550`**（T-701 + T-911 + T-912；2026-09-12 16:45 推送并 `ls-remote` 核对；令牌未写入任何仓库文件，已提醒用户撤销重建）。
- **⚠️ 环境**：本机 `git.exe` 已不在 PATH（`C:\Users\Chen\Desktop\Git` 被删），须用全路径
  `C:\Users\Chen\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe`。

## 本轮占用文件（豆包 / T-802 格式定稿 + 项目启动，2026-09-12 19:15）
- 新增（知识）：`docs/knowledge/2026-09-12-province-export-format.md`（省平台上报 10 列格式 + 字段映射 + 参考 SQL + 待 GLM 落档点）
- 新增（参考样例）：`docs/reference/province_export_sample.xlsx`（本机样品 1 真实数据 7 行）
- 新增（日记）：`docs/journal/2026-09-12-doubao-t802-and-launch.md`
- 修改（治理）：`TODO.md`（T-802 标 🔵进行中(豆包)）、`HANDOFF.md`（+豆包交接段）、本文件
- **未动 backend/ frontend/ 代码与 db/ 脚本**；项目已本地启动（前端 5173 / 后端 8080）供用户测试。

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

## 本轮占用文件（GLM / T-913 UI 重整，2026-09-12 18:20 → 18:50 已推送）

**✅ 远端已同步**：`origin/{agent/glm, develop, main} = c190441`（含 T-913 主体 + HANDOFF 补 commit，2 ahead of e416550）。`agent/copilot = d1910dc`、`agent/doubao = 6282c64` 保持未动。
- 扩展（设计令牌）：`frontend/src/styles/tokens.css`（+spacing scale + tone 双色 + header 字号）
- 扩展（EP 覆盖）：`frontend/src/styles/element-override.css`（统一行高 44 / 表单 gap 18 / 圆角 8 / hover 青调）
- 新增（公共组件）：`frontend/src/components/common/{PageHeader,AppCard,StatCard,StatusBadge,AppEmpty,AppBreadcrumb}.vue`（6 件）
- 新增（工具）：`frontend/src/utils/confirm.ts`（confirm/confirmReturn/askConfirm 三函数）+ `utils/sampleStatus.ts`（状态 label/tone 映射）
- 修改（壳）：`frontend/src/layouts/MainLayout.vue`（224px 侧栏分组 5 组 + 64px Header 含搜索/通知/帮助/用户菜单 + 面包屑插槽）
- 修改（工作台）：`frontend/src/views/dashboard/index.vue`（hero + 4 KPI + 8 阶段时间线 + 最近任务表 + 异常 sparkline）
- 修改（业务页）：`frontend/src/views/{assign,item,result,report/audit,task,sample}/index.vue`（7 页统一：PageHeader + AppCard + StatusBadge + AppEmpty + askConfirm）
- 新增（日记）：`docs/journal/2026-09-12-glm-ui-overhaul.md`
- 新增（知识）：`docs/knowledge/2026-09-12-ui-component-library.md`
- 新增（技能）：`.agents/skills/lims-ui-overhaul/SKILL.md`
- 本地专属（gitignore，不入库）：`backend/src/main/resources/application-dev.yml`

## 本轮占用文件（GLM / T-701 + T-911 + T-912，2026-09-12 16:20）
- 修改（状态机）：`backend/.../common/enums/SampleStatusTransition.java`（**新增独立 `RETURN` 退回白名单** + `assertReturn/canReturn/returnAllowed`）
- 新增（枚举）：`backend/.../common/enums/AuditAction.java`（1 审核通过 / 2 审核退回 / 3 签发）
- 新增（实体/Mapper）：`backend/.../entity/SampleAuditLog.java`、`backend/.../mapper/SampleAuditLogMapper.java`
- 新增（DTO）：`backend/.../dto/{AuditApproveDTO,AuditReturnDTO,ReportSignDTO}.java`
- 新增（VO）：`backend/.../vo/{AuditPendingVO,AuditDetailVO,AuditActionVO}.java`
- 新增（**T-912 口径**）：`backend/.../service/result/ResultEntryPolicy.java`（「有效录入」唯一权威 + 判定类型字典）
- 新增（服务）：`backend/.../service/AuditService.java` + `impl/AuditServiceImpl.java`
- 新增（Controller）：`backend/.../controller/ReportController.java`（`/api/report/*`）
- 修改（T-912 落地）：`backend/.../service/impl/ResultServiceImpl.java`（录齐/entered 走新口径、+abnormalCount）、`vo/ResultDetailVO.java`（`entered` 改真实字段、未录入不出网 conclusion）、`vo/ResultPendingVO.java`（+abnormalCount）
- 修改（实体）：`backend/.../entity/Sample.java`（+`auditBy/auditAt/auditOpinion/signBy/signAt`）
- 新增（单测）：`backend/src/test/.../service/impl/AuditServiceImplTest.java`（15 项）
- 修改（单测）：`SampleStatusTransitionTest.java`（+3 项退回白名单不变式）、`ResultServiceImplTest.java`（+4 项 T-912 口径）
- 新增（数据）：`db/init/08_audit_tables.sql`（`sample_audit_log`）、`db/migrations/V5__add_sample_audit_columns.sql`
- 修改（数据）：`db/init/05_sample_tables.sql`（+审核/签发 5 列）
- 修改（契约）：`docs/api/api-spec.md`（**0.3 分页裁决** + **新增第 7 章 `/api/report` 域** 6 接口；原第 7 章「待落地域」顺延为第 8 章）
- 新增（前端）：`frontend/src/api/report.ts` + `views/report/audit.vue`（双页签 + 异常项清单 + 放行确认）
- 修改（前端）：`frontend/src/router/index.ts`（+ `/report/audit`）、`frontend/src/layouts/MainLayout.vue`（+ 报告审核菜单）
- 修改（知识）：`docs/knowledge/2026-09-11-sample-statemachine-research.md`（+「正向/退回两张独立白名单」定稿节；修正原第 5 条过时表述）
- 修改（技能）：`.agents/skills/lims-stage-delivery/SKILL.md`（原则 4 升级：双白名单 + 审计留痕 + 放行红线 + 「未录入≠待判定」；环境备忘 +git 全路径/后端重启/ref 被吞症状）
- 新增（日记）：`docs/journal/2026-09-12-glm-t701-audit-sign.md`
- 本地专属（gitignore，不入库）：`backend/src/main/resources/application-dev.yml`

## 本轮占用文件（Copilot / T-601 复核终审 + T-701 铺垫，2026-09-12 15:32）
- 新增（日记）：`docs/journal/2026-09-12-copilot-t601-review.md`
- 修改（治理）：`STATUS.md`、`TODO.md`（T-601 追加终审结论 + 新增 T-911/T-912）、`HANDOFF.md`、`DECISIONS.md`
- 只读复核（未改动）：`service/judge/*`、`ResultServiceImpl`、`ResultController`、3 DTO、VO、
  `07_result_tables.sql`、`V4`、api-spec 第 6 章、`frontend/src/api/result.ts`、`views/result/index.vue`
- 环境修复（不入库）：回填 `refs/heads/agent/{copilot,doubao}`；本机 git 改用 PortableGit 全路径

## 上一轮占用文件（GLM / T-601 + T-909 + T-910）
- 新增（判定引擎，纯函数）：`backend/.../service/judge/{JudgeEngine,JudgeInput,JudgeOutcome}.java`
- 新增（枚举）：`backend/.../common/enums/{ResultConclusion,ConclusionSource}.java`
- 新增（实体/Mapper）：`backend/.../entity/SampleResult.java`、`backend/.../mapper/SampleResultMapper.java`
- 新增（DTO）：`backend/.../dto/{ResultJudgeDTO,ResultSaveDTO,ResultSubmitDTO}.java`
- 新增（VO）：`backend/.../vo/{ResultPendingVO,ResultDetailVO,ResultJudgeVO,ResultSaveVO}.java`
- 新增（服务）：`backend/.../service/ResultService.java` + `impl/ResultServiceImpl.java`
- 新增（Controller）：`backend/.../controller/ResultController.java`
- 新增（单测）：`backend/src/test/.../service/judge/JudgeEngineTest.java`（34 项）+ `service/impl/ResultServiceImplTest.java`（15 项）
- 修改（实体）：`backend/.../entity/Sample.java`（+`conclusion` 整体结论 + `getConclusionLabel()`）
- 新增（数据）：`db/init/07_result_tables.sql`（`sample_result`）、`db/migrations/V4__add_sample_conclusion.sql`
- 修改（数据）：`db/init/05_sample_tables.sql`（+`conclusion` 列）
- 修改（契约）：`docs/api/api-spec.md`（新增第 6 章 `/api/result` 域，5 端点 + 判定矩阵；原第 6 章「待落地域」顺延为第 7 章）
- 新增（前端）：`frontend/src/api/result.ts` + `views/result/index.vue`
- 修改（前端）：`frontend/src/router/index.ts`（+ `/result/entry`）、`frontend/src/layouts/MainLayout.vue`（+ 结果录入菜单）
- 新增（知识）：`docs/knowledge/2026-09-12-judge-engine-research.md`（规则引擎选型侦察 + ALCOA+ 留痕依据）
- 新增（技能）：`.agents/skills/judge-engine/SKILL.md`
- 修改（技能）：`.agents/skills/{lims-stage-delivery,sandbox-git-push}/SKILL.md`
- 新增（日记）：`docs/journal/2026-09-12-glm-t601-result-judge.md`

## 上一轮占用文件（GLM / T-501）
- 修改契约：`docs/api/api-spec.md`（新增第 5 章 `/api/assign` 域，5 端点 + 0/1/2/3 规则）
- 修改数据：`db/init/06_item_tables.sql`（sample_item 加 6 分配字段）
- 修改实体：`backend/.../entity/SampleItem.java`
- 新增实体：`backend/.../entity/{UserMethod,TesterMethod}.java`
- 新增 Mapper：`backend/.../mapper/{UserMethod,TesterMethod}Mapper.java`
- 新增 DTO：`backend/.../dto/{AssignAuto,AssignConfirm,AssignReassign}DTO.java`
- 新增 VO：`backend/.../vo/{AssignPending,AssignDetail,AssignAutoResult}VO.java`
- 新增枚举：`backend/.../common/enums/AssignType.java`
- 新增服务：`backend/.../service/AssignService.java` + `impl/AssignServiceImpl.java`
- 新增 Controller：`backend/.../controller/AssignController.java`
- 新增单测：`backend/src/test/.../AssignServiceImplTest.java`（14 项全过）
- 新增前端：`frontend/src/api/assign.ts` + `views/assign/index.vue`
- 修改前端路由：`frontend/src/router/index.ts`（+ `/assign`）
- 修改前端菜单：`frontend/src/layouts/MainLayout.vue`（+ 任务安排）
- 新增日记：`docs/journal/2026-09-11-glm-t501-assign.md`

## 上一轮占用文件（GLM / T-906 + T-907 + T-908）
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
- ✅ **T-701 审核/签发 + 审核退回 全链路完成（2026-09-12 GLM）** — 阶段七上半落地：
  - 契约 `docs/api/api-spec.md` **第 7 章** 6 接口（audit/pending、sign/pending、detail、audit/approve、audit/return、sign），
    权限 `report:audit`（seed id=711）/ `report:sign`（id=712）；原「待落地域」顺延第 8 章。
  - 状态机：`SampleStatusTransition` **新增独立 `RETURN` 白名单**（仅 S60→S50）+ `assertReturn`——
    正向表**不含**该边（单测固化 `assertTransition(S60,S50)` 必须拒绝）。
  - 数据：`db/init/08_audit_tables.sql`（`sample_audit_log` 只追加流水：action/from/to/opinion/**abnormal_confirmed**/operator/time）
    + `db/migrations/V5`（`sample_info` 加 `audit_by/audit_at/audit_opinion/sign_by/sign_at` 当前有效值，供 T-702 报告署名）。
  - 后端：`AuditServiceImpl`（三条不变式：正向/退回分道、流水只追加、异常项不静默放行）+ `ReportController`。
  - **放行红线**：存在异常项（未录入/待判定）时 `approve` 必须带 `abnormalConfirmed=true`，否则 400；
    前端未勾选时按钮禁用；流水留痕 `abnormal_confirmed=1`。
  - 前端：`views/report/audit.vue`（待审核/待签发双页签 + 抽屉：异常项清单标红 + 确认勾选 + 单项结果 + 审核/签发操作 + 流水表）
    + 路由 `/report/audit` + 菜单「报告审核」；支持深链 `?sampleId=N` 直达。
  - 质量门禁：后端 `mvn test` **107/107**（新增 22）、前端 `npm run lint` 0/0、`npm run build` ✅。
  - ✅ **端到端 54/54 断言通过且可重复**（重跑仍 54/54）：S40→录入→S50→提交→S60→**审核被拒(未确认异常)**→
    **退回→S50→回到检验员待办**→重新提交→S60→**审核通过(已确认)**→S70→签发→S80；负向 5 例；T-912 专项 6 断言。
  - ✅ **视觉回归**：审核列表页 + 审核抽屉（含异常项清单与放行勾选框）Edge headless 截图确认。
  - ℹ️ 本机数据已落在 **S80**（含 3 条审核流水），**T-702 报告生成可直接开工**。
- ✅ **T-911 / T-912 已自裁并落地（2026-09-12 GLM，见 DECISIONS）**：
  - T-911：**保留分页双轨、不追溯改**；api-spec 0.3 明确「新域一律 `current`/`size`」。
  - T-912：**「已录入」= `testValue` 非空白 ∥ jt3 已人工选结论**（`ResultEntryPolicy`）；空值行**阻断提交**；
    且**「未录入」（操作缺漏，阻断）与「待判定」（数据缺口，不阻断）严格区分**；审核页仍强制展示异常项清单。
- ✅ **T-601 复核终审通过（2026-09-12 15:32 Copilot，独立实测）**：mvn test 85/85 复跑 BUILD SUCCESS；
  判定矩阵与 T-902 D1–D5 逐格一致、D3 聚合正确；判定域对标准库零回溯（grep 零命中）；
  37 文件清单与申报一致；「不得检出/未检出」常量修复固化。**保留意见 2 条 = T-911/T-912，本轮已裁并落地**。
- ⚠️ **T-701 铺垫（只读，未实现）**：状态机正向白名单 S60→S70→S80→S90 已就位；
  **「审核退回→S50」缺失实锤**（S60 出边仅 S70）。设计建议已落 HANDOFF：退回走独立 RETURN 表
  + `assertReturn` 专用方法（勿塞 VALID 正向白名单），同步 AGENTS 7.2 + 单测；
  审核页放行红线：通过前展示「待判定/空值项」清单并显式确认。
- ⚠️ **环境**：本机 git.exe 不在 PATH（注册表指向目录已删），用
  `C:\Users\Chen\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe` 全路径。
- ✅ **T-601 结果录入 + 自动判定引擎 全链路完成（2026-09-12 GLM）** — 阶段六落地：
  - 契约 `docs/api/api-spec.md` **第 6 章** 5 接口（pending/detail/judge/save/submit），权限 `result:entry`（与 seed `sys_menu` id=61 一致）。
  - 数据：`db/init/07_result_tables.sql`（`sample_result`：原始值 + 结论 + 来源 + 判定依据说明，唯一键 `(sample_item_id, deleted)` 一项一行）+ `db/migrations/V4`（`sample_info.conclusion` 整体结论）。
  - 后端：**判定引擎为纯函数**（`service/judge/`，闭集白名单矩阵 + `BigDecimal.compareTo` + 闭集外一律「待判定 + WARN」），编排在 `ResultServiceImpl`（覆盖式 upsert + S40→S50→S60 双保险 + 整体结论聚合）。
  - ✅ **规范/形态定稿**：**不引入任何规则引擎/表达式引擎**（`std_value` 是数据不是表达式，禁止被执行）——见 `docs/knowledge/2026-09-12-judge-engine-research.md`。
  - 质量门禁：后端 `mvn test` **85/85**（新增 49 = 引擎 34 + 服务 15）、前端 `npm run build` ✅、`npm run lint` ✅ 0 错误 0 警告。
  - ✅ **端到端实测 45/45 断言通过**（本机 MySQL + 8080 起服务）：登录 → 待录入列表 → 明细 → **13 条判定矩阵预览（覆盖 jt1/jt2/jt3/`--`/D1/D2）** → 保存（S40→S50，整体结论=不合格）→ 提交（S50→S60）→ 负向（越态 400 / 空 items 400 / 单项不存在 400）。
  - ✅ **视觉回归**：结果录入页（新）+ 任务安排页（**T-501 遗留项已补验**）Edge headless 截图确认，菜单/表格/进度/标签均正常。
  - ✅ **fail-loud 实证**：`孔雀石绿`（不得检出 + 检出限为空 + 数值）落「待判定」，`judge_basis` 与 WARN 日志双留痕。
  - ⚠️ **给 T-701 的输入**：`sample_info.conclusion`（整体结论）与 `sample_result.judge_basis` 已可直接消费；「待判定」不阻断流转（放行红线在审核/签发）。
- 🔵 **T-909（判定引擎选型侦察）/ T-910（可复现技能沉淀）已完成**：知识库 `docs/knowledge/2026-09-12-judge-engine-research.md`；技能 `.agents/skills/judge-engine/SKILL.md` 新建，`lims-stage-delivery`（+第 7.5 步端到端/视觉回归 + 环境坑）与 `sandbox-git-push`（+规则 6 hash 双验证 / 规则 7 临时文件与并行 Edit 覆盖）更新。
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
**总进度：约 84%**（按 AGENTS 2.5 节固定口径：业务主干 55% + 前端 15% + 数据 10% + 质量 10% + 工程化 10%）
- **业务主干：7/9 阶段落地（55% × 7/9 ≈ 42.8%）** — 本轮 UI 重整不增减阶段。
  - ✅ 阶段一 / 二 / 三 / 四（T-401） / 五（T-501） / 六（T-601） / 七上半（T-701 审核签发）
  - ⬜ 剩余：**T-702**（CMA/CMA-CATL 报告生成 S80→S90，S，阶段七下半）、**T-801**（在检/历史/项目库查询，A，阶段九）、**T-802**（省平台上报导出，B，阶段九）
- **前端：约 14.5/15（+1.0）**——**八个业务页齐备 + 全量 UI 重整**：6 个公共组件（PageHeader/AppCard/StatusBadge/AppEmpty/StatCard/AppBreadcrumb）+ 1 个 confirm 工具 + 1 个状态映射；shell（侧栏分组 + Header + 面包屑）、Dashboard（hero+4 KPI+8 阶段+最近任务）已升级；动态路由未接入、系统管理 7 页待统一下一轮。
- **数据：约 9/10**——01→08 建表齐备，V1~V5 迁移齐备（V3 为可重跑口径校验器），seed 齐备。
- **质量：约 9.6/10（+0.1）**——后端 **107 项单测全过**（判定矩阵全格 + 退回白名单不变式 + T-912 口径 + 放行红线）；前端 `lint 0/0`、`vue-tsc --noEmit` 通过、`vite build` 成功；端到端 54 断言（可重复）保留；T-702 报告单测待补。
- **工程化：约 9.0/10（+0.5）**——治理齐备 + 三件套制度化 + 自裁机制 + **技能库 8 个**（本轮新增 `lims-ui-overhaul`）+ 知识库 6 篇（+`2026-09-12-ui-component-library`）。
- **本轮亮点**：保留 mine radio 暗色玻璃氛围，仅在结构空间 / 一致性双维度补齐；不引入新依赖；前端 dist +6KB（gzip 后）。
- **剩余任务全部由 GLM 承担**（S+A 归一）：T-702（S）+ T-801 + 动态路由（A）+ 系统管理 7 页统一下一轮（A）；B 级 T-802 归豆包。**Copilot 为把关方（可选复核），非产能方**。
- **剩余工作量分布**：报告模板合成（CMA/CMA-CATL，含 docx 版式还原）约占**剩余 45%**；查询/上报/动态路由/系统管理页/基础数据页/UI 统一收尾约占 55%。
- **数据与流水已就绪**：`sample_result` + `sample_audit_log` + `sample_info.conclusion/audit_by/sign_by` 全部到位；
  本机样品 1 已跑完整链路至 **S80**（3 条审核流水），**T-702 可直接开工**。
- **可复现资产**：`.agents/skills/` 8 技能 + `docs/knowledge/` 6 篇 + `docs/journal/` 6 篇；S 级任务可直接按 `lims-stage-delivery`、前端 UI 可按 `lims-ui-overhaul` 全链路复现。
