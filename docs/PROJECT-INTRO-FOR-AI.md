# 食品质量检验测试中心 LIMS —— 项目完整全景介绍

> 基于对 `D:\lims` 仓库全量阅读整理（AGENTS.md / STATUS / TODO / HANDOFF / DECISIONS / README / api-spec / 设计文档 / 知识库 / 日记 / 建表与迁移脚本 / 前后端全部源码清单 / 核心引擎实现），可直接转述给其他 AI 用于接手与改进。

## 一、项目一句话定位

一个**前后端分离的实验室信息管理系统（LIMS）**，服务对象是食品质量检验测试中心，覆盖「监抽任务 → 样品登记 → 项目分解 → 任务安排 → 数据录入（自动判定）→ 报告审核签发 → 报告生成打印 → 查询/省平台上报」的全流程检验业务，符合 CMA/CATL 资质审计要求（ALCOA+ 可追溯原则），并在约 99% 完成度基础上加装了**本地 AI 助手**与**全流程逐步回退**两项新增能力。

## 二、技术栈（硬约束，不可偏离）

| 层 | 技术 | 关键说明 |
|---|---|---|
| 前端 | Vue 3 + TypeScript(strict) + Vite + Element Plus + Pinia + Vue Router 4 | 禁 any；业务状态入 Pinia；按钮级权限 `v-permission`；`npm run build && npx vue-tsc --noEmit && npm run lint` 为门禁 |
| 后端 | Spring Boot **3.3.2** + Java 17 + Maven + MyBatis-Plus **3.5.7** | 不追 Boot 4.x；离线 Maven 本地仓（`.m2`），**新增依赖极难**——这是贯穿全项目的关键约束 |
| 数据库 | MySQL 8.0.45（InnoDB / utf8mb4），开发账号 root/11111111，库名 `lims` | `ngram` 全文解析器已启用（token_size=2）；`SAMPLE`/`generated` 等是 MySQL 保留字，已避坑 |
| 安全 | Spring Security + JWT（HS256，access 2h / refresh 7d），BCrypt | context-path=`/api`，前端 vite 代理 `/api→8080` 无 rewrite |
| 关键中间件 | EasyExcel 3.3.4（导入导出，禁 POI 裸 API）；ECharts 5.5.1（按需引入）；jsoup 1.18.3（**唯一**新增的 AI 相关依赖）；JDK17 HttpClient + SseEmitter（调本地 LLM，零 WebFlux） | 离线仓**没有** AOP/Lucene/PDFBox/Tika/spring-ai 可用，全部用零依赖方案绕开 |

## 三、仓库结构（D:\lims）

```
frontend/   Vue3 工程（api/ stores/ router/ views/ components/ styles/ utils/ types/ directives/）
backend/    Spring Boot 工程（controller/ service/ mapper/ entity/ dto/ vo/ config/ security/ common/）
db/         init/（12 个建表脚本） migrations/（V1~V10 增量迁移） seed/（RBAC/演示数据种子）
docs/       api/api-spec.md（接口契约，唯一权威） knowledge/（选型结论） journal/（工作日记）
            design/（PRD + 架构文档） class-diagram.mermaid  sequence-diagram.mermaid
ai/         Ollama 便携运行时 + 模型目录 + standards/（GB 标准库）+ scripts/（部署/OCR 脚本）
prompts/    三个 Agent 的提示词（glm/copilot/doubao）
.agents/skills/  9 个可复用技能（lims-stage-delivery、sandbox-git-push、judge-engine、rbac-backend 等）
AGENTS.md  STATUS.md  TODO.md  HANDOFF.md  DECISIONS.md  README.md  overview.md
```

## 四、协作模式（多 Agent 治理体系，接手时必须遵守）

- **三 Agent 分工**：GLM（S+A 级：架构/后端核心/判定引擎/前端主责）、Copilot（可选复核：契约终审/规则复核/diff 审查，默认不阻塞主线）、豆包（B 级：文档/数据/状态维护/杂务）。
- **分支策略**：`agent/glm → develop → main` 单向合并；**任何 Agent 禁直接 commit/push 到 main/develop**；三分支每轮 fast-forward 固化（最新 hash 见 `STATUS.md` 最新一节）。
- **开工六步**：checkout 自己分支 → 前置检索（`.agents/skills/` → `docs/knowledge/` → `docs/journal/` → 上网）→ 读 STATUS/TODO/HANDOFF/DECISIONS → 声明占用文件 → 领 TODO 任务 → 开发+日记+进度更新+提交+更新 HANDOFF。
- **工作纪律三件套**：每次会话必写 `docs/journal/` 日记（含心得/踩坑/可复用结论）、必更新 STATUS 进度百分比、动手前必检索。
- **自裁机制**：GLM 对口径歧义自行裁决（可复现证据 + 落档 DECISIONS + 反例说明 + fail-loud 优先），Copilot 裁决仅剩 1 次配额。
- **红线**：判定规则只能后端实现且检验员不可手改；前端权限只做显隐；提交前 `git status --short` 核对（曾发生 118 文件误删事故）。

## 五、核心业务模型

### 1. 业务七阶段（任务/接口/权限标识全部按此对齐，禁止跨阶段跳做）
基础数据准备 → 监抽任务管理 → 样品登记（Excel 导入）→ 检验项目分解（自动套库）→ 检验任务安排 → 检验数据录入（自动判定）→ 报告审核签发 → 报告生成打印 → 查询/省平台上报。

### 2. 样品状态机（全系统唯一流转标准，S10→S90，TINYINT+枚举）
| 状态 | 编码 | 触发 | 下一允许操作 |
|---|---|---|---|
| 已登记 | S10 | 采样单导入成功 | 登记确认 |
| 登记确认 | S20 | 登记员确认 | 项目分解 |
| 已分解 | S30 | 分解确认保存 | 任务安排 |
| 已安排 | S40 | 安排确认保存 | 检验数据录入 |
| 检验中 | S50 | 检验员首次录入 | 继续录入（自环）/录齐 |
| 检验完成 | S60 | 全部项目录齐 | 提交审核 |
| 已审核 | S70 | 审核通过 | 签发 |
| 已签发 | S80 | 签发 | 报告生成 |
| 已出报告 | S90 | 报告生成完成 | 上报导出/归档（终态） |
| （退回） | — | 审核退回 | S50 并通知检验员 |

**状态流转三张独立白名单**（`SampleStatusTransition`，EnumMap，O(1)）：
- `VALID` 正向推进（S10→S20→…→S90）；
- `RETURN` 审核退回（唯一边 S60→S50，带原因+留痕+通知）；
- `ROLLBACK` 纠错回退（2026-09-17 新增，6 条逐级边：S20→S10、S30→S20、S40→S30、S50→S40、S60→S50、S70→S60；**S80/S90 无回退边**——已签发/已上报属资质行为，白名单里物理不存在，走 `report_void` 作废/召回标记而非改状态）。
- 流转必须「白名单断言 + 乐观条件 UPDATE（WHERE id=? AND status=旧值）」双保险；Service 层第一行必须调断言，DAO 层禁直接 set 状态。

### 3. 结果自动判定引擎（`JudgeEngine`，纯函数、无状态、无 IO）
- **形态**：判定语义是代码里的**闭集白名单矩阵**，`std_value` 只被解析、从不被执行——**不引入任何规则引擎**（Drools/Easy Rules/LiteFlow/Aviator 全否决）。
- **三种判定类型**：jt1 限量比较（`≤X`型）、jt2 不得检出/不得使用、jt3 文本/感官（人工）。
- **判定矩阵**（Copilot 裁决 D1~D4 定稿）：未检出→合格；数值 < 最低检出限→视同未检出→合格；jt2 数值≥检出限→检出→不合格、缺检出限→**待判定（禁默判合格）**；标准值`--`（无依据）→未检出/低于检出限合格，否则待判定；白名单外任意组合→待判定+WARN。
- **浮点安全**：一切比较走 `BigDecimal.compareTo`；标准值关键词用「不得检出」、检验值关键词用「未检出」（两个常量必须区分——曾因此 6 个单测全红）。
- **快照下沉**：判定只读 `sample_item`（T-401 时从 `product_lib_item` 复制的 8 个判定字段快照），**禁止回溯标准库**——国标更新不得追溯篡改已出报告。
- **红线**：AI 绝不写入任何业务结论字段（反射测试穷举 AI 服务依赖断言无判定链 Mapper）；检验员不可手改单项结论；「未录入」（操作缺漏，阻断提交）与「待判定」（数据缺口，不阻断、审核页显式确认）严格区分。

### 4. 任务安排自动分配
样品编号含 `NA`→njna000（农）、`XA`→njxa000（畜）、`SA`→njsa000（水）；其余按「检验方法—检验员资质」匹配，人工改派只能选有资质者。三级规则顺序固定：分类→资质→兜底（assignType 0/1/2/3）。

## 六、数据库设计

- **新表规范（6.1，所有新建表强制）**：InnoDB/utf8mb4、全小写下划线、`id BIGINT AUTO_INCREMENT`、审计四字段（created_by/created_at/updated_by/updated_at）、`deleted` 逻辑删除、状态用 TINYINT+字典、高频字段建索引。
- **新旧库并存**：`lims.sql` 是旧系统导出参考数据（int 主键/驼峰列/无审计），**禁止在新代码复用**；旧数据由 `db/migrations/V1__import_legacy_data.sql` 清洗迁移（含字段映射+去重+前后条数校验），迁移完成后旧表废弃。
- **建表脚本**：`db/init/01` 基础表（basis/customer/dept）、`02` RBAC（sys_user/role/menu/user_role/role_menu）、`03` 监抽任务、`04` 检验员-方法资质、`05` 样品域（sample_info/sample_import_batch）、`06` 项目分解（product_lib/product_lib_item/sample_item）、`07` 结果（sample_result）、`08` 审核流水（sample_audit_log）、`09` 操作日志（sys_operation_log）、`10` 回退三表（sample_status_log/sample_rollback/sample_data_archive + report_void）、`11` AI 表（gb_document/gb_clause/gb_import_job/ai_conversation/ai_message）、`12` AI 提示留痕（ai_hint_log）。
- **迁移脚本 V1~V10**：V1 旧数据导入、V2 补 product_name/category、V3 判定类型 fail-loud 口径校验器、V4 整体结论列、V5 审核/签发字段、V6 报告生成列、V7 操作日志、V8 回退+AI 表、V9 AI 流程助手增量、V10 gb_clause 标题 ngram 全文索引。
- **⚠️ 最高风险改动（T-920）**：`sample_item`/`sample_result` 的 `deleted` 从 0/1 升级为「0=有效 / 非 0=该行自身 id」，解决「覆盖式重建+回退二次失效撞唯一键」；MP `@TableLogic` 只支持固定字面量，故失效一律走 `SampleDataDisposer` 显式 `set(deleted, id)`，禁用 `baseMapper.delete`；全仓禁硬编码 `deleted=1` 判定。
- **索引是派生数据不是业务留档**：GB 索引表允许物理重建（换版标准时 DELETE 旧 clause 重建），`ai_message.citations_json` 承担审计证据。

## 七、后端架构要点

- **分层职责**（Controller 不写业务/Service 不暴露 Mapper/Entity 无业务方法/DTO 带 JSR-303/VO 脱敏），约 30 个 Controller、30 个 Service、40 个 Entity、40+ DTO、60+ VO。
- **统一响应**：`{code:0,msg:success,data:{}}`；安全层 401/403 返回 HTTP 状态码 + 统一响应体（GlobalExceptionHandler 对 @PreAuthorize 拒绝已补 `@ResponseStatus(403)`）；业务异常 HTTP 200 + body.code。
- **JWT 鉴权**：过滤器每请求按 username 从 DB 装配 LoginUser（权限变更即时生效）；R100 代码层 isAdmin 短路全权限。
- **SSE 流式已修复**（T-923 关键）：SecurityConfig 放行 `DispatcherType.ASYNC/ERROR` + `JwtAuthenticationFilter.shouldNotFilterAsyncDispatch()→false` + 审计拦截器 preHandle 幂等——否则 `SseEmitter.complete()` 触发 ASYNC 派发致鉴权失败、连接被 RST（`curl -N` 退出码 18）、前端永久「正在生成」。
- **操作日志**：零 AOP 依赖用 HandlerInterceptor 实现；只记写请求（POST/PUT/DELETE）、绝不记请求体、失败也留痕、分级数据范围（人人查自己，`log:view` 才跨用户）、模块识别用有序前缀表（6 项单测固化顺序语义）。
- **数据权限落点在服务层闭环**（如 T-603 `resolveTesterScope()` 无请求绑定，伪造无效）。
- **报告生成**：实时聚合不落快照（S80 后无写路径，数据天然冻结）；电子签名「占位+可配置」（`sys_user.signature_url` 为空渲染虚线占位框，绝不伪造）；机构/资质配置化（`lims.report.*`）。

## 八、前端架构要点

- **UI 基准「Aurora Glass」**：暗色沉浸 + 极光配色 + 折射玻璃；令牌统一 `--lims-*`（`styles/tokens.css`）；三类表面 `.lims-glass`/`.lims-glass-refract`/`.lims-panel` 用途不可混用，**数据密集区禁用折射玻璃**；强调色只做光晕/描边，按钮用 brand-gradient；动效 ≤0.45s 且支持 prefers-reduced-motion；参考 Mineradio 仅借鉴设计思路（GPL-3.0 禁止逐字拷贝）。
- **动态路由「路径注册表 + 中间件转换」**：`router/routeRegistry.ts`（21 条显式登记 + PATH_ALIAS 兼容层）→ `router/dynamicRoutes.ts`（`buildNavigation()` 路由与侧栏**同源产出**）→ 五步守卫；catch-all 必须「移除后重加」保证在末尾（否则 F5 刷新变 404）；DB 菜单只存结构不存组件路径；图标白名单 ICON_MAP；无法识别的 path fail-loud `console.warn`。
- **公共组件体系**：PageHeader/AppCard/StatCard/StatusBadge/AppEmpty/AppBreadcrumb/DataTable/DataFilter/LimsChart 等，17 页 100% 迁移；三档分辨率（1440×900/1920×1080/1366×768）无横向溢出。
- **ECharts 集成**：按需引入 + Canvas 渲染器 + 路由级懒加载分包（analysis chunk 542KB/gzip 183KB 不入主包）+ ResizeObserver + CSS 变量取色 + notMerge + 空数据渲染 AppEmpty；统计接口无后端缓存（前端 60s 缓存）。
- **下载统一 `saveBlobAs()`**：先弹「另存为」对话框再发请求（工厂函数强制顺序，规避 transient activation 失效），不支持时降级默认目录；建议文件名带时间戳。
- **AI 前端**：可拖拽悬浮窗（自研 Pointer Events）+ 引用卡片 + 流程引导卡片 + 伴随查询条；`utils/aiStream.ts` 有 30s 静默超时 + `onIncomplete` 兜底；**push 进 `ref([])` 后必须取回响应式代理再改**（改裸对象不触发视图更新，这是「重开面板才看到答案」的真因）。

## 九、API 契约（docs/api/api-spec.md，唯一权威，GLM 起草自审落档）

约 18 章：0 通用约定（响应/分页双轨：新域一律 `current/size`）/ 1 认证（login/refresh/me/logout + 改密）/ 2 监抽任务 / 3 样品（导入/确认/查询）/ 4 项目分解（套库预览不落库+覆盖式保存）/ 5 任务安排（自动分配/确认/改派）/ 6 结果录入（保存/判定/提交）/ 7 报告审核（通过/退回，异常项显式确认红线）/ 8 报告生成（pending/detail/generate/print）/ 9 查询（testing/history/lib）/ 10 省平台导出（EasyExcel 流式、10 列、status≥80、`?taskNo=`）/ 11 基础数据（tester-method 5 接口、product-lib 11 接口）/ 12 系统管理（sys 21 接口，含 R100 三重保护/用户自锁/成环检测/重置密码独立接口）/ 13 检验员任务查询 / 14 统计（9 接口，`stat:view`）/ 15 操作日志 / 16 回退 / 17 AI 助手 / 18 AI 知识库。

## 十、RBAC 权限体系

- 预置角色：R100 综合管理（审核签发/权限管理/全部查询）、R1 样品登记员、R2 任务管理员、R3 检验员（共享账号 njna000/njxa000/njsa000）。
- 权限标识 `resource:action`（约 60 个）：`sys:*`、`base:*`、`task:*`、`sample:import/confirm/query`、`item:decompose`、`assign:confirm/reassign`、`result:entry/export-excel`、`report:audit/sign/generate/print`、`query:*`、`stat:view`、`export:province`、`log:view`、`rollback:execute/sensitive`。
- 鉴权：login→JWT→me（用户+角色+权限标识+菜单树）→接口 @PreAuthorize 二次鉴权；前端 v-permission 只做显隐。
- T-107 系统管理防护全集：用户自锁（不能删自己/最后一个 R100）、SysUserVO 类型层无 password/salt、username 不可改、R100 三重保护、菜单成环检测、角色绑定拒绝删除等。

## 十一、两大新增能力（超出说明书范围，用户新增需求）

### 特性 A：本地 AI 助手（qwen3:4b-instruct + Ollama 项目内托管）
- 零外发：`ai/runtime/` 便携版 Ollama + `ai/models/` 模型（OLLAMA_MODELS 定向）+ `ai/standards/` GB 标准库，全部随项目搬走。
- **领域护栏（三段式确定性，不靠提示词）**：`DomainGuard` 把问题分类为 business（本地确定性作答，**0 次调模型**，16ms 秒回）/ standard（GB 检索+带出处引用）/ other（同事口吻拒答+替代建议）；Ollama 离线/超时/中断返回明确降级态+修复指引，业务接口零影响。
- **GB 检索**：MySQL ngram 全文索引（`gb_clause`），检索路由「已分词短词→BOOLEAN(AND→OR)、整句→自然语言模式」+ 噪声词剔除（标准号前缀/纯数字）；`clause_title` 建索引作为第一排序键（实测 5 项抽样条款全部第 1 位）。
- **OCR 扫描件导入**（`ai/scripts/prepare-standards.py` + 后端扫描任务）：判 PDF 形态先于 OCR；并发必须钉线程（4×7=28，否则 128 线程抢 32 核活锁）；两行式条款标题合并、OCR 数字空格还原、纯数字伪边界剔除（824→0）；断点续跑以页文件存在性为准。
- **数值红线（最重要）**：数值类回答**不得来自模型记忆**，只能来自对象快照（`sample_item`）且带结构化来源，取不到就明说取不到——实测 4B 模型自信答错（毒死蜱黄瓜限量答 0.05，实际 0.02）且错得与正确答案一模一样；`ai_message.citations_json` 存结构化引用快照可审计。
- **结构化回答**（不引 Markdown 库）：`{answer, citations[], refused, domain, online}`，引用卡片天然强制出处。
- **流程引导 + 伴随查询**（特性增量）：业务域事实层恒为确定性代码（`BusinessFlowMap`/`FlowGuideAssembler`），措辞层模型润色默认关闭；`ValueAnchorAssembler` 提供当前样品/状态锚点，AI 回答可带「本次会话关联对象」的快照；`ai_hint_log` 记录所有提示词留痕。

### 特性 B：全流程逐步回退（撤销/回滚）
- **第三条独立白名单 ROLLBACK**（语义三分：VALID 推进 / RETURN 业务否定 / ROLLBACK 纠错，不得合并）。
- **版本化留档**（非快照还原/非反向补偿）：失效前整行 pre-image 存 `sample_data_archive`，主表置失效（不物理删），回退只做「状态逆流+失效下游+留档」；`sample_status_log` 统一时间线（7 类事件：正向/退回/签发/回退/恢复/作废/报告生成）。
- **边策略**（`RollbackEdgePolicy` 唯一权威）：常规 5 边需 `rollback:execute`，敏感边 S70→S60 需 `rollback:sensitive`+二次确认；被拒边 S80→S70（4102）/S90→S80（4103）走作废/召回；跨级一律 4101。
- **四条不变式已单测固化**：乐观条件 UPDATE（4108）、失效处置在状态 UPDATE 后同事务、一次回退恰好 1 条 event_type=4 流水、绝不物理删除。
- **连带收获**：覆盖式 upsert 保存前留档（archive_reason=2），补上 ALCOA+ 原始值可追溯缺口。

## 十二、当前状态与质量

- **完成度**：业务说明书口径 **99%**（13 项业务功能零缺口、17 页前端全完成、T-101~T-803 全 ✅）；两项新能力：AI 助手（真实链路跑通）、回退机制（已按 T-924 内嵌业务页 + 批量 + 跨级链式）。**2026-10-08 全流程修复轮**：31 项缺陷（T-930~T-939）全量修复、数据修复落地、独立回归 53 PASS / 0 FAIL。
- **质量门禁（2026-10-08 复核）**：后端单测 **261/261 全绿**（mvn -o test）；前端 vue-tsc 0 错误 / eslint 0 问题 / vite build 成功；端到端 54 断言全过（含越权真 HTTP 403、S60→S90 全跳）；真实链路 curl_exit=0；回归报告 `docs/test/2026-10-08-regression-verification.md`。
- **Git**：`agent/glm → develop → main` 每轮 fast-forward 固化并推送；最新三分支 hash 见 `STATUS.md` 最新一节。
- **遗留（T-921/T-922）**：① 全仓约 61 处 `rgba(255,255,255,…)` 硬编码（22 种不同 alpha，2026-10-08 复核后判定「抽 3 个变量」不成立、全量令牌化需专门视觉回归，**本轮明确不做**，登记待办）；② `BaseEntity.deleted` Java 类型 Integer 而列已 BIGINT（当前 @TableField(select=false) 无风险，放开读取须改 Long）；③ `SampleDataDisposer` 快照 JSON 含中文派生 getter 冗余（功能无误，将来解析时按列精确序列化）；④ T-922 待业务确认：是否新增第四条 `RECALL` 白名单（S80→S70 召回后重新签发，设计已留扩展点 §10-R6）。

## 十三、给后续改进 AI 的关键提示

1. **先读四件套再动手**：STATUS → TODO → HANDOFF → DECISIONS；交接文档用于定位不用于决策，一切以代码复核为准。
2. **红线清单**：不引入新依赖（离线仓）；不私增状态/跳态（先改 AGENTS 7.2 再改白名单+单测）；AI 不写判定字段、不编造标准号/数值；禁 mock 假数据（一切用户可见数字必须真实）；`sample_item/sample_result` 失效走 `SampleDataDisposer` 禁 delete；S80/S90 无回退边。
3. **已踩过的坑（知识库可迁移）**：SSE ASYNC 派发鉴权、Vue 裸对象响应式失效、BOOLEAN 整句中文 0 命中、ngram 单字丢弃/1191 列清单、OCR 线程超订活锁、@Transactional 内调 @Async 竞态、「禁止 X」诱发 4B 复述 X、catch-all 注册顺序、MySQL 保留字。
4. **扩展点**：RECALL 白名单（T-922，业务确认后按 ROLLBACK 同模式新增）；`autoRegister` 窄例外（seed 补出「会话审计」菜单后须删除该用法）；报告若将来允许 S90 回退改数据则必须改落快照+版本号（先改 AGENTS 7.2）；统计接口数据量上万后加缓存。
