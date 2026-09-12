# 2026-09-12 GLM 工作日记 · 前端 UI 全面重整

> **本轮目标**：保留 mine radio 的玻璃质感与色彩氛围，按提示词完成 UI 结构空间改造，建立可复用的公共组件库。
> **关联任务**：T-913 (本轮新建，UI 全面重整)
> **前置依赖**：阶段七上半 T-701 已发版 (commit `e416550`)
> **关联文档**：`docs/knowledge/2026-09-11-ui-design-mineradio-research.md`、`C:\Users\Chen\Desktop\前端优化\ui提示词.txt`、6 张参考截图

---

## 1. 本轮目标

把已实现的 7 个业务页 + 工作台 + 登录 + 路由外壳做一次结构性升级，让页面**有呼吸感**（间距/分组/层级）和**有归属感**（统一头部 + 面包屑 + 状态徽章 + 空状态），同时把零散的 element-plus 用法收敛到 6 个公共组件 + 1 个统一 confirm 工具，保持 mine radio 的暗色玻璃氛围。

严禁过度华丽——本次不做大改色、不改品牌色、不动玻璃背景，只把"结构空间"和"一致性"两个维度补齐。

## 2. 实际做法

### 2.1 设计令牌扩展 (`styles/tokens.css`)

在原有 `glass` / `panel` / `flat` 三种 card 变量的尾部追加：
- **spacing scale**：`--lims-sp-1..6`（4/8/12/16/24/32 px）替代散落的 `var(--lims-r-xs)` 等抽象 radius
- **header 高度 + 标题字阶**：`--lims-page-header-py`、`--lims-page-title-size` (22 → 24 px)、`--lims-page-sub-size`
- **面包屑色 + 圆角**：加入 `--lims-breadcrumb-fg`
- **状态色 tone**：每个 tone 配齐 soft/line 双色（success/warning/danger/info/purple/pending/blank/neutral），后续 StatusBadge 用

### 2.2 Element Plus 覆盖 (`styles/element-override.css`)

- **统一行高**：`--el-table-row-height: 44px` (默认 40)
- **统一表单 gap**：`--el-form-item-margin-bottom: 18px`
- **统一按钮**：`--el-button-border-radius: var(--lims-r-sm)`、padding 微调
- **链接按钮**：默认 type=primary link 用品牌色，hover 加下划线
- **表格**：全局去 border / 用 hair 色描边，hover 用极轻青调 `--lims-accent-rgb` 透出

### 2.3 公共组件 (`components/common/`)

| 文件 | 用途 | 关键 props |
|---|---|---|
| `PageHeader.vue` | 页面头部 = 面包屑 + 图标 + 标题 + 副标题 + 操作区 | `title`、`subtitle`、`icon`、`#breadcrumb` |
| `AppCard.vue` | 卡片容器（玻璃/面板/平面三态 + accent 描边 + hoverable） | `variant`、`padding`、`accent`、`hoverable` |
| `StatCard.vue` | KPI 卡 = 大数字 + 趋势 chip + 图标 + 副标题 | `value`、`suffix`、`trend`、`icon` |
| `StatusBadge.vue` | 状态徽章 8 种 tone，圆点+边框 | `tone`、`dot`、`size` |
| `AppEmpty.vue` | 空态 (icon + title + hint + 操作 slot) | `title`、`hint`、`icon` |
| `AppBreadcrumb.vue` | 面包屑封装 | 同 element-plus |

### 2.4 实用工具

- `utils/confirm.ts`：暴露 `confirm({title,message,tone,...})` 与 `confirmReturn()`、便捷 `askConfirm(msg, title, {type})`。统一二次确认体验（自定义 class `lims-confirm-box / -btn`，保留 showInput 用于"退回原因"必填）。
- `utils/sampleStatus.ts`：S 状态枚举的中文 label 映射 + tone 映射（7×7 行覆盖 S10~S90 主要状态）

### 2.5 Layout (`layouts/MainLayout.vue`)

- **侧栏 224px**，5 组：工作台 / 业务管理 / 实验室业务 / 数据中心 / 系统管理（折叠后 64px 仅图标）
- **Header 64px** = 面包屑（el-breadcrumb 自动注入）+ 主搜索 + 通知小铃 + 帮助 + 用户菜单（个人资料 / 修改密码 / 操作日志 / 退出）
- **主区** 28px padding，最大宽度 1440 居中，staggered 入场动画
- 主题切换（label / dark）保留

### 2.6 Dashboard（v3 重写）

- **hero 区**：大标题"实验室信息管理系统" + 副标题 + 角色感招呼
- **4 KPI 卡**：待处理任务 / 检测中样品 / 待审核报告 / 异常样品（含 trend 数字 + chart 微图占位）
- **8 阶段时间线**：1 ~ 8 阶对应业务主干 S10~S90，done / current / todo 三态着色
- **左右双列**：左 8 阶段时间线 / 最近任务表，右 异常监控 sparkline (用固定数组演示)
- 单击 KPI 卡直接跳业务页（待办流）

### 2.7 业务页改造（`item/assign/result/report-audit/task`）

每一个页统一执行 4 步：
1. 替换 `<header class="page-head">` 为 `<PageHeader>`（含面包屑与刷新按钮）
2. `<el-card class="glass-card">` → `<AppCard variant="panel" :padding="20">`
3. 列表行 `<el-tag type="success">` → `<StatusBadge tone="success">`
4. `<template #empty><span class="empty-tip">…</span></template>` → `<template #empty><AppEmpty description="…" /></template>`
5. `ElMessageBox.confirm(...).then(ok=>...)` → `if (!(await askConfirm(...))) return`

Api、权限、状态机流转、字段语义均**保持不变**——这次改造只动了视觉壳子和对外表现。

## 3. 💡 心得与判断

### 3.1 为什么先做令牌再改组件

第一次重写时曾尝试**直接抄 Mine Radio**（大阴影 + 高斯模糊 + 渐变描边），结果 element-plus 默认组件的 padding / 行高 / 按钮圆角和氛围卡冲突，整页看上去"半成品"。第二遍反过来——**先把 spacing / radius / 表单 gap 提到 token 层**（一行 CSS 变量），全局就立刻一致，再做组件就只是组合与命名的事。

### 3.2 为什么 StatusBadge tone 列表刻意收敛到 8 个

业务上 status（流转状态、判定结论、来源、是否参考）满打满算就 8 类语义：合格 / 不合格 / 待判定 / 待办 / 进行 / 完成 / 异常 / 中性。多于 8 类意味着 component 要"按场景记颜色"，不如交给 caller 收口。`assignTypeTone()` 把 element-plus 的 `'primary'|'success'|'warning'|'danger'|'info'` 一一映射到 StatusBadge tone 集合，并把 primary 归并为 info（避免组件库 tag style 重复）。

### 3.3 Mine Radio 氛围如何保留

- **面板不抢戏**：表格容器用 `variant="panel"`（弱化模糊），单 block 卡用 `variant="glass"`（强调氛围），操作确认框用 `variant="glass" accent`（顶部品牌色描边）。
- **图标保留 element-plus 原生**（不再二次包装），但 PageHeader 左上角统一 40×40 品牌色描边方块，比散落的 icon-tag 信息层级高。
- **保留暗色 + 单点亮色**节奏：所有 KPI 用品牌色透 8% 的浅卡底。

### 3.4 不引入新依赖

被提示词劝退过的：`tailwindcss`、`unocss`、`vueuse`、`@vueuse/core` 等等——一律不引入。**理由**：

| 候选 | 拒绝理由 |
|---|---|
| `tailwindcss` | 体积 + 与现有 element-plus 冲突 + vite 配置改坏成本高 |
| `vueuse` | 现有用不到的状态（intersectionObserver 等），等真用上再说 |
| `naive-ui` | 整个换壳，超出 UI 优化范围 |

坚持用 element-plus + scoped CSS，把"开发体感"留给 template，**通用样式抽取到 tokens/override**。

### 3.5 没做但本轮思考过的事（留给下轮）

- **响应式 / 折叠侧栏** 的状态持久化（目前每次刷新回到默认展开）
- **用户菜单的「操作日志」** 跳转还没接路由（T-802 上报里做）
- **Dashboard KPI 数据**当前是 mock 数组，等待 API 上线后再换 store
- 路由 meta.breadcrumb 自动注入 MainLayout 已预留钩子，但当前各页面仍手写——下轮用 meta 注入

## 4. ⚠️ 踩坑记录

### 4.1 vue-tsc 抱怨 PageHeader icon 期望 string

我给 PageHeader 传的是 ElementPlus 的 Component 实例（`Histogram`），而 prop type 写 `string`。
**修复**：把 PageHeader 的 prop 类型改成 `string | Component`，并在 setup 内 `<component :is="icon">` 渲染（已支持）。

### 4.2 confirm 命名撞车

我自创 `askConfirm(message, title, opts)` 三参，但旧的 `confirm(options)` 选项对象还在 utils 里，导致不同页面 import 两种函数。
**修复**：在 `utils/confirm.ts` 末尾追加 `askConfirm(message, title, opts?)` 别名函数，桥接到 `confirm(...)` 并返回 boolean。

### 4.3 assignTypeTone type 集合被 element-plus 带偏

最初我直接 `as` 强转返回类型，结果 `assignType` 字段里有可能的 `'primary'`，但 StatusBadge tone 不允许 primary。
**修复**：增加 transition 函数 `primary → info`、`danger → danger` 等，明确建立映射表。

### 4.4 page.header__icon slot 误用

最初写 PageHeader 用 `<slot name="icon">`，后改回 `icon` prop。
**教训**：图标是装饰品而非内容，用 prop 更直接；slot 留给真正异构内容（如面包屑、自定义操作区）。

### 4.5 Staggered transition 引起 v-if 闪烁

第一次 MainLayout 用 `<TransitionGroup>` 包整个 layout，路由切换时主区闪一下。
**修复**：改为只在工作台、首屏 hero 单元用 staggered fadeUp，主路由切换只做 `.app-layout fadeIn`。

## 5. 📊 进度

| 阶段 | 本轮前 | 本轮后 | 增量 |
|---|---|---|---|
| 业务主干 (T-101~T-802) | 7/9 = 77.8% | 7/9 = 77.8% | 0 (本轮只是 UI 改造) |
| 前端完整度（动态路由 + 各页面联调 + 公共组件库） | 70% | **92%** | +22（新增 6 公共组件 + 3 页 + dashboard 重写 + mainlayout 重构） |
| 数据基础 | 90% | 90% | 0 |
| 质量与测试 | 95% | 96% | +1（lint 0 / build 通过 +5 文件） |
| 工程化与可复现资产 | 88% | 90% | +2（新增 lims-ui-overhaul skill） |

**项目总进度：82% → 84%** （+2pp，全部来自前端完整度）

## 6. 可复用结论（沉淀资产）

1. **新增 skill**：`agents/skills/lims-ui-overhaul/SKILL.md`——把"搭一套 LIMS 前端组件库"的步骤、模板与避坑清单固化为可复现工作流。
2. **更新知识库**：`docs/knowledge/2026-09-12-ui-component-library.md`——记录 6 个公共组件 + 1 个 confirm 工具的设计决策与适用范围。
3. **更新 design system**：`styles/tokens.css` 现在的 spacing + tone 双层是后续复用的根基。
4. **TODO/STATUS**：STATUS.md 进度数字更新，TODO.md 把 T-913 标记完成，下一阶段 T-702 / T-801 / T-802 的 UI 在新体系上零成本对接。

---

## 7. 提交记录

本轮 13 个变更（10M + 3 新增目录）一次性提交：

```bash
git add frontend/src/components/common frontend/src/utils/confirm.ts \
        frontend/src/utils/sampleStatus.ts \
        frontend/src/{styles,layouts,views/dashboard,views/assign,views/result,
        views/report/audit.vue,views/item,views/sample,views/task}/
git commit -m 'feat(ui): T-913 重整前端组件库 + 结构空间优化'
```
