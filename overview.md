# LIMS 项目交付总览

## 🆕 最新一轮（2026-09-18）：AI 助手跑通真实链路 + 用户实测缺陷修复

**TL;DR**：把用户提供的 **67MB / 422 页扫描版 GB 2763-2021 用 OCR 导入并建成检索索引**，
助手升级为「业务全流程助手」（自动关联标准 + 按样品状态给下一步操作指引）；
随后修复用户实测反馈的 **3 个独立缺陷**（流式卡死 / 响应式失效 / 检索召回错误）。
后端 **236 项单测全绿**，前端 `vue-tsc` / `eslint` / `vite build` 三门禁全绿。

### A. 扫描版 GB 标准导入（OCR 通道）

| 项 | 结果 |
|---|---|
| PDF 形态判定 | 每页 = 一张 1240×1753 JPEG（`/DCTDecode`）· **无字体无文字层**（pypdf 抽样 7 页抽出 **0 字符**） |
| OCR 选型 | `rapidocr-onnxruntime`（离线 Python 预处理；后端**仍拒收 PDF**，fail-loud 分流 4211/4212） |
| 并发实测 | 单进程 8.4s/页；**`workers=4` 未钉线程时只有 5.29 页/分钟（比单进程还慢 26%）且会活锁** —— 根因是 32 核上 4×32=128 线程超订；钉成 `4×7=28` 后正常 |
| 成果 | 422 页全部识别 → **4660 个条款块**入 `gb_clause`，`ocr_derived=1` 溯源 |

### B. 用户实测反馈的三个缺陷（本轮修复）

| # | 现象 | 根因 | 证据 |
|---|---|---|---|
| 1 | 界面永久「正在生成」，关掉悬浮窗再打开才看到回答 | 前端改「push 进 `ref([])` 的**裸对象**」**不触发视图更新**（重开组件重新挂载才重新求值） | Node + `@vue/reactivity` effect 计数：`render#` 停在 2 不动 |
| 2 | 同上（叠加因素） | `SseEmitter` 完成触发 **ASYNC 派发** → JWT 过滤器默认跳过 → 匿名 → `AccessDeniedException`；SSE 响应已提交无法转错误 ⇒ **连接被 RST** | `curl -N` **退出码 18**（帧其实发全了）+ 日志 `AuthorizationFilter:98` |
| 3 | 问「GB 2762 铅的限量」返回 GB 2763 的无关条款 | 整句走 BOOLEAN ⇒ 短语语义 0 命中；`GB`/`2763-2021` 噪声词 OR 兜底制造 **968 条假命中**，正确块 `score=0` 未进候选 | MySQL 探针 `COUNT(*)` + `score` |

**修复要点**：前端「取回响应式代理再改」+ SSE 静默超时/无 `done` 帧兜底；
后端放行 `DispatcherType.ASYNC/ERROR`、ASYNC 派发也认证、审计 `preHandle` 幂等；
检索改为「整句走自然语言 + 剔除噪声词 + 条款标题相关性优先」；
OCR 条款版式归一（**两行式标题合并** —— 原文 `4. 121` / `毒死蜱（chlorpyrifos）` 分列两行，旧实现导致正文被挂到「表120」编号下）。

**修复后实测**：经 5173 代理 `curl_exit=0`；`4.121 → 毒死蜱（chlorpyrifos）`；
5 个抽样项目（毒死蜱/阿维菌素/腐霉利/吡虫啉/多菌灵）条款**全部排到检索第 1 位**；
纯数字噪声标题 **824 → 0**；审计日志记到 `operator=nj001` / `duration_ms=5570`（此前 `anonymousUser` / `0`）。

> 完整复盘见 `docs/knowledge/2026-09-18-sse-async-vue-reactivity-ngram-routing.md`（含「现象→根因→修法→判据」四类坑）。

## 🆕 2026-09-17：新增两项能力 —— 本地 AI 助手 + 全流程逐步回退

**TL;DR**：为项目加装两项**超出业务说明书范围**的新能力 —— 一个**只谈本项目业务、能检索本地 GB 标准库**的
本地大模型悬浮助手（`qwen3-4b`，托管在项目目录内），以及一套**覆盖七阶段每一步、可逐级回退并可取证**的流程回溯机制。
后端 **212 项单测全绿**（既有 151 + 本轮新增 61），前端 `vue-tsc` / `eslint` / `vite build` 三门禁全绿。

| 特性 | 做法要点 |
|---|---|
| **A｜本地 AI 助手** | qwen3-4b 由项目内 `ai/runtime` + `OLLAMA_MODELS=ai/models` 托管（可整体搬走）；AI 调用用 **JDK17 `HttpClient`**、流式用 `SseEmitter`（**不引 spring-ai/WebFlux**）；**领域护栏**＝业务域由本地规则**确定性作答（不调模型）**、越界**礼貌拒答**（同事口吻 + 替代建议）、标准域走 GB 检索；GB 库用 **MySQL `FULLTEXT ... WITH PARSER ngram`** 秒级检索（只查索引不读文件）；回答为**结构化对象 + 引用卡片**（不引 Markdown 库）；悬浮窗自研 Pointer Events 拖拽 + 位置记忆，样式全用 `--lims-*` 令牌。**零新增 npm 依赖**；Maven 仅新增 `jsoup`（HTML 解析）。 |
| **B｜全流程逐步回退** | **第三条独立白名单 `ROLLBACK`**（6 条边，**S80/S90 物理上没有回退边**）；统一状态流水 `sample_status_log`（7 类事件）+ `sample_rollback`（可恢复状态机）+ `sample_data_archive`（失效留档可取证）+ `report_void`（S80/S90 作废/召回**标记动作**，不改 status）；分级授权（`S70→S60` 为敏感回退，需 R100/R2 + 强理由 + 二次确认）；回退前展示**下游失效清单**，回退**可原路恢复**，历史**不消失**。 |

**关键设计裁决**（全文见 `DECISIONS.md` 2026-09-17，共 16 条）

- **「退回」与「回退」是两个语义**：前者是业务否定（下游角色打回、不动数据），后者是纠错（操作者撤回、必须失效下游且可再撤销）——故**三张白名单不得合并**。
- **红线落在白名单里，不落在 UI 里**：S80 报告已对外生效、S90 数据已上报省平台，回退边**根本不存在**，绕不过去。
- **审计系统里回退 ≠ 删除**：数据层选「版本化留档」，排除「快照还原」（会让历史「从未存在」）与「反向补偿」（逆操作漏一个即静默损坏）。
- ⚠️ **最高风险改动**：`sample_item`/`sample_result` 的 `deleted` 语义升级为「`0`=有效 / 非 0=行自身 id」，解决「覆盖式重建 + 二次失效撞唯一键」；MP `@TableLogic` **不支持表达式**（实测），故走显式 `set(deleted, id)` 并禁用 `baseMapper.delete(...)`。
- **护栏写成代码与测试，不写成提示词**：`AiDomainIsolationTest` 用**反射穷举依赖**断言 AI 不持有判定链 Mapper。
- **推翻旧自裁**：2026-09-13「不新建状态流水表」的前提被回退需求命中，本轮改为新建 `sample_status_log` 并落档。

**验证（由我亲自复现，非仅采信成员自述）**

| 项 | 结果 |
|---|---|
| 后端 `mvn -o test` | **Tests=212, Failures=0, Errors=0**（既有 151 + 本轮新增 61） |
| 前端 `vue-tsc --noEmit` | 退出码 0，无输出（0 错误） |
| 前端 `eslint .` | 无输出（0 error / 0 warning） |
| 前端 `vite build` | 退出码 0；**主包零增长**（`index-*.js` 1,276.92 kB / gzip 412.81 kB，与改动前基线一致）——悬浮窗与回退 UI 落在 `MainLayout` chunk（28.45 kB）与懒加载 chunk，**未进主包** |
| 三条白名单独立性 | 三张独立 EnumMap；`ROLLBACK` 恰好 **6** 条边、**零 S80/S90**；`assertTransition(S60,S50)` 仍拒绝 |
| AI 不碰判定链 | `service/ai/**` 对结论字段**零写命中**；唯一 `SampleMapper` 依赖是 `BusinessContextReader` 的**只读**依赖，已由 `AiDomainIsolationTest` 白名单锁死 |
| 回退不变式④ | QA 首轮指出「只写在类注释里、无断言」，**第 2 轮补齐 `SampleDataDisposerTest`(8 项)** 固化为可运行断言 |

**⚠️ 两件事必须由你亲手做才能闭环（不得视为已完成）**

1. **Ollama 运行时与 qwen3-4b 权重尚未下载** —— 实施环境代理对 GitHub Release 大文件返回 **502**，
   实测 **7 条镜像路径全部失败**（GitHub 直连 / ghfast.top / gh-proxy.com / ghproxy.net / gh.llkk.cc / hf-mirror / modelscope）。
   交付形态是**一键部署脚本 + 多镜像回退 + 手动兜底**：
   ```powershell
   cd D:\lims\ai\scripts
   .\deploy-ollama.ps1 ; .\start-ollama.ps1 ; .\status-ollama.ps1
   ```
   → 本轮 AI 能力的验收边界是「**Ollama 不可用时系统行为正确**」（降级 + fail-loud + 业务零影响），**真实推理未端到端跑通**。
2. **GB 标准库无真实数据**（用户尚未提供标准文件），检索链路仅用构造数据验证；
   另有**悬浮窗拖拽手感 / 回退确认框 / 时间线**需人工体验终验。

**交付物索引**

| 类型 | 路径 |
|---|---|
| PRD（含 6 处设计张力裁决） | `docs/design/2026-09-17-prd-ai-assistant-and-rollback.md` |
| 架构设计（选型反例排除 + 9 表 DDL + 契约 + 文件清单 + 任务分解） | `docs/design/2026-09-17-arch-ai-assistant-and-rollback.md` |
| 知识库：回退机制 | `docs/knowledge/2026-09-17-rollback-mechanism.md` |
| 知识库：本地 AI 与 GB 检索 | `docs/knowledge/2026-09-17-local-ai-and-gb-retrieval.md` |
| 工作日记 | `docs/journal/2026-09-17-glm-ai-assistant-and-rollback.md` |
| AI 运行时与部署脚本 | `ai/README.md`、`ai/scripts/*` |
| 建表与迁移 | `db/init/10_rollback_tables.sql`、`db/init/11_ai_tables.sql`、`db/migrations/V8__rollback_and_ai.sql` |

**未提交**：本轮**未执行任何 git 提交**；工作区含**本会话之前的历史遗留未提交改动**，
提交前须 `git status --short` 逐项核对（AGENTS 第 12 章红线）。

---

## 🔧 上一轮（2026-09-14 16:10）：修复用户实测反馈的 4 类问题

**一句话**：用户以 R3 检验员等账号实测反馈的问题已全部闭环——**3 个代码缺陷 + 2 个演示数据缺口**，
现在**六个账号各自都有可走的第一步**。提交 `7b73417`。

| 用户反馈 | 根因 | 处理 |
|---|---|---|
| R3 工作台「业务概览加载失败」 | 工作台**无条件**调 `/stat/overview`（需 `stat:view`），403 当错误 | 工作台改**权限感知**：只请求有权限的接口；无权限区块渲染**中性空态**；KPI 按权限过滤；Hero 按钮按权限显隐 |
| 点「查看质量分析」404 | 按钮硬编码跳转；未授权页未注册 → catch-all | 按钮加 `v-if`；守卫回查注册表，**已登记但无权限 → 403** |
| 下载导入模板「空页面/没响应」 | `target=_blank` + dev server 对 `.xlsx` 返回**空 Content-Type** | 改用 HTML **`download` 属性**（忽略 Content-Type）；实测真实落盘 6106 bytes |
| R3「没有录入的地方、录不了」 | **非缺陷**：库里无 S40/S50 样品 | 新增 `db/seed/03_demo_flow_seed.sql`（幂等）：**DEMO-2026-003 草鱼 S40** 供检验员录入 |
| 「报告生成不了」 | **非缺陷**：无 S80 样品，两条都是 S90 → 只给「重打印」 | 新增 **DEMO-2026-005 S70**（可签发→生成）、**DEMO-2026-004 S60**（可审核） |
| 「其他账号也要关注」 | R1/R2 同样吃 403 | 逐角色普查 6 账号 × 全部菜单接口；修复后**失败请求 0** |
| （自查）控制台 15 条 403 | 顶部「待办提醒」挂载即拉 6 个接口 | 待办源声明 `permission`，**发请求前**过滤 |

**验证（实测）**：R3 工作台红错 **0** ｜ 点质量分析 → **403 页** ｜ **实际录入保存 0/4 → 75%**、状态转「检验中」 ｜
模板**真实落盘** ｜ 审核放行红线生效 ｜ 签发→生成→打印全通 ｜ **console 0 错误 / 0 失败请求** ｜
后端 **113/113** ｜ 前端 lint/tsc/build 全过。另清理 14 个累积构建产物目录（36 MB，均 gitignore）。

### 演示数据：每个角色的第一步

```bash
mysql -uroot -p123456 --default-character-set=utf8mb4 lims < db/seed/03_demo_flow_seed.sql
```

| 演示样品 | 状态 | 给谁测 |
|---|---|---|
| DEMO-2026-001 鲜食玉米 | S10 | `nj002` 样品登记员 → 登记确认 |
| DEMO-2026-002 菠菜 | S30 | `nj003` 任务管理员 → 自动分配 + 安排确认 |
| DEMO-2026-003 草鱼 | S40 | `njsa000`/`njna000`/`njxa000` → **录入 → 自动判定 → 提交** |
| DEMO-2026-004 鳜鱼 | S60 | `nj001` → 报告审核（含 1 个待判定，可验**放行红线**） |
| DEMO-2026-005 团头鲂 | S70 | `nj001` → 签发 → 生成 CMA / CMA-CATL → 打印 |

### 下载入口在哪里

| 想下载什么 | 入口 |
|---|---|
| 采样单导入模板 | **样品登记**页 → 页头「下载导入模板」 |
| 检验员任务 Excel | **我的检验任务**页 → 导出 |
| 省平台上报 Excel | **省平台上报**页（可按任务编号筛选） |
| 检验报告 | **报告生成**页 → 生成/重打印 → 打印页「打印 / 另存为 PDF」 |

> 项目不生成报告 PDF 文件：报告是白底 A4 的 HTML 页面，打印即得纸质件；电子件用浏览器另存为 PDF。

---

# 上一轮（2026-09-14 15:20）：项目功能完工交付总览

## 一句话总结

**说明书 13 项业务功能全部落地、17 个前端页面全部完成、两处「空壳/假数据」缺陷已清除；
Git 对象库从「91 个对象缺失、`git status` 直接报错」修复为完整可用，并已推送三分支。项目可交付。**

- 提交：`a768952`（主体，78 文件 / +5528 −1607）+ `3b3d774`（技能文档）
- 远端：`agent/glm = develop = main = 3b3d774` ｜ 工作区干净（0 项）｜ `git fsck` 0 missing / 0 broken
- 进度：**96% → 99%**（业务功能零缺口，剩余仅为用户人工体验终验）

---

## 一、盘点结论：已完成的没有遗漏（并推翻了两处过期交接描述）

| 盘点项 | 结论 |
|---|---|
| 说明书 13 项功能 | **全部落地**（RBAC / 方法资质 / 项目标准库 / 监抽任务 / 采样单导入 / 项目分解 / 任务安排 / 检验员任务查询+导出 / 结果录入+自动判定 / 审核签发 / CMA·CMA-CATL 报告生成 / 在检·历史·项目库查询 / 省平台上报导出） |
| 前端 17 页 | 公共组件（PageHeader / AppCard / StatusBadge / AppEmpty / DataFilter / DataTable / askConfirm）**100% 迁移** |
| 后端接口 | 15 个 Controller / 90+ 端点，与 api-spec 逐章对齐，**无未实现端点** |
| 权限标识 | 代码 `hasAuthority` ↔ seed `sys_menu` **逐个比对无缺口** |
| P1~P5 巡检问题 | **全修**（含 P3 列宽：全站 65 处 `show-overflow-tooltip`） |

> ⚠️ 交接单曾称「T-917-5 还剩 6 页待迁」「P3 列宽待修」——**均为过期状态**，实测早已完成。
> **教训：交接文档描述的是「当时」，代码描述的是「现在」；复核必须看代码。**

## 二、本轮真正修复的两处缺陷 + 一处工程债

### 1. T-918 操作日志落地（消除空壳）

用户菜单「操作日志」此前只有一句「待后端接入」。本轮补齐完整链路：

| 层 | 产物 |
|---|---|
| 数据 | `db/init/09_operation_log.sql` + `db/migrations/V7__add_operation_log.sql` |
| 写入 | `config/OperationLogInterceptor`（HandlerInterceptor）+ 注册进 `WebConfig` |
| 查询 | `GET /api/sys/log/page` + Service/DTO/VO/Controller |
| 契约 | `docs/api/api-spec.md` 第 15 章 |
| 前端 | `api/system.ts` + `MainLayout.vue` 真实分页表格 |
| 测试 | `OperationLogInterceptorTest`（6 项） |

**三个关键设计**
1. **不用 AOP 用 HandlerInterceptor**：离线 Maven 仓无 `aspectjweaver`。审计的本质需求是
   「集中记录 + 零业务侵入」，spring-webmvc 自带的拦截器同样满足。
2. **绝不记录请求体**：请求体可能含密码（登录/改密/重置密码），只记方法/路径/结果/耗时/操作人/IP。
3. **分级数据范围**：接口只要求登录，不用 `@PreAuthorize('log:view')`——
   否则普通检验员查不到自己的记录（ALCOA+ 基本要求失效）。
   改为「人人可查自己（服务端强制 `operator=本人工号`），`log:view` 才能跨用户」。
   **权限注解解决「能不能调接口」，解决不了「能看哪些行」。**

### 2. 顶部铃铛去假数据

原为 **4 条写死的假通知**（「3 份报告待审核」「样品 JK-2026-001 检测出铅超标」…），
违反 DECISIONS 2026-09-13「禁 mock 假数据」原则。改写为 6 个业务域真实待办汇总：
数据复用既有分页接口的 `total`（不新增接口/字段，保证与点进去看到的条数永远一致）、
零值不展示、`Promise.allSettled` 独立容错、不做「已读」。

> **关键认识：「禁 mock」的适用边界是「一切用户可见的数字」，不只业务页面。壳层（顶栏）最容易漏。**

### 3. 死代码处置

- `AppSkeleton.vue`：零引用 → **接入工作台 KPI 加载态**（UI 规范「Loading 优先 Skeleton」）
- `ProgressBar.vue`：零引用 → **删除**（全站 4 处进度已用 `el-progress`，含其独有的 `text-inside` 形态）

## 三、Git 对象库修复（本轮最大障碍）

| 阶段 | 现象 / 处理 |
|---|---|
| 症状 | `.git` 缺 **91 个对象**（6 commit + 多 tree/blob）；`git status`/`branch` 直接报错 |
| 弯路 | `git fetch` **修不好**——本地 `refs/remotes/origin/*` 指向旧 hash，git 据此告诉远端「这些我都有了」→ 远端不发送 → 拉完仍缺（`did not send all necessary objects`） |
| 解法 | `git clone --mirror` 取回完整对象库 → 拷 `objects/pack/pack-<new>.*` → **删过期 `multi-pack-index`** → `git fsck --full` = 0/0 |
| 副坑 | 给 `git clone` 传绝对 POSIX 路径会**静默什么都不做**（退出码 0、目录不存在），须用相对路径 |
| 副坑 | 判断能否推送只看 **`git ls-remote`**；`curl https://github.com` 返回 `000` 但 git 传输栈完全正常 |
| 沙箱坑 | 提交后 `.git/refs/heads/agent/` **整个目录被吞**，须从 reflog 取 hash 并用 PowerShell 回填 |

## 四、验收（全部实测，非推断）

| 项目 | 结果 |
|---|---|
| 后端单测 | **113/113 BUILD SUCCESS**（新增 6） |
| 前端门禁 | `eslint` 0 错误 ／ `vue-tsc --noEmit` 0 错误 ／ `vite build` ✅ |
| **真实浏览器全量遍历** | Edge + CDP 走 **20 个页面** → **0 console error ／ 0 网络请求失败** |
| **三档分辨率** | 1440×900 ／ 1920×1080 ／ 1366×768：`scrollWidth == clientWidth`，DOM 无越界元素 |
| 操作日志端到端 | 6 断言全过：写请求入库 ／ **403 失败也留痕** ／ GET 不入库 ／ 无 `log:view` 仅见自己 ／ **伪造 `operator` 参数无效** ／ 有权限可按工号过滤 |
| 待办提醒 | 实测 2 条真实待办（待分解 1、可生成报告 1），角标 = 2，无假数据 |

## 五、用户 20 项 Checklist 核对

① Sidebar ② Header ③ Card ④ Button ⑤ Input ⑥ Table ⑦ StatusBadge ⑧ Modal/Drawer
⑨ Loading/Empty/Error ⑩ 组件视觉统一 ⑪ 品牌色统一 ⑫ 无大面积空白 ⑬ 数据层级清晰
⑭ 异常数据明显 ⑮ Dashboard 真实数据 ⑯ ECharts 有意义 ⑰ **无 Console Error ✅实测**
⑱ 不破坏业务 ✅（113 单测 + 端到端） ⑲ **无横向溢出 ✅实测三档** ⑳ **1440/1920 布局正常 ✅实测**
（①~⑯ 为视觉/结构项，已由公共组件 100% 迁移 + 逐页截图抽样确认）

## 六、下一步（用户自测）

```bash
# 后端（8080）
cd D:\lims\backend
set JAVA_HOME=C:\Program Files\Java\latest\jdk-21
C:\Users\Chen\Desktop\apache-maven-3.9.11\bin\mvn.cmd -o -DskipTests spring-boot:run

# 前端（5173，代理 /api → 8080）
cd D:\lims\frontend
npm run dev
```

- 登录账号（密码 = 账号名）：`nj001`(R100 综合管理) / `nj002`(登记员) / `nj003`(任务管理员) /
  `njsa000`·`njna000`·`njxa000`(检验员)
- 建议重点体验：顶部铃铛「待办提醒」（真实数据）→ 右上头像「操作日志」（本轮新增）
- 若需继续增强（**均非缺陷**）：报告导出 PDF ／ 通知已读持久化 ／ 操作日志按月归档

## 七、本轮资产

| 类型 | 文件 |
|---|---|
| 日记 | `docs/journal/2026-09-14-glm-final-closure.md`（含 3 条高价值踩坑） |
| 知识 | `docs/knowledge/2026-09-14-operation-log-interceptor.md`（零 AOP 审计实现 + 8 项验收清单） |
| 技能 | `.agents/skills/sandbox-git-push/SKILL.md` 新增**规则 8**（对象库损坏恢复） |
| 契约 | `docs/api/api-spec.md` 第 15 章 |
| 治理 | `DECISIONS.md`（+13 条）/ `TODO.md`（T-917 ✅、+T-918）/ `STATUS.md` / `HANDOFF.md` |
| 截图 | `.shots/final/`（登录 / 工作台三档 / 待办提醒 / 操作日志 / 4 个业务页） |
