<script setup lang="ts">
/**
 * AppCard — 统一卡片容器
 * ----------------------------------------------------------------------------
 * 三种 variant：
 *   - glass      : 玻璃质感（默认，用于氛围卡 / 标题卡）
 *   - panel      : 数据密集区（表格 / 表单 / 详情，弱化模糊）
 *   - flat       : 平面容器（无边框阴影，仅底色差异，用于分组容器）
 * 可选 padding（默认 20px）；可附带 hoverable / accent 等。
 */
withDefaults(
  defineProps<{
    variant?: 'glass' | 'panel' | 'flat'
    padding?: string | number
    hoverable?: boolean
    accent?: boolean
  }>(),
  { variant: 'glass', padding: '20px', hoverable: false, accent: false },
)
</script>

<template>
  <section
    class="app-card"
    :class="[
      `app-card--${variant}`,
      { 'app-card--hoverable': hoverable, 'app-card--accent': accent },
    ]"
    :style="{ padding: typeof padding === 'number' ? `${padding}px` : padding }"
  >
    <slot />
  </section>
</template>

<style scoped>
.app-card {
  position: relative;
  border-radius: var(--lims-r-card);
  transition:
    border-color var(--lims-dur) var(--lims-ease-out),
    box-shadow var(--lims-dur) var(--lims-ease-out),
    transform var(--lims-dur) var(--lims-ease-out),
    background var(--lims-dur) var(--lims-ease-out);
}

/* glass —— 玻璃质感（氛围卡 / 标题卡 / 工作台 KPI） */
.app-card--glass {
  border: 1px solid var(--lims-glass-border-soft);
  background: var(--lims-glass-bg);
  box-shadow: var(--lims-glass-shadow);
  backdrop-filter: var(--lims-glass-blur);
  -webkit-backdrop-filter: var(--lims-glass-blur);
}

/* panel —— 数据密集区（表格 / 表单 / 详情） */
.app-card--panel {
  border: 1px solid var(--lims-hair);
  background: var(--lims-surface);
  box-shadow: var(--lims-shadow-card);
}

/* flat —— 平面容器（次级容器，无视觉重量） */
.app-card--flat {
  border: 1px solid var(--lims-hair);
  background: transparent;
}

/* hoverable —— 鼠标悬浮时提亮 */
.app-card--hoverable:hover {
  border-color: var(--lims-glass-border);
  box-shadow: var(--lims-glass-shadow-focus);
  transform: translateY(-2px);
}

/* accent —— 顶部品牌色描边（用于重点强调卡） */
.app-card--accent::before {
  position: absolute;
  top: 0;
  left: 12%;
  width: 76%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--lims-accent), transparent);
  content: '';
  opacity: 0.65;
}
</style>