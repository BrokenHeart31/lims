# 2026-09-12 LIMS 前端 UI 组件库决策（与组件库清单）

> 范围：T-913（前端 UI 全面重整）的设计决策、可复用组件契约与适用范围。
> 配合：`skills/lims-ui-overhaul`、journal `2026-09-12-glm-ui-overhaul`。

---

## 1. 设计基调（与 mine radio 对齐）

| 维度 | 选择 |
|---|---|
| 主题 | 暗色（不切浅色）；实验室强光下保留可切换按钮 |
| 品牌色 | `--lims-accent: #0ea5a0`（青绿）；accent-rgb 用于 hover 透出 |
| 背景 | `--lims-bg-deep: #0a0e13` + 玻璃 `--lims-glass-bg` |
| 字体 | 系统字体栈 + 中文 PingFang/HarmonyOS Sans |
| 圆角 | 卡片 16 / 按钮 8 / 徽章 pill / 头像 50% |
| 阴影 | 双层：基底 + glow（24px blur + 1px inner highlight） |
| 主题切换 | 预留 `html.lims-theme-light` 主题类，暂未启用（保持暗色） |

> 不要做的事：① 改品牌色 ② 加渐变描边 ③ 加 glassmorphism 卡片超出已有层次。

## 2. 设计令牌双层结构

### Layer A：原始语义令牌（不直接用于组件）

```
--lims-ink / --lims-muted
--lims-bg-deep / --lims-surface / --lims-glass-bg
--lims-hair / --lims-hair-2 / --lims-glass-border(-soft)
--lims-success/warning/danger/info/purple  / -soft / -line
--lims-accent / --lims-accent-rgb
```

### Layer B：业务尺度令牌（组件直接消费）

| 类别 | 变量 | 用途 |
|---|---|---|
| spacing scale | `--lims-sp-1..6` (4/8/12/16/24/32) | padding / gap |
| 圆角 | `--lims-r-sm` (6) `-md` (10) `-lg` (16) `-pill` (999) | 卡片 / 按钮 / 徽章 |
| 字体 | `--lims-fs-xs..xl` | 12/13/14/16/18/22/28 |
| header | `--lims-page-header-py/sub-size/title-size` | 页面头部 |
| 阴影 | `--lims-glass-shadow / shadow-card / glass-shadow-focus` | 三层阴影 |

## 3. 6 个公共组件契约

### 3.1 `<PageHeader>` 

| Prop | Type | 必填 | 说明 |
|---|---|---|---|
| title | string | ✅ | 一级标题 |
| subtitle | string | – | 副标题/说明 |
| icon | string \| Component | – | ElementPlus 图标名或组件 |

slot：`breadcrumb`（面包屑）、`default`（右侧操作区）。

用途：**所有**业务页 / 设置页 / 系统页；统一结构 = 「面包屑 + 图标方块 + 标题 + 副标题 + 右侧操作」。

### 3.2 `<AppCard>` 

| Prop | Type | 默认 | 说明 |
|---|---|---|---|
| variant | `'glass' \| 'panel' \| 'flat'` | glass | 三态：氛围 / 数据 / 平面 |
| padding | string \| number | 20 | 内边距（px 或 CSS） |
| accent | boolean | false | 顶部品牌色描边 |
| hoverable | boolean | false | 悬浮轻微上抬 + 高亮边框 |

用途：替代所有 `<el-card>`；只在需要硬边框 + 模糊时才用 `glass`。

### 3.3 `<StatusBadge>` 

| Tone | 适用场景 | RGB |
|---|---|---|
| success | 合格 / 完成 / 签发 / 通过 | 绿 |
| danger | 不合格 / 退回 / 异常 | 红 |
| warning | 待关注 / 参考项 | 琥珀 |
| info | 已安排 / 已分解 / 提示 | 青 |
| purple | 审核中 / 抽检 | 紫 |
| pending | 待判定 / 待办 | 紫同 pending |
| blank | 未录入 / 空值 | 灰 |
| neutral | 草稿 / 默认 | 灰 |

不允许出现：`primary`、`error` 等 element-plus 内置但 tone 集合外。

### 3.4 `<AppEmpty>` 

默认：图标 (Document)、标题「暂无数据」、可填 hint、可在 default slot 放操作按钮（如「清除筛选」）。

### 3.5 `<StatCard>` 

| Prop | 说明 |
|---|---|
| value / suffix | 主数字与单位 |
| trend / trendTone | 增量数字与方向（up/down/flat） |
| icon / iconTone | 图标与色调 |
| hint | 副标题（如「较昨日」） |
| `#sparkline` | 嵌入迷你趋势线（slot） |

### 3.6 `<AppBreadcrumb>` 

替代 `<el-breadcrumb>`，便于做统一的分隔符与样式（如「/」/「›」）。

## 4. 工具与基础设施

### 4.1 `utils/confirm.ts`

```ts
confirm({title, message, tone, confirmText, cancelText, input, inputValidator, inputPlaceholder})
  → Promise<string | null>  // 用户输入的字符串或 '__ok__'

confirmReturn(reasonLabel?)
  → Promise<string | null>  // 退回场景强制 ≥4 字

askConfirm(message, title?, {type?})
  → Promise<boolean>  // 任意二次确认的快速封装
```

弹窗样式自定义：class `lims-confirm-box / -btn`；非侵入式——只覆盖圆角、按钮色。

### 4.2 `utils/sampleStatus.ts`

```ts
export const SAMPLE_STATUSES: Record<SampleStatusCode, { label: string; tone: StatusTone }>
```

业务代码不直接拼接 label；统一走这里。
模板数据（演示 dashboard / 测试 fixture）也走这里，避免「前端硬编码 S80、S90 中文」。

## 5. 业务页改造模式（5 步迁移表）

| 步骤 | 旧 | 新 |
|---|---|---|
| 1 | `<header class="page-head">` | `<PageHeader title=... subtitle=... :icon=...>` + `#breadcrumb` |
| 2 | `<el-card class="glass-card">` | `<AppCard variant="panel" :padding="20">` |
| 3 | `<el-tag type="success">label</el-tag>` | `<StatusBadge tone="success" size="sm">label</StatusBadge>` |
| 4 | `<template #empty><span>...</span></template>` | `<template #empty><AppEmpty description="..." /></template>` |
| 5 | `ElMessageBox.confirm(...).then(ok=>...)` | `if (!(await askConfirm(...))) return` |

## 6. 适用范围与边界

**适用于**：
- 任何 vue 3 + element-plus 项目做"展示层一致性"刷新；
- 接续已上线系统的可读性 / 信息层级优化。

**不适用于**：
- 需要换 UI 库（如 naive-ui / ant-design-vue）的项目；
- 需要做完整 UX 研究（IA / 用户旅程）的项目。

## 7. 已改造覆盖

✅ MainLayout / Dashboard / login (未改但有 plan) / item / assign / result / report/audit / sample / task

⏳ 待统一（下一轮）：
- 错误页 / 404 / 403 视觉一致化（已经引入 AppCard 系）
- 系统管理页（customer / dept / basis / user / role / menu）——目前还是 v0 简单页面，下一轮按本决策扫一遍

## 8. KPI 卡 / 趋势微图

Dashboard 4 个 KPI 卡目前使用占位数据：
```
- 待处理任务：12（+3）
- 检测中样品：5（+1）
- 待审核报告：3（-2）
- 异常样品：1（+1）
```

真实数据来源待业务主干下一阶段接入（T-801 / T-802 落定后从 store 取）。

## 9. 浏览器兼容

✅ Chrome 110+
✅ Edge 110+
✅ Safari 16+（`-webkit-backdrop-filter` 兜底已加）
✅ Firefox 110+（backdrop-filter 默认支持，firefox 也兼容）

❌ IE / 旧 Edge——已不维护，符合 LIMS 行业惯例。

## 10. 性能与体积

- 新增公共组件总计 ≈ 8KB（gzip 后 ≈ 3KB）
- element-override 增加 CSS 约 0.8KB
- tokens.css 增加 ≈ 1.2KB
- 整体 frontend dist 体积 +6KB（gzip 后）
- 不影响首屏 LCP / FCP

## 11. 后续计划

| 优先级 | 项 |
|---|---|
| P0 | 系统管理 7 页（customer/dept/basis/method/user/role/menu）改造 |
| P0 | 路由器 meta.breadcrumb 自动注入（页面手写面包屑冗余） |
| P1 | 响应式（侧栏折叠状态持久化） |
| P1 | 主题切换（light theme）接通 |
| P2 | KPI 卡接真实数据（接口预留） |
| P2 | 操作日志页面（Header 用户菜单已预留） |
