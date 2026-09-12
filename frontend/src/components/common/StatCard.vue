<script setup lang="ts">
/**
 * StatCard — KPI 数值卡（提示词 §十）
 * ----------------------------------------------------------------------------
 * 结构：图标（可选）+ 大数值 + 标签 + 同比/环比 + 微型图表（可选）
 * 用法：
 *   <StatCard label="待审核" :value="12" trend="+3" trend-tone="up" icon="Bell" />
 *   <StatCard label="合格率" :value="98.2" suffix="%" trend="-1.2" trend-tone="down" />
 */
import type { Component } from 'vue'

defineProps<{
  /** 标签 */
  label: string
  /** 主数值 */
  value: number | string
  /** 数值后缀（%/条/项） */
  suffix?: string
  /** 同比/环比文字（"+3" / "-12%"） */
  trend?: string
  /** 同比方向 */
  trendTone?: 'up' | 'down' | 'flat'
  /** 图标（element-plus icon 名 或已注册的组件） */
  icon?: string | Component
  /** 图标色调（控制左侧图标色） */
  iconTone?: 'accent' | 'success' | 'warning' | 'danger' | 'info' | 'purple'
  /** 提示文字（右下角解释） */
  hint?: string
}>()
</script>

<template>
  <div class="stat-card app-card app-card--glass app-card--hoverable">
    <div class="stat-card__top">
      <span
        v-if="label"
        class="stat-card__label"
      >{{ label }}</span>
      <span
        v-if="icon"
        class="stat-card__icon"
        :class="`is-${iconTone ?? 'accent'}`"
        aria-hidden="true"
      >
        <el-icon :size="18">
          <component :is="icon" />
        </el-icon>
      </span>
    </div>

    <div class="stat-card__value">
      <span class="stat-card__num lims-mono">{{ value }}</span>
      <span
        v-if="suffix"
        class="stat-card__suffix"
      >{{ suffix }}</span>
    </div>

    <div class="stat-card__foot">
      <span
        v-if="trend"
        class="stat-card__trend"
        :class="`is-${trendTone ?? 'flat'}`"
      >
        <template v-if="trendTone === 'up'">▲ {{ trend }}</template>
        <template v-else-if="trendTone === 'down'">▼ {{ trend }}</template>
        <template v-else>— {{ trend }}</template>
      </span>
      <span
        v-if="hint"
        class="stat-card__hint"
      >{{ hint }}</span>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px;
  min-height: 124px;
}

.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stat-card__label {
  color: var(--lims-muted);
  font-size: var(--lims-fs-sm);
  font-weight: 500;
}

.stat-card__icon {
  display: grid;
  width: 32px;
  height: 32px;
  border-radius: var(--lims-r-sm);
  place-items: center;
}

.stat-card__icon.is-accent {
  color: var(--lims-accent);
  background: rgba(var(--lims-accent-rgb), 0.12);
}
.stat-card__icon.is-success {
  color: var(--lims-success);
  background: var(--lims-success-soft);
}
.stat-card__icon.is-warning {
  color: var(--lims-warning);
  background: var(--lims-warning-soft);
}
.stat-card__icon.is-danger {
  color: var(--lims-danger);
  background: var(--lims-danger-soft);
}
.stat-card__icon.is-info {
  color: var(--lims-info);
  background: var(--lims-info-soft);
}
.stat-card__icon.is-purple {
  color: var(--lims-purple);
  background: var(--lims-purple-soft);
}

.stat-card__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-card__num {
  font-size: clamp(26px, 2.4vw, 32px);
  font-weight: 700;
  letter-spacing: 0.4px;
  color: var(--lims-ink);
  line-height: 1.05;
}

.stat-card__suffix {
  color: var(--lims-muted);
  font-size: var(--lims-fs-base);
  font-weight: 500;
  margin-left: 4px;
}

.stat-card__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: auto;
}

.stat-card__trend {
  font-size: var(--lims-fs-xs);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--lims-r-pill);
}

.stat-card__trend.is-up {
  color: var(--lims-success);
  background: var(--lims-success-soft);
}

.stat-card__trend.is-down {
  color: var(--lims-danger);
  background: var(--lims-danger-soft);
}

.stat-card__trend.is-flat {
  color: var(--lims-muted);
  background: rgba(255, 255, 255, 0.06);
}

.stat-card__hint {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}
</style>