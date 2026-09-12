<script setup lang="ts">
/**
 * StatusBadge — 统一状态徽章
 * ----------------------------------------------------------------------------
 * 业务可识别的 status 抽象：tone（颜色）+ icon + label
 * 用法：
 *   <StatusBadge tone="success">已签发</StatusBadge>
 *   <StatusBadge tone="pending">待判定</StatusBadge>
 *   <StatusBadge tone="blank">未录入</StatusBadge>
 *   <StatusBadge tone="purple">审核中</StatusBadge>
 */
withDefaults(
  defineProps<{
    tone?:
      | 'success'
      | 'warning'
      | 'danger'
      | 'info'
      | 'purple'
      | 'neutral'
      | 'pending'
      | 'blank'
    /** 是否显示圆点 */
    dot?: boolean
    /** 自定义 size */
    size?: 'sm' | 'md'
  }>(),
  { tone: 'info', dot: true, size: 'md' },
)
</script>

<template>
  <span
    class="status-badge"
    :class="[`is-${tone}`, `is-${size}`]"
  >
    <span
      v-if="dot"
      class="status-badge__dot"
      aria-hidden="true"
    />
    <span class="status-badge__label"><slot /></span>
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: var(--lims-r-pill);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.6;
  white-space: nowrap;
  border: 1px solid transparent;
}

.status-badge.is-sm {
  padding: 1px 8px;
  font-size: 11px;
}

.status-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

/* tone 配色（线 + 底 + 字） */
.status-badge.is-success {
  color: var(--lims-success);
  background: var(--lims-success-soft);
  border-color: var(--lims-success-line);
}
.status-badge.is-warning {
  color: var(--lims-warning);
  background: var(--lims-warning-soft);
  border-color: var(--lims-warning-line);
}
.status-badge.is-danger {
  color: var(--lims-danger);
  background: var(--lims-danger-soft);
  border-color: var(--lims-danger-line);
}
.status-badge.is-info {
  color: var(--lims-info);
  background: var(--lims-info-soft);
  border-color: var(--lims-info-line);
}
.status-badge.is-purple,
.status-badge.is-pending {
  color: var(--lims-purple);
  background: var(--lims-purple-soft);
  border-color: var(--lims-purple-line);
}
.status-badge.is-blank {
  color: var(--lims-muted);
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--lims-hair-2);
}
.status-badge.is-neutral {
  color: var(--lims-muted);
  background: rgba(255, 255, 255, 0.04);
  border-color: var(--lims-hair);
}
</style>