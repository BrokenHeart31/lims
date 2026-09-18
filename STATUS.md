# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 2026-09-18 18:05 合并固化（GLM / 用户指令）

- **`agent/glm` → `develop` → `main` 已全部合并并推送**，三分支同 hash **`9d23e64`**（父 `09d5c9c`）。
- 方式：**快进（fast-forward）**；合并前已核对 `git log agent/glm..develop` 为空（无他人提交会被覆盖）。
- 远端状态：`origin/agent/glm`、`origin/develop`、`origin/main` 均为 `9d23e64`；本地跟踪引用已回填。
- 工作区：`git status --short` = 0 项未提交；后端 **236/236** 单测全绿、前端 `vue-tsc`/`eslint`/`vite build` 全绿。
- 提交内容：
  - `475ecbc` feat(ai,rollback)：本地 AI 助手 + 全流程逐步回退 + 用户实测缺陷修复（219 文件 / +26238 −109）
  - `9d23e64` docs(skill)：sandbox-git-push 补「commit 后 ref 被吞」的取 hash 顺序与实测更正

## 2026-09-18 用户实测缺陷修复（GLM / 流式卡死 + 检索召回 + 审计口径）

> 触发：用户反馈「问 GB 2762 铅的限量，AI 助手一直显示正在生成，关闭再打开才看到回答」。
> 结论：**三个独立缺陷叠加**，已全部修复并实测闭证。详见
> `docs/knowledge/2026-09-18-sse-async-vue-reactivity-ngram-routing.md` 与 `docs/journal/2026-09-18-glm-ai-flow-assistant.md`（本轮追加章节）。

- **本轮改动的文件**（全部为既有新增能力的修复，无新功能）：
  - 后端**修改**：`config/SecurityConfig.java`（放行 `DispatcherType.ASYNC/ERROR`）、
    `security/JwtAuthenticationFilter.java`（ASYNC 派发也认证）、`config/OperationLogInterceptor.java`（preHandle 幂等）、
    `service/ai/GbRetriever.java`（检索路由 + 噪声词剔除）、`service/ai/impl/AiAssistantServiceImpl.java`（点名标准未收录 → 确定性作答）、
    `service/ai/ChunkSplitter.java`（两行式标题合并 / OCR 数字还原 / 伪边界剔除 / 带标题边界必切）、
    `service/ai/parser/TxtParser.java`（标准名抽取）、`mapper/GbDocumentMapper.java`（+已收录标准号查询）、
    `mapper/GbClauseMapper.java` + `resources/mapper/GbClauseMapper.xml`（标题相关性排序）
  - 前端**修改**：`stores/aiAssistant.ts`（**取回响应式代理再改**）、`utils/aiStream.ts`（静默超时 + `onIncomplete`）
  - 数据：**新增** `db/migrations/V10__gb_clause_title_fulltext.sql`；**修改** `db/init/11_ai_tables.sql`（+`ft_gbc_title`）
  - 测试**修改/新增**：`GbRetrieverTest`(+3)、`ChunkSplitterTest`(+2)、`ParsersTest`(+2)、`AiAssistantServiceOfflineTest`(+2)
- **质量门禁（自行复现）**：后端 `mvn -o test` **236 项全绿 / BUILD SUCCESS**；
  前端 `vue-tsc` 0 错误、`eslint` 0 问题、`vite build` 成功。
- **真实链路实测**：经 `localhost:5173` 代理 `curl -N` → **退出码 0**（修复前为 18），refs/token/done 三帧齐全；
  `sys_operation_log` 中 `/ai/chat/stream` 记到 `operator=nj001`、`duration_ms=5570`（修复前 `anonymousUser` / `0`）。
- **GB 索引已按新规则重建**：4660 块、纯数字标题 **0**、`4.121 → 毒死蜱（chlorpyrifos）`；
  5 个抽样项目（毒死蜱/阿维菌素/腐霉利/吡虫啉/多菌灵）的条款**全部排到检索第 1 位**。
- **存量库已应用**：`V10` 已执行（`gb_clause` 现有 `ft_gbc_content` + `ft_gbc_title` 两个 ngram 索引）。
- **项目总进度：99% → 99%**（本轮为缺陷修复与质量提升，未新增业务范围）。

## 2026-09-17 新增能力（GLM / **本地 AI 助手 + 全流程逐步回退**，超出说明书范围）

- **本轮占用**（新增为主，改动既有文件均已列明）：
  - 数据：`db/init/10_rollback_tables.sql`、`db/init/11_ai_tables.sql`、`db/migrations/V8__rollback_and_ai.sql`、
    `db/seed/01_rbac_seed.sql`（权限种子增量）；**存量表语义变更**：`db/init/06_item_tables.sql`、`db/init/07_result_tables.sql`（`deleted` 列 TINYINT→BIGINT）
  - 后端新增：`common/enums/{RollbackEdgePolicy,RollbackGroup,StatusEventType,ArchiveTarget,AiDomain}.java`、
    `entity/{SampleStatusLog,SampleRollback,SampleDataArchive,ReportVoid,GbDocument,GbClause,GbImportJob,AiConversation,AiMessage}.java`、
    `mapper/*.java`（9 个）、`dto/*`（9 个）、`vo/*`（9 个）、
    `service/rollback/{RollbackPlanner,RollbackScope,SampleDataDisposer}.java`、
    `service/{RollbackService,SampleStatusLogService,ReportVoidService}.java` + `impl/*`、
    `service/ai/**`（22 个，含 `parser/*` 5 个）、`controller/{RollbackController,ReportVoidController,AiController,AiKbController}.java`、
    `config/AsyncConfig.java`
  - 后端**修改**：`common/enums/SampleStatusTransition.java`（+第三条白名单 `ROLLBACK`）、
    `common/ResultCode.java`（+4102/4103/4108/4109/4201/4202/4203/4211）、
    `security/SecurityUtils.java`、`entity/Sample.java`、`mapper/{SampleItemMapper,SampleResultMapper}.java`、
    `config/{OperationLogInterceptor,WebConfig}.java`、`resources/application.yml`、
    **6 个既有 ServiceImpl**（`Sample/Item/Assign/Result/Audit/ReportGenerate`）——接入状态流水
  - 前端新增：`components/ai/*`（6 个）、`components/rollback/RollbackTimelineDrawer.vue`、
    `views/rollback/index.vue`、`views/ai/audit.vue`、`stores/aiAssistant.ts`、`api/{ai,rollback}.ts`、`utils/aiStream.ts`、`types/rollback.ts`
  - 前端**修改**：`layouts/MainLayout.vue`（挂载悬浮窗）、`router/{routeRegistry,dynamicRoutes}.ts`（+路由）、
    `views/{report/audit,result/index}.vue`（+携带上下文入口，**未改其数据流与权限判断**）
  - AI 运行时与脚本：`ai/{README.md, config/*, scripts/*}`（`.gitignore` 已排除 `ai/runtime`、`ai/models`）
  - 设计与资产：`docs/design/2026-09-17-{prd,arch}-ai-assistant-and-rollback.md`、
    `docs/knowledge/2026-09-17-{rollback-mechanism,local-ai-and-gb-retrieval}.md`、`docs/journal/2026-09-17-*.md`
- **两项能力**：
  1. **本地 AI 助手**：qwen3-4b 项目内托管（`ai/runtime` + `OLLAMA_MODELS=ai/models`）；JDK17 `HttpClient` 调 Ollama
     （**零新增 Maven 依赖**，唯一新增是 `jsoup` 用于 HTML 解析）；**领域护栏**（业务域由本地规则确定性作答、越界拒答、
     标准域走 GB 检索）；**GB 标准库**用 MySQL 8.0 `FULLTEXT ... WITH PARSER ngram` 秒级检索（**零新增依赖**）；
     回答为**结构化对象 + 引用卡片**（不引 Markdown 库）；可拖拽悬浮窗（自研 Pointer Events，无拖拽库）。
  2. **全流程逐级回退**：**第三条独立白名单 `ROLLBACK`**（6 条边，**S80/S90 无回退边**）+ 统一状态流水
     `sample_status_log`（7 种事件类型） + 回退记录 + 失效留档 + 报告作废/召回标记动作。
- **⚠️ 本轮最高风险改动**：`sample_item`/`sample_result` 的 `deleted` 语义由「0/1 两态」升级为「0=有效 / 非 0=行自身 id」，
  以解决「逻辑删除二次失效撞唯一键」。**MyBatis-Plus `@TableLogic` 只支持固定字面量、不支持表达式**（实测），
  故这两张表的失效必须走 `SampleDataDisposer` 显式 `set(deleted, id)`，**禁用 `baseMapper.delete(...)`**。
  全局 `logic-delete-value=1` 保留（其余表仍两态，查询恒为 `deleted = 0`，不受列类型影响）。
- **验收**（真实命令/产物，非自述）：
  - 后端 `mvn -o test`：**212 项全绿**（既有 151 + 本轮新增 61：回退 30 + AI 23 + QA 补齐 8，0 失败 0 错误）
    > 首轮交付时为 204；**QA 第 2 轮**（授权补齐 P1/P2）新增 `SampleDataDisposerTest`(8) 把**不变式④「无物理删除路径」**
    > 真正固化成断言，并加固乐观 UPDATE 的 `WHERE status=旧值` 捕获，最终 **212/212 BUILD SUCCESS**。
  - 前端 `vue-tsc --noEmit` / `npm run lint` / `npm run build` 三门禁全绿
  - 回退四条不变式（乐观 UPDATE 冲突→4108 / 失效处置在状态 UPDATE 之后且同事务 / 一次回退仅新增 1 条 `event_type=4` /
    无物理删除路径）已用 `InOrder` + `verify(times(1))` **测试固化**，非注释承诺
  - `AiDomainIsolationTest` 用**反射穷举依赖**断言 AI 不持有判定链 Mapper（T3 红线可执行化）
  - 独立复核：三条白名单真正独立；`ROLLBACK` 恰好 6 条边且无 S80/S90 出边；AI 包对结论字段 grep 零命中
- **⚠️ 必须由用户执行才能闭环的事项（不得视为已验证）**：
  1. **Ollama 运行时与 qwen3:4b 权重未下载**——实施环境沙箱代理对大文件返回 502，实测 **7 条镜像路径全部失败**。
     交付的是 `ai/scripts/deploy-ollama.ps1`（多镜像回退 + 手动兜底），**需用户在本机执行**。
     故本轮 AI 能力的验收边界是「**Ollama 不可用时系统行为正确**」（降级 + fail-loud + 业务零影响），
     **真实推理链路未端到端跑通**。
  2. GB 标准库**无真实数据**（用户尚未提供标准文件），检索链路以构造数据验证。
  3. 悬浮窗拖拽/回退 UI 未做真实浏览器人工体验终验（需用户点一次）。
- **进度**：**按说明书口径维持 99%**（两项能力超出说明书范围，见 DECISIONS 2026-09-17 口径裁决）。
  新能力自身完成度：**回退机制 后端+测试+UI 完成、真实浏览器端到端未跑；AI 助手 代码链路完成、真实模型推理未验证**。
- **未提交**：本轮改动**未执行任何 git 提交**（本机 git 沙箱有已知问题，统一由 GLM 后续处理）。
  当前工作区含**本会话之前遗留的未提交改动**（如 `directives/permission.ts` 09-14、`.agents/skills/excel-import/SKILL.md` 09-15），
  提交前必须 `git status --short` 逐项核对（AGENTS 第 12 章红线）。

## 2026-09-14 16:30 下载改造（GLM / **全部下载改为「自选保存位置」**）

- **本轮占用**：`frontend/src/utils/download.ts`（新增 `saveBlobAs` / `notifySaveOutcome` / 类型常量）、
  `views/{result/index.vue, result/my-tasks.vue, export/province.vue, sample/index.vue,
  base/tester-method.vue, base/product-lib.vue}`、`README.md` + 治理文件。**后端无变更**。
- **改动**：6 处下载（4 导出 + 2 模板）统一改走 `saveBlobAs()` → 弹系统**「另存为」对话框**让用户选位置与文件名；
  不支持的浏览器或非安全上下文自动退回默认下载目录并提示。
- **关键约束**：`showSaveFilePicker()` 依赖**瞬时用户激活态**，`await` 之后即失效抛 `SecurityError`。
  故 `saveBlobAs` 的参数是**数据工厂函数**而非 Blob —— 从 API 形状上强制「先弹框、后取数据」。
  **后续维护不得改成「先请求再弹框」**。
- **代价（已落档）**：必须先弹框 ⇒ 拿不到服务端 `Content-Disposition` 文件名，改用**带时间戳的建议名**（用户可改名）。
- **验收**：桩替换原生对话框后真实点击 —— 检验任务 5111 字节 / 省平台 4338 字节 /
  采样单模板 6106 字节（与磁盘一致）写入成功；取消分支提示正确且**未发请求**；
  `eslint` 0 / `vue-tsc` 0 / `vite build` ✅ / console 0 错误。
- ⚠️ **需人工确认**：无头浏览器无法弹真实系统对话框（对照实验证实 headless 不校验激活态），
  **对话框外观与真实落盘需用户点一次**。
- 进度维持 **99%**。

## 2026-09-14 16:10 修复轮（GLM / **用户实测反馈的 4 类问题全部闭环**）

- **本轮占用**：`frontend/src/{views/dashboard/index.vue, views/sample/index.vue, views/error/403.vue,
  router/index.ts, layouts/MainLayout.vue}`、`db/seed/03_demo_flow_seed.sql`（新增）、`README.md` + 治理文件。
  **后端无 Java 变更**（本轮纯前端 + 数据）。
- **3 个真代码缺陷**：
  1. 工作台无条件调 `/stat/overview`（需 `stat:view`）→ 无权限账号看到红色「业务概览加载失败」；
     改为**权限感知**（条件请求 + 分区中性空态 + 按钮 `v-if`），错误告警只留给「有权限但真失败」。
  2. Hero 硬编码跳转 → 无权限点「查看质量分析」命中 catch-all 变 404；
     路由守卫新增「已登记但无权限 → 403」判定，403 页文案改为可操作。
  3. 模板下载 `target=_blank` + dev server 对 `.xlsx` 返回**空 Content-Type** → 空页面无响应；
     改用 HTML `download` 属性（忽略 Content-Type），实测真实落盘 6106 bytes。
- **2 个数据缺口（非缺陷）**：库里无 S40/S50 样品（检验员无从录入）、无 S80 样品（只给重打印）。
  新增 `db/seed/03_demo_flow_seed.sql`（**幂等**）：5 个演示样品覆盖 S10/S30/S40/S60/S70，
  另补「河蟹」项目标准库 5 项使既有样品 2 可套库。**六个账号现在各自都有可走的第一步。**
- **自查发现并修复**：顶部「待办提醒」挂载即拉 6 个接口 → 无权限账号刷出 15 条控制台 403；
  改为**发请求前**按 `permission` 过滤（权限来自 `/me`，前端已知，不该用 403 去发现）。
- **验收**：后端 **113/113**；前端 lint 0 / vue-tsc 0 / build ✅；
  真实浏览器：R3 工作台 **0 红错**、点质量分析 → **403 页**、**实际录入并保存 → 进度 0/4→75%**、
  模板**真实落盘**、报告审核放行红线生效、签发→生成→打印全通、**console 0 错误 / 0 失败请求**。
- 进度维持 **99%**（修复轮，无新功能域；可自测性显著提升）。

## 2026-09-14 15:20 收尾（GLM / **项目功能完工**：T-918 操作日志 + 通知去假数据 + Git 对象库恢复）

- **本轮占用**：`backend/.../config/{OperationLogInterceptor,WebConfig}.java`、`entity/SysOperationLog.java`、
  `mapper/SysOperationLogMapper.java`、`service/{SysOperationLogService,impl/SysOperationLogServiceImpl}.java`、
  `dto/OperationLogQueryDTO.java`、`vo/SysOperationLogVO.java`、`controller/SysLogController.java`、
  `db/init/09_operation_log.sql`、`db/migrations/V7__add_operation_log.sql`、
  `frontend/src/{layouts/MainLayout.vue,api/system.ts,api/auth.ts,views/dashboard/index.vue}`、
  `docs/api/api-spec.md`（+第 15 章）、`db/seed/01_rbac_seed.sql`（注释）与全部治理文件。
- **两件事**：
  1. **T-918 操作日志落地**（消除「空壳」）：`sys_operation_log` + `OperationLogInterceptor`（零 AOP 依赖，
     经论证用 HandlerInterceptor 等价达成）+ `GET /api/sys/log/page` + 前端真实分页表格。
     **分级数据范围**：人人可查自己，「跨用户查看」需 `log:view`，服务端强制不可绕过。
  2. **顶部铃铛去假数据**：原 4 条写死的假通知改写为 6 个业务域真实待办汇总（零值不展示、点击直达）。
- **附带**：`AppSkeleton` 接入工作台 KPI 加载态；删除零引用死代码 `ProgressBar.vue`。
- **验收**：后端 **113/113**（新增 6）；前端 lint 0 / vue-tsc 0 / vite build ✅；
  **Edge + CDP 真实浏览器全量遍历 20 页 → 0 console error / 0 网络失败**；
  **三档分辨率（1440×900 / 1920×1080 / 1366×768）`scrollWidth == clientWidth` 且无越界元素**；
  操作日志端到端 6 断言全过（含 403 失败留痕、伪造 `operator` 参数无效）。
- **🔧 Git 对象库已修复**：`D:\lims\.git` 曾缺 **91 个对象**（6 commit + 多 tree/blob），
  `git fetch` **无法修复**（本地 ref 污染协商，远端拒绝补发）。改用**镜像克隆取 pack**：
  `git clone --mirror` → 拷 `objects/pack/pack-<new>.*` → 删过期 `multi-pack-index` →
  `git fsck --full` = **0 missing / 0 broken**。现 `git status` / `git log` / `git branch` 全部正常。
- 进度 **96% → 99%**。**业务功能零缺口**；剩余仅为用户人工体验终验。

## 2026-09-13 23:30 接手（豆包 / 顶部栏补全 + P1/P4/P5 修复）

- 本轮占用：`backend/.../AuthController.java`、`AuthService.java`、`AuthServiceImpl.java`、`dto/ChangePasswordDTO.java`、`frontend/src/layouts/MainLayout.vue`、`components/common/PageHeader.vue`、`views/query/{testing,history,library}.vue`、`api/auth.ts`、`public/favicon.svg`、`index.html`、日记与治理文件。
- 新增后端接口 `POST /api/auth/change-password`（自服务改密）；前端补全个人资料/改密/操作日志/帮助四个对话框；修面包屑重复 + PageHeader 竖排 + 查询按钮对齐 + favicon。
- 验证：后端 107/107 单测过、vue-tsc 0 错、lint 0 错、浏览器实测 console 0 错误。
- 进度 **95% → 96%**。下一步 GLM：Git 恢复推送 → 操作日志后端 → T-917-6~10 三档分辨率验收。

## 2026-09-13 22:55 接手（豆包 / 测试巡检，代码未动）

- 本轮占用：`docs/journal/2026-09-13-doubao-test-audit.md`、`docs/journal/README.md`、`HANDOFF.md`、本文件。**未改 backend/ frontend/ db/ 任何代码。**
- 实测结论：后端 `mvn -o test` **107/107 BUILD SUCCESS**；前端 `lint` 0 错误、`vue-tsc` 0 错误、`vite build` 37.56s 成功；nj001 登录 + 全页面浏览器实测通过。
- 发现 5 个前端 UI 问题（P1~P5），详见 HANDOFF 顶部与日记：P1 PageHeader 标题 flex-shrink 缺失竖排（影响约 8 页）、P2 双面包屑重复、P3 表格列宽截断、P4 查询按钮对齐不一致、P5 favicon 404。
- Git 仍损坏：`D:\lims\.git` 缺 tree `3c168259...`；临时副本 `Temp\lims_work_ui` 在 main 分支暂存 38 文件待 GLM 处理。
- 进度维持 **95%**（本轮只测试找问题）。下一步 GLM：修 P1~P5（纯 CSS/布局，约 1 人时）→ Git 恢复推送 → T-917-6~10 三档分辨率与 20 项 Checklist。

## 2026-09-13 20:54 接手（GLM / T-917 收口）

- 本轮占用：`frontend/src/views/{assign,item,sample,task}/index.vue`、`frontend/src/views/report/{generate,print}.vue`、`frontend/src/views/{query/analysis,dashboard/index}.vue`；按需复核 `components/common/{DataFilter,DataTable,LimsChart}.vue`、`styles/report-print.css`；治理文件、根 `.gitignore`、工作日记。
- 无其他 Agent 占用。继续 T-917-5，不重做已完成业务、动态路由；不改 API/DB/状态机/认证/权限/Pinia 数据结构。
- Git 只读实测：主仓库在 `agent/glm`，HEAD=`b206f780a9fa2dc27e3750c0be62cabd70948166`，status 因 tree `3c168259069967b619abcd0e561105bc51dfad37` 缺失失败。临时副本在 **main**，38 个暂存文件、无删除项，`.shots/` 未跟踪；不得在该 main 上直接提交。
- 本轮暂不 checkout/pull/reset/stash/commit/push；验收完成并解决对象完整性与分支问题后再推进 Git。
- 进度暂保持约 95%；代码门禁、真实浏览器验收和远程同步独立记录。

## 2026-09-13 20:34 状态更新（GLM / 未完成任务交接）

- **本轮目标**：不继续修改业务代码，先将未完成任务、未验证项和 Git 阻塞集中写入 `HANDOFF.md`、`TODO.md`、`docs/journal/`，交接给下一位 Agent。
- **T-917 当前状态**：T-917-1~4 已完成；T-917-5 进行中，已完成审核、结果录入、系统、基础数据、查询与导出批次；剩余页面为 `assign/index.vue`、`item/index.vue`、`sample/index.vue`、`task/index.vue`、`report/generate.vue`、`query/analysis.vue`，另需复核 Dashboard 和独立打印页。
- **T-917-6~10**：仍待办，包含交互统一、ECharts/Dashboard 最终复核、全局视觉统一、三档分辨率验收和 20 项 Checklist。浏览器自动化不可用，尚未完成真实浏览器视觉验收。
- **质量门禁**：已记录 lint 与 vue-tsc 通过；20:27 查询/导出批次的统一 build 需要下一位 Agent 重新执行并留证。后端本轮无业务代码变更，交付前仍应复核 `mvn test`。
- **Git 阻塞**：`D:\lims\.git` broken tree，禁止直接提交；临时副本 `C:\Users\Chen\AppData\Local\Temp\lims_work_ui` 已暂存 38 个文件、无暂存删除项，但 `.shots/` 仍未跟踪。提交前必须再次逐项检查暂存区，随后按 `agent/glm → develop → main` 推进并用 `git ls-remote` 校验。
- **本轮占用文件**：`STATUS.md`、`TODO.md`、`HANDOFF.md`、`docs/journal/README.md`、`docs/journal/2026-09-13-glm-handoff-unfinished.md`、`.workbuddy-ai/memory/2026-09-13.md`。
- **交接索引**：详细清单见 `docs/journal/2026-09-13-glm-handoff-unfinished.md`，下一位 Agent 应以该文件的「明确未完成任务」和「建议顺序」为准。

## 2026-09-13 20:27 状态更新（GLM / T-917-5 查询与导出批次）

- **追加完成**：项目库查询、在检查询、历史查询、检验员任务查询、省平台导出筛选区接入 `DataFilter`；保留既有查询参数、分页、导出与业务权限。
- **验证**：lint、vue-tsc 均通过；此前系统与基础数据批次的 Vite build 通过，本批次待完成统一 build。
- **当前主线**：T-917-5 继续进行，已覆盖审核、结果录入、系统、基础数据、查询与导出页面；仍需完成剩余页面视觉细化和最终验收。

## 2026-09-13 20:17 状态更新（GLM / T-917-5 系统与基础数据批次）

- **追加完成**：用户、角色、菜单、方法资质、项目标准库筛选区继续接入 `DataFilter`；方法资质列表接入 `DataTable` 统一 loading/empty 外壳；部门页保留树形表格专用布局。
- **验证**：lint、vue-tsc、Vite build 均通过；本次构建输出 `frontend/dist-step5-system-base`。

## 2026-09-13 20:10 状态更新（GLM / T-917-4 + T-917-5 首批）

- **当前主线**：T-917 UI/UX 全面重构；T-917-1~4 已完成，T-917-5 进行中，T-917-6~10 待办。
- **本轮占用文件**：
  - `frontend/src/views/report/audit.vue`：审核 KPI、`DataFilter`、统一高风险确认；
  - `frontend/src/views/result/index.vue`：结果异常整行高亮、`DataFilter`；
  - `frontend/src/views/{system,user,role,menu,dept}.vue`、`frontend/src/views/base/{tester-method,product-lib}.vue`：删除确认统一 `askConfirm()`；
  - `TODO.md`、`STATUS.md`、`docs/journal/README.md`、`docs/journal/2026-09-13-glm-ui-step4-step5.md`、`docs/knowledge/2026-09-13-ui-component-system.md`。
- **验证**：前端 `npm run lint` 通过（0 errors / 0 warnings）；`vue-tsc --noEmit` 通过；Vite build 通过，输出目录 `frontend/dist-step5-filter`。
- **进度评估**：项目总进度约 **94% → 95%**。增量来自公共组件治理同步、审核/结果录入首批迁移和系统/基础数据高风险确认统一。
- **Git 阻塞**：当前 `D:\lims\.git` 仍有 broken tree（`3c168259...`），`git status/log` 无法可靠读取；本轮不在损坏对象库上提交，待后续使用临时副本恢复并逐项核对暂存区。
- **下一步**：继续迁移系统管理/基础数据/查询页的 `DataFilter`、`DataTable`、加载/空态与响应式布局；完成后再做三档分辨率与无横向溢出验收。

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
- ✅ **前端动态路由已落地**（2026-09-13 GLM，选型=**路径注册表 + 中间件转换**，DECISIONS 已落档）：
  - `router/routeRegistry.ts` **21 条显式登记**（path → 组件 + 权限 + title），**刻意不用 `import.meta.glob`**（`/sample` vs `/assign/index` 约定互相矛盾，glob 无法收敛）；`PATH_ALIAS` 兼容层做旧路径规范化。
  - `router/dynamicRoutes.ts` `buildNavigation()` **一次产出「路由 + 侧栏菜单树」二者同源**，杜绝菜单与路由漂移。
  - 菜单树只提供结构（title/path/icon/层级/排序），**组件路径不落库**（构建期概念 / Vite 静态分析 / 安全三条理由）。
  - **踩坑（高价值）**：vue-router 4 catch-all 按注册顺序匹配且 `addRoute` 恒追加 → catch-all 若先注册，动态路由永远匹配不到，表现为**「菜单点击正常、F5 刷新变 404」**；故 `registerNotFound()` 必须「移除后重加」。
  - 验证：`vue-tsc` 0 错误、`vite build` 成功、**离线路由断言 31/0**、`/me` 实测 R100 见 11 组 / R3 见 2 组、SPA 深链接 6 条 HTTP 200。
  - 无页面菜单（`/base/basis`、`/base/customer`、`/sys/log`）按用户决策设 `visible=0` 隐藏（保留数据，DB 结构零改动）。
- ⚠️ git 沙箱：`.git/refs/heads/agent/*` 引用会被 git.exe 静默丢弃（每次 git 操作后必须 shell 回填）；push 需 `git -c http.sslVerify=false`。
- ℹ️ 本机 MySQL 实际密码 123456（非 AGENTS 约定 11111111），在 gitignore 的 application-dev.yml。

## 进度评估（距整个项目圆满完成）
**总进度：约 99%**（按 AGENTS 2.5 节固定口径：业务主干 55% + 前端 15% + 数据 10% + 质量 10% + 工程化 10%）
> 2026-09-14 收尾结论：**业务功能零缺口**。说明书 13 项功能全部落地并实测；
> 未完成项从「功能」降级为「用户人工体验终验」与远端分支同步（后者本轮已完成）。
- **业务主干：9/9 阶段 + 说明书 13 项功能全部落地（55%）** — 七阶段主线 + 三个延伸域
  （基础数据维护 / 统计看板 / 操作日志）全部实现并端到端实测。
  - ✅ 阶段一（T-105 方法资质 / T-106 项目标准库 / T-107 系统管理 4 页） / 二 / 三 / 四 /
    五 / 六（含 T-603） / 七（T-701 + T-702） / 八 / 九（T-802 + T-803）
  - ✅ **T-918 操作日志**（2026-09-14）——说明书未要求，但它是「用户菜单里的空壳」，
    收尾时补齐为完整审计能力
  - ⬜ 无业务缺口
- **前端：17 页全部完成（15%）** — 公共组件 100% 迁移；加载/空态/错误态齐备；
  三档分辨率无溢出；真实浏览器 20 页 0 console error。
- **数据：10/10**——`db/init/01→09` 建表齐备，`V1~V7` 迁移齐备（V3/V6/V7 可重跑），seed 齐备。
- **质量：约 9.8/10**——后端 **113 项单测全过**；前端 lint 0 / vue-tsc 0 / build ✅；
  真实浏览器全量遍历 + 三档分辨率实测；T-918 端到端 6 断言（含越权与失败留痕）。
- **工程化：约 9.8/10**——治理齐备 + 三件套制度化 + 自裁机制 + 技能库 9 个 + 知识库 15 篇 + 日记 17 篇。
- **本轮亮点**：
  ① **图表库选型** ECharts 5.5.1 按需引入，路由级分包实测 `analysis-*.js` 542.83 kB / gzip 183.12 kB，
     **主包零增长**（关键可接受前提）；选型四问 + 反例排除完整落档 DECISIONS。
  ② **修复一处自引入缺陷**：`SysUserVO`/`SysRoleVO` 漏 `@JsonFormat`，导致日期返回 ISO 串
     （`2026-09-13T14:58:39`）与项目其余 16 个 VO 字段（`yyyy-MM-dd HH:mm:ss`）不一致——
     根因是 `spring.jackson.date-format` 对 JSR-310 无效。已修复并加注释防复发。
  ③ **补齐权限种子缺口**：`base:lib:add/edit/remove` 在代码中使用但未定义，
     被 R100 硬编码权限掩盖，普通角色一测即 403。
  ④ **数据纪律**：测试期间对 `product_lib_item` 的覆盖式替换已完整还原（原 4 条明细恢复，
     测试行物理删除），实例数据零残留。
  ⑤ **动态路由（本轮）**：选型「路径注册表 + 中间件转换」，**未改任何 DB 表结构、未改任何 API 契约**，
     以 21 条显式登记 + 别名单向映射收敛全部历史路径；菜单与路由**同源产出**杜绝漂移。
- **剩余任务**：T-917-5 剩余 6 个页面的逐页迁移与 Dashboard/打印页专项复核；T-917-6~10 的交互统一、ECharts/Dashboard 最终验收、三档分辨率和 20 项 Checklist；以及临时 Git 副本的暂存区核对、提交和 `agent/glm → develop → main` 分支同步。
- **可复现资产**：`.agents/skills/` 9 技能 + `docs/knowledge/` 9 篇 + `docs/journal/` 10 篇；
  本轮新增的「ECharts 集成」「RBAC 维护界面防护」「统计接口模式」「**Vue3 动态路由注册表**」四篇知识可直接支撑同类项目复现。

## 本轮新增可复用资产（2026-09-13 T-105/106/107/603/803）

| 资产 | 位置 | 用途 |
|---|---|---|
| ECharts 集成知识 | `docs/knowledge/2026-09-13-echarts-integration.md` | 按需引入最小集 + 路由级体积验证 + CSS 变量取色 + ResizeObserver |
| RBAC 维护防护知识 | `docs/knowledge/2026-09-13-rbac-maintenance-guardrails.md` | 8 类失效模式与防护（自锁/提权/孤儿引用/成环/形态错配） |
| 统计接口模式知识 | `docs/knowledge/2026-09-13-statistics-api-patterns.md` | 单 SQL 多列聚合 + 补零月 + `null` vs `0` 第三态语义 |
| 图表封装组件 | `frontend/src/components/common/LimsChart.vue` | 可复用图表容器（空态优先 + 主题跟随） |
| 图表配置工厂 | `frontend/src/utils/chartOptions.ts` | 取数/布局/图形三层分离 |
| 工作日记 | `docs/journal/2026-09-13-glm-t105-107-603-803.md` | 本轮全部判断与踩坑 |
| **动态路由知识** | `docs/knowledge/2026-09-13-dynamic-routing-registry.md` | 三层分离 + 为何不用 glob + catch-all 陷阱 + 落地五步 |
| **动态路由技能** | `.agents/skills/vue3-dynamic-routing/SKILL.md` | 可操作流程（含完整代码与验证 Checklist） |
| **动态路由日记** | `docs/journal/2026-09-13-glm-dynamic-routing.md` | 选型判断 / 4 条踩坑 / 裁决点 |
| 路由注册表 | `frontend/src/router/routeRegistry.ts` | 21 条 path→组件显式登记 + 路径别名兼容层 |
| 导航构建器 | `frontend/src/router/dynamicRoutes.ts` | 菜单树 → 路由 + 侧栏菜单（同源产出） |

