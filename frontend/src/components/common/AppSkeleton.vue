<script setup lang="ts">
/** AppSkeleton — 统一骨架屏，适用于 KPI、表格和详情区域。 */
withDefaults(
  defineProps<{
    rows?: number
    height?: string
    width?: string
    rounded?: boolean
  }>(),
  { rows: 3, height: '14px', width: '100%', rounded: true },
)
</script>

<template>
  <div
    class="app-skeleton"
    aria-hidden="true"
  >
    <span
      v-for="row in rows"
      :key="row"
      class="app-skeleton__line"
      :class="{ 'is-rounded': rounded }"
      :style="{ height, width: row === rows && rows > 1 ? '72%' : width }"
    />
  </div>
</template>

<style scoped>
.app-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-3);
  width: 100%;
}

.app-skeleton__line {
  display: block;
  background: linear-gradient(90deg, var(--lims-layer-card-hover), var(--lims-layer-elevated), var(--lims-layer-card-hover));
  background-size: 220% 100%;
  animation: app-skeleton-shimmer 1.4s ease-in-out infinite;
}

.app-skeleton__line.is-rounded { border-radius: var(--lims-r-ctrl); }

@keyframes app-skeleton-shimmer {
  from { background-position: 100% 0; }
  to { background-position: -100% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .app-skeleton__line { animation: none; }
}
</style>
