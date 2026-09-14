<script setup lang="ts" generic="T">
/**
 * DataTable — 表格状态与分页外壳，业务列和操作仍由页面维护。
 * keepMounted 保留表格实例及 reserve-selection；错误只隐藏旧表，不清空选择。
 */
import AppEmpty from './AppEmpty.vue'
import AppLoading from './AppLoading.vue'

withDefaults(
  defineProps<{
    rows: T[]
    loading?: boolean
    error?: string
    keepMounted?: boolean
    total?: number
    current?: number
    pageSize?: number
    pageSizes?: number[]
    pagination?: boolean
    emptyTitle?: string
  }>(),
  {
    loading: false,
    error: '',
    keepMounted: false,
    total: 0,
    current: 1,
    pageSize: 20,
    pageSizes: () => [10, 20, 50],
    pagination: true,
    emptyTitle: '暂无数据',
  },
)

const emit = defineEmits<{
  'update:current': [value: number]
  'update:pageSize': [value: number]
  'current-change': [value: number]
  'size-change': [value: number]
  retry: []
}>()
</script>

<template>
  <div
    v-loading="keepMounted && loading"
    class="data-table"
    :aria-busy="loading"
  >
    <div
      v-if="!keepMounted && loading"
      class="data-table__loading"
      role="status"
    >
      <AppLoading />
    </div>
    <div
      v-else-if="error"
      role="alert"
    >
      <AppEmpty
        title="数据加载失败"
        :hint="error"
      >
        <el-button
          type="primary"
          :loading="loading"
          @click="emit('retry')"
        >
          重新加载
        </el-button>
      </AppEmpty>
    </div>
    <template v-else-if="!keepMounted && rows.length === 0">
      <slot name="empty">
        <AppEmpty :title="emptyTitle" />
      </slot>
    </template>
    <!-- keepMounted 时空态由业务表格的 empty 插槽负责，禁止 v-if 卸载表格。 -->
    <div
      v-if="keepMounted || (!loading && !error && rows.length > 0)"
      v-show="!error"
      class="data-table__body"
    >
      <slot :rows="rows" />
    </div>
    <!-- 即使当前页为空也保留分页，用户可回到上一页或更改每页条数。 -->
    <div
      v-if="pagination && !error"
      class="data-table__pager"
    >
      <el-pagination
        :current-page="current"
        :page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        :disabled="loading"
        layout="total, sizes, prev, pager, next, jumper"
        @update:current-page="(value: number) => emit('update:current', value)"
        @update:page-size="(value: number) => emit('update:pageSize', value)"
        @current-change="(value: number) => emit('current-change', value)"
        @size-change="(value: number) => emit('size-change', value)"
      />
    </div>
  </div>
</template>

<style scoped>
.data-table { min-width: 0; }
.data-table__loading { min-height: 180px; }
.data-table__body { min-width: 0; overflow-x: auto; }
.data-table__pager { display: flex; justify-content: flex-end; overflow-x: auto; padding: var(--lims-sp-4); }
@media (max-width: 780px) {
  .data-table__pager { justify-content: flex-start; }
}
</style>
