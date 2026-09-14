<script setup lang="ts">
/** AppLoading — 页面级加载占位，避免每个页面重复拼装 spinner。 */
withDefaults(
  defineProps<{
    text?: string
    minHeight?: string | number
  }>(),
  { text: '正在加载…', minHeight: '160px' },
)
</script>

<template>
  <div
    class="app-loading"
    :style="{ minHeight: typeof minHeight === 'number' ? `${minHeight}px` : minHeight }"
    role="status"
    aria-live="polite"
  >
    <span
      class="app-loading__spinner"
      aria-hidden="true"
    />
    <span class="app-loading__text">{{ text }}</span>
  </div>
</template>

<style scoped>
.app-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lims-sp-3);
  color: var(--lims-muted);
  font-size: var(--lims-fs-sm);
}

.app-loading__spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--lims-hair-strong);
  border-top-color: var(--lims-accent);
  border-radius: 50%;
  animation: app-loading-spin 0.8s linear infinite;
}

@keyframes app-loading-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .app-loading__spinner { animation: none; }
}
</style>
