<script setup lang="ts">
/** DataFilter — 统一数据筛选栏；查询/重置由页面决定，组件只负责布局。 */
withDefaults(
  defineProps<{
    compact?: boolean
  }>(),
  { compact: false },
)

defineEmits<{
  search: []
  reset: []
}>()
</script>

<template>
  <div
    class="data-filter"
    :class="{ 'is-compact': compact }"
  >
    <div class="data-filter__fields">
      <slot />
    </div>
    <div
      v-if="$slots.actions"
      class="data-filter__actions"
    >
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.data-filter {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lims-sp-4);
  padding: var(--lims-sp-4);
  border: 1px solid var(--lims-hair);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-header);
}
.data-filter.is-compact { padding: var(--lims-sp-3); }
.data-filter :deep(.el-form-item) { margin: 0; }
.data-filter :deep(.el-form-item__content) { min-width: 0; }
.data-filter :deep(.el-input) { max-width: 100%; }
.data-filter__fields {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0 var(--lims-sp-4);
  min-width: 0;
}
.data-filter__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--lims-sp-2);
}
@media (max-width: 780px) {
  .data-filter { align-items: stretch; flex-direction: column; }
  .data-filter__actions { justify-content: flex-end; }
}
</style>
