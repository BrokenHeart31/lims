# STEP 1：现有项目与 UI 现状分析（T-917-1）

> **性质**：只读分析，**不改一行代码**。依据 `C:\Users\Chen\Desktop\前端优化\ui提示词.txt` §二 的硬性要求
> （「在开始编码之前，必须先完整检查当前项目」「先建立『现有项目 → 新 UI 系统』的映射关系」）。
>
> **产出目标**：让后续 STEP 2~10 的每一步都有据可依，且**不重造轮子**（提示词 §三十三 明确禁止）。
>
> **执行人**：GLM ｜ **执行日**：2026-09-13

---

## 一、结论先行（三句话）

1. **项目基础远好于预期**：已有 **Aurora Glass 设计令牌体系**（`tokens.css` 301 行）、
   **6 个公共组件**、**17 个页面全部使用公共组件**（`el-card` 直接用量 = 0，`el-empty` 直接用量 = 0），
   硬编码色值几乎为零。**这不是一个「Element Plus 拼出来的后台」**。
2. **真正待解决的是「对齐与补齐」，不是「推倒重来」**：现有色板（`#08090b` 纯黑底 / `#00f5d4` 青）
   与新规范色板（`#080C10` `#0D1218` `#131A22` 层级底 / `#18D6C5` 品牌青）**存在系统性偏差**；
   且规范要求的 **Skeleton / ProgressBar / DataTable / DataFilter / AppConfirm** 等组件**尚未抽取**。
3. **最大风险不是视觉，而是「破坏业务」**：本项目后端 9 阶段已全通、107 单测通过、端到端 54 断言通过。
   UI 重构必须**严格限定在 `frontend/src/{styles,components,layouts,views}`**，
   已确认**无需触碰** DB / API / Store 数据结构 / 状态机（详见 §五「不可破坏项」的自查）。

---

## 二、当前技术栈（提示词 §二 逐条确认）

| 检查项 | 现状 | 与规范一致性 |
|---|---|---|
| Vue 版本 | **Vue 3.5.13** | ✅ 符合 |
| 语言 | **TypeScript 5.6.3**（strict，`vue-tsc --noEmit` 门禁） | ✅ 符合 |
| 构建工具 | **Vite 6.0.5** | ✅ 符合 |
| UI 方案 | **Element Plus 2.9.3**（全量引入 + 手写主题覆盖层） | ✅ 提示词 §三十四 要求「已用 EP 则优先主题覆盖，不要迁移框架」 |
| 状态管理 | **Pinia 2.3.0**（仅 `stores/auth.ts`） | ✅ 保留 |
| 路由 | **Vue Router 4.5.0**（本轮刚完成动态路由 T-916） | ✅ 保留 |
| 图表 | **ECharts 5.5.1**（按需引入，路由级分包） | ✅ 保留 |
| CSS 方案 | **原生 CSS 变量令牌 + scoped style**（无 Tailwind / 无预处理器） | ✅ 符合「少依赖」 |
| 依赖数量 | 生产依赖 **7 个** | ✅ 极简 |

**当前 ECharts 配置位置**：`frontend/src/utils/chartOptions.ts`（取数/布局/图形三层分离）
+ `frontend/src/components/common/LimsChart.vue`（容器：ResizeObserver + CSS 变量取色 + 空态优先 + notMerge）。

**当前权限/路由逻辑**：
- 动态路由 = **路径注册表**（`router/routeRegistry.ts` 21 条）+ 中间件转换（`router/dynamicRoutes.ts`）
- 守卫五步在 `router/index.ts`；按钮级权限用 `v-permission` 指令（`directives/permission.ts`）
- **UI 重构不得改动这套逻辑**（用户不可破坏项含「权限体系」「登录认证」）

---

## 三、现有页面清单（17 个业务页 + 2 个错误页，合计 19 个 .vue）

| # | 路由 | 文件 | 行数 | 规范对应章节 | 当前状态 |
|---|---|---|---|---|---|
| 1 | `/dashboard` | `views/dashboard/index.vue` | 612 | §十 工作台 | 已有 KPI + 图表；**待核实是否真实数据** |
| 2 | `/task` | `views/task/index.vue` | 558 | §十一 监抽任务 | 已有 Filter+Table+Pagination |
| 3 | `/sample` | `views/sample/index.vue` | 845 | §十二 样品登记 | 已有 KPI+Filter+Table |
| 4 | — | *（样品详情）* | — | §十三 样品详情 | ⚠️ **无独立页面**（生命周期 Timeline 待建） |
| 5 | `/item/decompose` | `views/item/index.vue` | 747 | §十四 项目分解 | 已是 Master-Detail 抽屉结构 |
| 6 | `/assign/index` | `views/assign/index.vue` | 690 | §十五 任务安排 | 仅列表；⚠️ **无看板视图** |
| 7 | `/result/entry` | `views/result/index.vue` | 877 | §十六 结果录入 | 已有结果表；**待核实异常行高亮** |
| 8 | `/report/audit` | `views/report/audit.vue` | 1086 | §十八 报告审核 | **最大的页面**；第一批重点重构对象 |
| 9 | — | *（审核详情）* | — | §十九 报告审核详情 | 已内嵌 Drawer（非独立页） |
| 10 | `/report/generate` | `views/report/generate.vue` | 447 | §二十 报告签发 | 已有 |
| 11 | `/query/testing` | `views/query/testing.vue` | 413 | §二十一 检测数据 | ⚠️ 与 §二十一 要求的指标/图表清单差异待核 |
| 12 | `/query/analysis` | `views/query/analysis.vue` | 403 | §二十二 质量分析 | 已有 6 KPI + 7 图（ECharts） |
| 13 | — | *（统计报表）* | — | §二十三 统计报表 | ⚠️ **并入 `/query/analysis`**，无独立报表页 |
| 14 | `/sys/user` | `views/system/user.vue` | 725 | §二十四 用户管理 | 已有 |
| 15 | `/sys/role` | `views/system/role.vue` | 546 | §二十五 角色权限 | 已是「左角色 + 右权限树」 |
| 16 | — | *（操作日志）* | — | §二十六 操作日志 | ⚠️ **DB 有菜单但页面不存在**（本轮已设 `visible=0`） |
| 17 | `/sys/menu` + `/sys/dept` | `views/system/{menu,dept}.vue` | 583 + 401 | §十七 系统设置 | 已有 |
| — | `/base/product-lib` | `views/base/product-lib.vue` | 907 | （规范未列） | 已有，明细抽屉整表编辑 |
| — | `/base/tester-method` | `views/base/tester-method.vue` | 645 | （规范未列） | 已有 |
| — | `/result/my-tasks` | `views/result/my-tasks.vue` | 429 | （规范未列） | 已有 |
| — | `/export/province` | `views/export/province.vue` | 163 | （规范未列） | 已有 |
| — | `/query/history` `/query/lib` | `views/query/{history,library}.vue` | 448 + 351 | （规范未列） | 已有 |
| — | `/403` `/404` | `views/error/{403,404}.vue` | 26 + 26 | （规范未列） | 已有 |
| — | `/login` | `views/login/index.vue` | 277 | （规范未列） | 已有（Aurora 光斑效果） |
| — | `/report/print` | `views/report/print.vue` | 110 | §二十 PDF | standalone 路由，公文版式 |

**页面总数核对**：`views/` 下 **20 个 .vue**（17 业务 + 2 错误 + 1 打印）。
用户所说「约 17 个页面」与规范 §三十六 STEP 5 的列表一致（规范列的 17 项中有 3 项在本项目**无独立页面**，
另有 6 个本项目特有页面规范未列）——**映射表见 §四**。

---

## 四、映射表：规范 §三十六 STEP 5 的 17 项 → 本项目实际落点

| 规范 STEP 5 项 | 本项目对应 | 差异处理建议 |
|---|---|---|
| 1. 工作台 | `dashboard/index.vue` | 直接重构 |
| 2. 监抽任务 | `task/index.vue` | 直接重构 |
| 3. 样品登记 | `sample/index.vue` | 直接重构 |
| 4. 样品详情 | **无独立页面** | ⚠️ **建议新增**：样品生命周期 Timeline（§十三）。<br>风险：需新路由 + 新 API？→ 已核 `api/sample.ts` 有详情类接口，**可纯前端组装**，不加后端 |
| 5. 项目分解 | `item/index.vue` | 已是 Master-Detail，对齐视觉即可 |
| 6. 任务安排 | `assign/index.vue` | 列表已有；**看板视图为增强项**，非必须（§十五 明确「不要炫技」） |
| 7. 结果录入 | `result/index.vue` | 重点：异常行高亮（§十六） |
| 8. 报告审核 | `report/audit.vue` | **第一批重点**（用户明确指定） |
| 9. 报告审核详情 | 已内嵌 Drawer | 保持 Drawer 形态（§十九 允许 Drawer 或详情页） |
| 10. 报告签发 | `report/generate.vue` | 直接重构 |
| 11. 检测数据 | `query/testing.vue` | 核实指标清单 |
| 12. 质量分析 | `query/analysis.vue` | 直接重构（已是亮点页） |
| 13. 统计报表 | **并入 `query/analysis.vue`** | 保持合并（拆分会产生重复页面） |
| 14. 用户管理 | `system/user.vue` | 直接重构 |
| 15. 角色权限 | `system/role.vue` | 已是左角色右权限树，对齐视觉 |
| 16. 操作日志 | **无页面**（`visible=0`） | ⚠️ 建页需 **backend 新增 API**（违反不可破坏项）→ **本轮不做**，已在 TODO 标注 |
| 17. 系统设置 | `system/{menu,dept}.vue` | 直接重构 |

**本项目特有、规范未列的 6 页**（`product-lib` / `tester-method` / `my-tasks` / `province` / `history` / `lib`）：
**一律纳入统一升级**（用户原话：「这是整个 LIMS 项目的统一升级」），按 §四「所有页面共享同一套 Design System」执行。

---

## 五、现有 Design System 盘点（提示词 §四 的 20 项抽象清单）

### 5.1 已具备的设计令牌（`styles/tokens.css`，301 行，分 11 组 + 扩展段）

| 组 | 令牌 | 现状值 | 规范要求值 | 差异 |
|---|---|---|---|---|
| 底色 | `--lims-bg` | `#08090b` | `#080C10` | ⚠️ 偏黑，需对齐 |
| 深底 | `--lims-bg-deep` | `#050607` | — | 保留 |
| 纸面 | `--lims-paper` | `#0e1014` | `#0D1218`（Sidebar） | ⚠️ 需拆分 Sidebar/Header/Card 三层 |
| 表面 | `--lims-surface` | `rgba(18,21,26,.66)` | `#131A22`（Card） | ⚠️ **半透明玻璃 vs 实色层级**，方向性差异 |
| 表面悬浮 | `--lims-surface-hover` | `rgba(28,32,38,.72)` | `#171F28`（Card Hover） | ⚠️ 同上 |
| 高层级 | — | **无** | `#1B2530`（Elevated） | ➕ 待新增 |
| 品牌色 | `--lims-accent` | `#00f5d4` | `#18D6C5` | ⚠️ 需替换（更沉稳） |
| 品牌 hover | — | **无**（用 `--lims-accent-deep`） | `#20E5D3` | ➕ 待新增 |
| 品牌弱化 | — | 散落 `rgba(0,245,212,0.10/0.12/0.16)` | `rgba(24,214,197,0.10/0.12/0.16)` | ➕ 待收敛为 3 个令牌 |
| 成功 | `--lims-success` | `#22c55e` | `#35D49A` | ⚠️ 需替换 |
| 信息 | `--lims-info` | `#7fd8ff` | `#4EA1FF` | ⚠️ 需替换 |
| 警告 | `--lims-warning` | `#f5a524` | `#F5B942` | ⚠️ 需替换 |
| 危险 | `--lims-danger` | `#ff5367` | `#F06472` | ⚠️ 需替换 |
| 紫 | `--lims-purple` | `#9b7cff` | `#9B7CFF` | ✅ 一致 |
| 文字主 | `--lims-ink` | `#e8ecef` | `#F5F7FA` | ⚠️ 需替换 |
| 文字次 | `--lims-ink-2` | `#d2d7dc` | `#CBD5E1` | ⚠️ 需替换 |
| 文字弱 | `--lims-muted` | `#8a9099` | `#94A3B8` | ⚠️ 需替换 |
| 文字禁用 | `--lims-faint` | `#5c636d` | `#64748B` | ⚠️ 需替换 |
| 边框 | `--lims-hair/2/strong` | `rgba(255,255,255,0.07/0.11/0.16)` | `0.06/0.08/0.12` | ⚠️ 略偏亮，需对齐 |
| Sidebar 宽 | `--lims-sidebar-w` | `224px` | `约 224px` | ✅ 一致 |
| Header 高 | `--lims-header-h` | `60px` | `约 64px` | ⚠️ 差 4px |
| Content padding | `--lims-content-px/py` | `24px` | `24~32px` | ✅ 一致 |
| Card 圆角 | `--lims-r-card` | `12px` | `12px` | ✅ 一致 |
| 控件圆角 | `--lims-r-ctrl` | `8px` | `8px` | ✅ 一致 |
| 行高 | `--lims-row-h` | `52px` | `52~60px` | ✅ 一致 |

**结论**：**令牌体系的结构已完全正确**，只需**替换取值** + **新增 3 个缺失槽位**（Elevated / 品牌 hover / 品牌弱化三档）。
这是 STEP 2 的工作量主体 —— **改一张表，全站生效**，这正是令牌体系的价值。

### 5.2 已有公共组件（`components/common/`，6 个）

| 组件 | 行数 | 规范对应 | 评价 |
|---|---|---|---|
| `AppCard.vue` | 85 | §三十一 Card | ✅ 三 variant（glass/panel/flat）设计合理；⚠️ 需按 §三十一 降 shadow |
| `PageHeader.vue` | 139 | §九 页面 Header | ✅ 面包屑+标题+副标题+操作区齐全 |
| `StatCard.vue` | 185 | §十 Stat Card | ✅ 图标+数值+标签+趋势+微图 |
| `StatusBadge.vue` | 110 | §十二 状态 Badge | ✅ **8 种 tone + dot + 2 size**，覆盖度很好 |
| `AppEmpty.vue` | 92 | §二十七 Empty | ✅ 图标+标题+提示+操作槽 |
| `AppBreadcrumb.vue` | 80 | §七/§九 | ⚠️ **0 个页面在用**（PageHeader 自带了面包屑槽）→ 死代码，需决策 |
| `LimsChart.vue` | 160 | §二十一 图表 | ✅ ResizeObserver + 主题取色 + 空态优先 |

### 5.3 规范要求但**尚缺**的组件（提示词 §三十三 清单逐条比对）

| 规范要求 | 现状 | 处理 |
|---|---|---|
| `layout/AppSidebar` | MainLayout 内联 | ⚠️ 可抽（1153 行偏大） |
| `layout/AppHeader` | MainLayout 内联 | ⚠️ 可抽 |
| `layout/AppContainer` | 无 | ➕ 新增（统一 content padding） |
| `common/AppButton` | 无（直接用 `.el-button` 主题覆盖） | ⚠️ **建议不新增**：EP 主题覆盖已生效，再包一层是冗余 |
| `common/AppModal` / `AppDrawer` | 无（用 `.el-dialog` / `.el-drawer` 主题覆盖） | ⚠️ 同上，视 §十九 弹窗一致性要求决定 |
| `common/AppLoading` | **无** | ➕ **必增** |
| `common/AppConfirm` | 部分（`utils/confirm.ts` 函数式） | ✅ 已有函数式封装，够用 |
| `data/DataTable` | **无**（17 页各自写 `el-table`） | ➕ **必增**（收益最大：统一行高/空态/loading/分页） |
| `data/DataFilter` | **无**（各页各自写 filter 区） | ➕ **必增**（收益大：统一间距/折叠/重置） |
| `data/ProgressBar` | **无**（§十二/§十八 要求「7/7」「100%」进度） | ➕ **必增** |
| `data/StatusBadge` | ✅ 有 | 保留 |
| `data/StatCard` | ✅ 有 | 保留 |
| `charts/LineChart` 等 | 间接（`LimsChart` + `chartOptions` 工厂） | ✅ **建议不拆**：工厂模式比 4 个组件文件更灵活 |
| `business/*`（SampleCard / InspectionTimeline / ResultTable / ReportPreview） | `components/report/Report{Page1,Page2,Cover}` 已有 3 个 | ⚠️ 按需抽，不预先造 |
| `Skeleton`（§二十七 明确要求） | **无（0 处 `el-skeleton`）** | ➕ **必增**（§二十七 说「优先使用 Skeleton」） |
| `Timeline`（§二十六 操作日志） | 无 | ⚠️ 待 §十七/§二十六 定 |

### 5.4 三类表面 / 状态色令牌的完整度

- **表面**：`surface` / `surface-solid` / `surface-hover` / `surface-2` / `surface-3` / `shell` —— **6 档**，充足
- **状态软色**：success/warning/danger/info/purple 各带 `-soft` + `-line` —— **完整 10 个**
- **结论三态**（对齐判定引擎）：`--lims-conclusion-{pass,fail,pending,blank}` —— **已有，且语义正确**（`blank` 区分「未录入」）

> 💡 **重要发现**：现有令牌体系里 `--lims-conclusion-blank`（未录入，紫）与 `--lims-conclusion-pending`（待判定，橙）
> **是两个不同颜色**——这与项目 `T-912` 的裁决（「未录入 ≠ 待判定」）**完全对应**。
> **新色板必须保留这个语义区分**（规范 §五 只给了 Purple，未细分，**以本项目业务为准**，符合提示词末尾「优先级 1 = 现有真实业务逻辑」）。

---

## 六、现有 UI 问题清单（提示词 §二 第 8 点要求）

### 🔴 P0 — 会导致「看起来不像同一个产品」

| # | 问题 | 证据 | 影响面 |
|---|---|---|---|
| 1 | **色板整体偏离规范** | 底 `#08090b` vs `#080C10`；品牌 `#00f5d4` vs `#18D6C5`；5 个语义色全偏 | 全站 |
| 2 | **无「背景层级」概念**：Card 用半透明玻璃（`.66`）叠在纯黑上，靠 blur 区分层级 | `tokens.css:26` `--lims-surface: rgba(18,21,26,0.66)` | 全站 —— 规范 §五 明确要求「**必须通过不同深色层级建立空间关系**」（Sidebar `#0D1218` / Header `#10161D` / Card `#131A22` 三层实色） |
| 3 | **Card 普遍带 shadow** | `--lims-shadow-card` 用于所有 `.app-card--panel` | §三十一 明确「普通 Card：背景 + Border 即可；Shadow 只用于 Dropdown/Modal/Drawer/Floating Panel」 |
| 4 | **Header 高 60px**（规范 64px） | `--lims-header-h: 60px` | 全站布局 |
| 5 | **无 Skeleton**（0 处） | `grep el-skeleton` = 0 | §二十七「Loading 优先使用 Skeleton；不要整个页面出现一个巨大 Loading」 |
| 6 | 🔴 **Dashboard 使用硬编码假数据** | `dashboard/index.vue:53-58` KPI 写死 `{待处理任务:12, 检测中样品:38, 待审核报告:7, 异常样品:2}`；`:67` 最近任务 `mock` 数组 | **违反用户 Checklist ⑮「Dashboard 有真实业务数据」+ 提示词 §三十五 第 15 条「禁止使用假数据破坏已有业务逻辑」** |

### 🔴 P0-6 专项：Dashboard 假数据（本轮最严重的实质缺陷）

**现象**：`views/dashboard/index.vue` 完全没有 import 任何 `api/*`，KPI 与「最近任务」全部是源码里的字面量。

**为什么这是 P0 而非 P2**：
1. 用户 Checklist 第 ⑮ 项明确要求「Dashboard 有真实业务数据」；
2. 提示词 §三十五「禁止事项」第 15 条明令禁止假数据；
3. **本项目后端已有现成的真实接口**——假数据不是「不得已」，而是**遗留未接线**。

**现成可用接口（无需新增任何后端代码）**：

| 接口 | 返回 | 可直接喂给 |
|---|---|---|
| `GET /stat/overview` | `totalSamples` / `testingSamples` / `completedSamples` / `reportCount` / `qualifiedSamples` / `unqualifiedSamples` / `qualifiedRate` / `pendingSamples` | KPI 行（**待处理 = totalSamples - completedSamples**、检测中 = `testingSamples`、待审核 = `reportCount`、异常 = `unqualifiedSamples` + `pendingSamples`） |
| `GET /stat/sample-status` | `StatNameValue[]` | 样品状态分布饼图 |
| `GET /stat/monthly-trend?months=6` | `StatTrend[]` | 检测任务趋势折线 |
| `GET /stat/inspect-type` / `-category` / `-tester-workload` / `-dept` / `-top-clients` / `-unqualified-items` | `StatNameValue[]` | 其余图表 |

**修复方式（STEP 5 执行，纯前端）**：Dashboard 改为 `onMounted` 并行调 `Promise.all([...])`，
KPI 与图表全部接真实值；**「最近任务」若确无对应 API，则改为调 `query/testing` 的分页接口取前 5 条真实在检样品**
（`api/query.ts` 已有 `pageTestingQueryApi`），**不允许继续保留 mock 数组**。
`qualifiedRate` 为 `null` 时显示「—」而非 `0%`（沿用 T-803 已落档的第三态语义）。

### 🟠 P1 — 一致性与完整的缺口

| # | 问题 | 证据 | 影响面 |
|---|---|---|---|
| 6 | **17 个页面各自手写 `el-table`**，行高/空态/loading 靠全局 CSS 兜底 | `el-table` 用量 17 处 | 表格质感不统一（§三十） |
| 7 | **各页各自手写 filter 区**，间距/折叠/重置行为不一致 | 无 `DataFilter` | §十一/§十二 要求统一 Filter |
| 8 | **无 ProgressBar 组件**（§十二「检测进度 7/7」、§十八 同） | 无 | 样品登记 / 报告审核 |
| 9 | **`AppBreadcrumb.vue` 死代码**（0 引用） | `grep AppBreadcrumb views` = 0 | 代码卫生 |
| 10 | **3 处硬编码色值 fallback**：`#16a34a` / `#dc2626` / `#d97706` | `tester-method:619,622`、`product-lib:882,885`、`my-tasks:416`、`role:539` | 小，顺手清理 |
| 11 | **`MainLayout.vue` 1153 行**（含 sidebar + header + 通知 + 搜索 + 用户菜单） | 最大文件 | 可维护性（§三十三 建议抽 `AppSidebar`/`AppHeader`） |
| 12 | **全局注册了全部 EP 图标**（`main.ts` 循环注册 `ElementPlusIconsVue`） | `main.ts:38-40` | ⚠️ **与 T-916 的 `ICON_MAP` 白名单原则冲突**——全量注册使 tree-shake 失效 |

### 🟡 P2 — 规范要求的业务可视化增强（非缺陷）

| # | 项 | 规范 | 现状 |
|---|---|---|---|
| 13 | 样品详情「生命周期 Timeline」 | §十三 | 无独立页面 |
| 14 | 任务安排看板视图 | §十五 | 仅列表（§十五 说「不要为炫技加无意义拖拽」→ 可选） |
| 15 | 结果录入异常行高亮 | §十六「必须突出」「不要把异常隐藏在普通文字中」 | ⚠️ **实测确认缺失**：`grep row-class-name\|is-abnormal` 在 `result/index.vue` = **0 命中** → 异常行无整行视觉强调 → **STEP 5 必做** |
| 16 | 报告审核 KPI（待审核/异常报告/今日审核） | §十八 | ⚠️ **Tabs 已有**（待审核/待签发，`audit.vue:332-344`），但 **`StatCard` 0 命中 → KPI 行缺失** → STEP 5 必做 |
| 17 | Dashboard 4 张核心 StatCard + 4 张指定图表 | §十 | ⚠️ **StatCard 组件已用，但数据是假值**（见 P0-6）；图表数量亦少于规范要求的 4 张 |
| 18 | 操作日志 Timeline | §二十六 | **无页面且需后端 API** → 本轮不做 |

---

## 七、可复用部分（明确「不要重造轮子」清单）

**直接沿用，只改取值**：
1. `styles/tokens.css` —— **结构完整**，改值 + 加 3 槽位
2. `styles/element-override.css` —— EP 主题覆盖层完整（含 fixed-column 背景修复、el-tag 过渡卡死修复两个**已实测的宝贵修复**）
3. `styles/base.css` —— 极光光斑 + 网格纹理 + `.lims-glass` / `.lims-panel` / `.lims-mono` 工具类
4. `components/common/{AppCard,PageHeader,StatCard,StatusBadge,AppEmpty}.vue` —— 5 个组件保留
5. `components/common/LimsChart.vue` + `utils/chartOptions.ts` —— 图表方案保留
6. `utils/{request,download,confirm,sampleStatus}.ts` —— 不动
7. **全部 12 个 `api/*.ts`** —— 一行不改
8. **全部 19 个 view 文件的 `<script setup>` 业务逻辑** —— **一行不改**（只改 `<template>` 结构 + `<style>`）

**必须保留的两处「用血换来的」修复**（见 `element-override.css` 注释）：
- `el-tag` 过渡卡死兜底（`opacity: 0` 导致标签不可见）
- `el-table` 固定列不透明背板（`--el-table-bg-color: transparent` 的副作用，1366×768 下列重叠糊）

---

## 八、建议修改部分（按 STEP 顺序的施工图）

| STEP | 动作 | 文件 | 风险 |
|---|---|---|---|
| 2 | **替换令牌取值 + 新增 3 槽位**；新增 `.lims-surface-{sidebar,header,card,elevated}` 四层实色 | `tokens.css` | 🟢 低（改一张表） |
| 2 | 同步 EP 覆盖层的色彩变量（`--el-color-*` 系列全量重算 light-3/5/7/8/9 变体） | `element-override.css` | 🟡 中（变体需手工推导，缺失会发白） |
| 2 | 清理 3 处 hex fallback | 4 个 view | 🟢 低 |
| 3 | 抽 `AppSidebar` / `AppHeader` / `AppContainer`；Header 60→64；激活态 3px 品牌竖条 | `layouts/` + `MainLayout.vue` | 🟡 中（1153 行拆分需谨慎，**用 Edit 不用 Write**） |
| 3 | `main.ts` 图标全量注册 → 按需（与 `ICON_MAP` 统一） | `main.ts` | 🟡 中（需核对 `PageHeader` 等组件的 icon 用法） |
| 4 | 新增 `AppLoading` / `AppSkeleton` / `ProgressBar` / `DataTable` / `DataFilter` | `components/` | 🟡 中 |
| 4 | 按 §三十一 降 Card shadow | `AppCard.vue` + `element-override.css` | 🟢 低 |
| 5 | **报告审核页（第一批重点）** → 结果录入 → Dashboard → 其余 14 页 | `views/` | 🟠 中高（逐页，需逐页自测） |
| 6~10 | 三档分辨率 + 20 项 Checklist + Console 0 Error | 全站 | 🟠 中高 |

---

## 九、不可破坏项自查（对应用户 6 条红线）

| 红线 | 本项目是否会触碰 | 依据 |
|---|---|---|
| DB 表结构 | ❌ **不会** | 令牌/组件/view 均不涉及 SQL |
| 已有 API | ❌ **不会** | `api/*.ts` 全部不动 |
| API 参数 | ❌ **不会** | 同上 |
| 核心业务状态 | ❌ **不会** | 状态机在后端 `SampleStatusTransition`；前端只渲染 |
| Pinia Store 数据结构 | ❌ **不会** | `stores/auth.ts` 只在 STEP 3 读取 `navMenus`，不改结构 |
| 登录认证 / 权限体系 | ❌ **不会** | 守卫五步 / `v-permission` / 路由注册表不动 |
| 已有业务流程 | ❌ **不会** | `<script setup>` 业务逻辑零改动 |
| **例外风险评估** | §十三 样品详情（新增页）**能用现有 API 纯前端组装**；§二十六 操作日志**需新 API → 本轮不做** | — |

---

## 十、STEP 1 交付结论

1. **不需要推倒重来**。项目的令牌体系、组件抽象、页面覆盖度**已达到规范要求的 80% 结构**。
2. **STEP 2 的真正工作是「配色对齐」**：一张 `tokens.css` 表格的取值替换 + 3 个新槽位 + EP 变体重算。
3. **STEP 4 的真正工作是「补齐 5 个缺失组件」**（AppLoading / AppSkeleton / ProgressBar / DataTable / DataFilter），
   其中 **DataTable + DataFilter 收益最大**（覆盖 17 个页面的表格与筛选）。
4. **必须保留**：`--lims-conclusion-blank`（未录入）与 `--lims-conclusion-pending`（待判定）的语义区分
   —— 这是本项目业务裁决的视觉落点，**规范未涵盖，以业务为准**（提示词末尾优先级 1）。
5. **必须保留**：`el-tag` 过渡兜底、`el-table` 固定列背板两个实测修复。
6. **⚠️ 盘点发现 1 处「非视觉」实质缺陷（P0-6）**：**Dashboard 全是硬编码假数据**，
   而后端 `GET /stat/overview` 等 9 个真实接口**早已交付**。此缺陷同时违反用户 Checklist ⑮
   与提示词 §三十五 第 15 条，**优先级高于任何纯视觉工作**，STEP 5 首轮必须修复。
7. **⚠️ 盘点发现 2 处「规范要求但实测缺失」**：结果录入**无异常行高亮**（§十六 硬性要求）、
   报告审核**无 KPI 行**（§十八 硬性要求）。二者与 P0-6 并列为 STEP 5 的三个「必做实质项」。
8. **下一步**：进入 STEP 2（统一 Design Token）。

### STEP 5 必做实质项汇总（非视觉美化，缺一不可）

| 项 | 规范依据 | 用户 Checklist | 现状 |
|---|---|---|---|
| Dashboard 接真实数据 | §十；§三十五-15 | ⑮ | 🔴 全假数据 |
| 结果录入异常行高亮 | §十六 | ⑭「异常数据明显」 | 🔴 完全缺失 |
| 报告审核 KPI 行 | §十八 | ⑬「数据层级清晰」 | 🟠 Tabs 有、KPI 无 |
| Skeleton 加载态 | §二十七 | ⑨「Loading/Empty/Error 统一」 | 🟠 全站 0 处 |

---

## 附：本轮盘点用到的核查命令（可复现）

```bash
cd D:/lims/frontend/src

# 1. 页面与组件行数（识别体量与重构优先级）
find src/views src/components src/layouts -name "*.vue" -exec wc -l {} \; | sort -rn

# 2. 公共组件复用率（判断组件抽象是否被真正使用）
for c in AppCard PageHeader StatCard StatusBadge AppEmpty LimsChart AppBreadcrumb; do
  echo "$c: $(grep -rl "$c" views --include=*.vue | wc -l)"
done

# 3. 是否绕过公共组件（0 = 无绕过）
grep -rl "el-card\|el-empty" views --include=*.vue | wc -l

# 4. 缺失态检查（Skeleton 为规范硬性要求）
grep -rl "el-skeleton" views components --include=*.vue | wc -l

# 5. 真实硬编码色值（排除 Vue 的 #default 插槽语法干扰）
grep -rnE '#[0-9a-fA-F]{3,6}[;,) ]|rgba?\(' views --include=*.vue | grep -v 'var(--lims'
```

> ⚠️ **坑**：`grep "#[0-9a-fA-F]\{3,8\}"` 会把 Vue 的 `#default` 插槽语法全部误判为色值
> （实测产生 **126 条假阳性 / 仅 4 条真阳性**）。必须用 `#[0-9a-fA-F]{3,6}[;,) ]` 加后缀字符类收窄。
