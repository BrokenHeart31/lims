---
name: lims-ui-overhaul
description: |
  LIMS 前端 UI 一站式组件库重整工作流。
  何时使用：当被告知「前端 UI 优化」「结构空间优化」「/ui 升级」「保持 mine radio 氛围」等场景，
  或维护阶段发现页面缺统一头/卡/状态徽章/二次确认需要收敛。
  入口提示词：`C:\Users\Chen\Desktop\前端优化\ui提示词.txt` 同款诉求。
---

# LIMS 前端 UI 重整 Skill

> 适用：Vue 3 + Vite + TypeScript + Element Plus + Pinia 的 LIMS 类项目，
> 已有暗色玻璃氛围（参考 mine radio），要做**结构空间 + 一致性**双维度优化。

## 0. 何时不该用

- 项目是新建项目，还没有 `frontend/` ——先按 `vue3-crud-page` 搭起；
- 项目是浅色风格 ——本 skill 的暗色调令牌需重写；
- 单一页面零散美化 ——直接编辑对应文件即可，不必铺整套组件库。

## 1. 工作流（7 步）

```
1. 探查现状（tokens / styles / layouts / 各 view）
2. 扩展 tokens.css（spacing + tone + header）
3. 改 element-override.css（行高 / 间距 / 圆角 / 颜色）
4. 建公共组件库（6 件套）
5. 建工具层（confirm / status-map）
6. 重写 MainLayout + Dashboard
7. 批改 7~8 个业务页（每页 5 步：header / card / badge / empty / confirm）
```

每一步都要：
- 跑 `npm run lint -- --fix` 收敛格式；
- 跑 `npm run build` 阻塞错；
- 用 `git status --short` 自查；

## 2. 关键约束（违反即失败）

1. **不改后端任何文件**——本 skill 只动 frontend/；
2. **不引入新依赖**——element-plus + scoped CSS 足够；尤其拒绝 tailwind / naive-ui / unocss；
3. **保留 mine radio 暗色玻璃氛围**——品牌色、玻璃背景、暗色调一律不动；
4. **保持业务逻辑、API、权限不动**——只动视觉壳与一致性；
5. **所有 confirm 走 `confirm(...) / askConfirm(...)`**——禁止散落 ElMessageBox.confirm(...).then()；
6. **StatusBadge tone 集合严格 8 个**（success/warning/danger/info/purple/pending/blank/neutral）；
7. **状态机 / 判定语义 / 领域枚举不动**——遵循 AGENTS 第 7 章「七阶段 + 状态流转白名单」。

## 3. 公共组件模板（缺哪抄哪）

### 3.1 PageHeader

```vue
<script setup lang="ts">
import type { Component } from 'vue'
defineProps<{ title: string; subtitle?: string; icon?: string | Component }>()
</script>

<template>
  <header class="page-header lims-glass">
    <div class="page-header__main">
      <div v-if="icon" class="page-header__icon">
        <el-icon :size="22"><component :is="icon" /></el-icon>
      </div>
      <div class="page-header__text">
        <div v-if="$slots.breadcrumb" class="page-header__crumb">
          <slot name="breadcrumb" />
        </div>
        <div class="page-header__title-row">
          <h1 class="page-header__title">{{ title }}</h1>
          <span v-if="subtitle" class="page-header__sub">{{ subtitle }}</span>
        </div>
      </div>
    </div>
    <div v-if="$slots.default" class="page-header__actions">
      <slot />
    </div>
  </header>
</template>
```

### 3.2 AppCard（glass / panel / flat 三态）

```vue
<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'glass' | 'panel' | 'flat'
    padding?: string | number
    accent?: boolean
    hoverable?: boolean
  }>(),
  { variant: 'panel', padding: 20, accent: false, hoverable: false },
)
</script>
<template>
  <section class="app-card" :class="[`app-card--${variant}`,
    {'app-card--accent': accent,'app-card--hoverable': hoverable}]"
    :style="{padding: typeof padding==='number'?`${padding}px`:padding}">
    <slot />
  </section>
</template>
```

### 3.3 StatusBadge（8 tone）

```ts
tone: 'success' | 'warning' | 'danger' | 'info' | 'purple' | 'pending' | 'blank' | 'neutral'
```

每个 tone 配置 `--lims-*-soft` / `--lims-*-line` 两个色变量，徽章圆点 + 1px 描边圆角胶囊。

### 3.4 AppEmpty（icon + title + hint + 操作 slot）

允许覆盖 icon 与底部操作。默认空态提示文案统一写在「空数据 → 下一步建议」。

### 3.5 StatCard（KPI 卡）

数据 + 后缀 + trend chip + icon + 副标题；可含 sparkline 槽。

### 3.6 AppBreadcrumb（面包屑封装）

接 `<el-breadcrumb>`，传 separator、items。

## 4. 工具（utils/confirm.ts）

```ts
export async function confirm(opts: ConfirmOptions): Promise<string | null>  // 选项对象风格
export async function confirmReturn(reasonLabel?: string): Promise<string | null> // 退回原因必填
export async function askConfirm(
  message: string, title = '确认',
  opts: { type?: 'primary' | 'success' | 'warning' | 'danger' } = {},
): Promise<boolean>  // 快速 boolean，返回是否按下「确认」
```

**用法**：
```ts
if (!(await askConfirm('将审核通过，存在异常项，确定吗？', '审核确认', { type: 'warning' }))) return
```

## 5. PageHeader 调用模式（固定）

```vue
<PageHeader
  title="结果录入"
  subtitle="按检测单项录入检验结果，单项结论由判定引擎自动生成"
  icon="Promotion"
>
  <template #breadcrumb>
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">工作台</el-breadcrumb-item>
      <el-breadcrumb-item>实验室业务</el-breadcrumb-item>
      <el-breadcrumb-item>结果录入</el-breadcrumb-item>
    </el-breadcrumb>
  </template>
  <el-button :icon="Refresh" @click="loadPending">刷新</el-button>
</PageHeader>
```

## 6. tokens.css 必须扩展的变量

```css
/* spacing scale（统一间距，替换散落的 --lims-r-*） */
--lims-sp-1: 4px;  --lims-sp-2: 8px;   --lims-sp-3: 12px;
--lims-sp-4: 16px; --lims-sp-5: 24px;  --lims-sp-6: 32px;

/* header */
--lims-page-header-py: 22px;
--lims-page-title-size: 24px;
--lims-page-sub-size: 13px;
--lims-breadcrumb-fg: var(--lims-muted);

/* status tone 双色（每个 tone = soft + line） */
--lims-success-soft: rgba(34,197,94,.12);    --lims-success-line: rgba(34,197,94,.4);
--lims-warning-soft: rgba(245,158,11,.12);   --lims-warning-line: rgba(245,158,11,.4);
--lims-danger-soft: rgba(239,68,68,.12);     --lims-danger-line: rgba(239,68,68,.4);
--lims-info-soft: rgba(14,165,160,.12);      --lims-info-line: rgba(14,165,160,.4);
--lims-purple-soft: rgba(167,139,250,.14);   --lims-purple-line: rgba(167,139,250,.42);
```

## 7. element-override.css 必改项

```css
:root {
  --el-table-row-height: 44px;
  --el-form-item-margin-bottom: 18px;
  --el-table-border-color: var(--lims-hair);
  --el-table-header-bg-color: rgba(255,255,255,.035);
  --el-table-row-hover-bg-color: rgba(var(--lims-accent-rgb), .06);
  --el-button-border-radius: var(--lims-r-sm);
}
```

## 8. 业务页改造清单（逐页 5 步）

| 步骤 | 旧 | 新 |
|---|---|---|
| 1 | `<header class="page-head">` | `<PageHeader title=... subtitle=... :icon=...>` + breadcrumb slot |
| 2 | `<el-card class="glass-card">` | `<AppCard variant="panel" :padding="20">` |
| 3 | `<el-tag type="success">label</el-tag>` | `<StatusBadge tone="success" size="sm">label</StatusBadge>` |
| 4 | `<template #empty><span class="empty-tip">...</span></template>` | `<template #empty><AppEmpty description="..." /></template>` |
| 5 | `await ElMessageBox.confirm(...).then(...)` | `if (!(await askConfirm(...))) return` |

## 9. 验证门禁（不通过不算完）

```bash
cd frontend
npm run lint    # 必须 0 error / 0 warning（用 --fix 自动修）
npm run build   # 必须 vue-tsc --noEmit 通过 + vite build 成功
```

最大耗时：lint + build ≈ 30s。

## 10. 提交模板

```bash
git add frontend/
git status --short   # 必须仅含 frontend/** 且无 .env/.lock 误入
git commit -m 'feat(ui): T-913 重整前端组件库 + 结构空间优化'
```

## 11. 何时升级本 skill

- element-plus 升大版本（>= 3.4 视觉重构）→ 重写 element-override.css
- 引入新依赖（如真用了 vueuse）→ 在「约束」一节记录
- 发现新组件反复写 → 提取到 components/common 并写 prop 模板

## 12. 参考资料

- 知识库 `docs/knowledge/2026-09-11-ui-design-mineradio-research.md`（mine radio 设计调研）
- 知识库 `docs/knowledge/2026-09-12-ui-component-library.md`（本轮沉淀组件决策）
- journal `docs/journal/2026-09-12-glm-ui-overhaul.md`（本轮完整决策 + 踩坑记录）
