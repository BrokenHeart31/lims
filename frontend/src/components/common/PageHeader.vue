<script setup lang="ts">
/**
 * PageHeader — 统一页面头部
 * ----------------------------------------------------------------------------
 * 结构：面包屑 + 标题 + 副标题 + 右侧操作区
 * 用法：
 *   <PageHeader title="样品登记" subtitle="采样单 Excel 导入与登记确认">
 *     <template #breadcrumb>
 *       <el-breadcrumb-item>...</el-breadcrumb-item>
 *     </template>
 *     <el-button>刷新</el-button>
 *     <el-button>导出</el-button>
 *   </PageHeader>
 */
import type { Component } from 'vue'

defineProps<{
  /** 标题（必填） */
  title: string
  /** 副标题（说明当前页面在做什么） */
  subtitle?: string
  /** 标题图标（ElementPlus icon 名 / Component） */
  icon?: string | Component
}>()
</script>

<template>
  <header class="page-header lims-glass">
    <div class="page-header__main">
      <div
        v-if="icon"
        class="page-header__icon"
        aria-hidden="true"
      >
        <el-icon :size="22">
          <component :is="icon" />
        </el-icon>
      </div>
      <div class="page-header__text">
        <div
          v-if="$slots.breadcrumb"
          class="page-header__crumb"
        >
          <slot name="breadcrumb" />
        </div>
        <div class="page-header__title-row">
          <h1 class="page-header__title">
            {{ title }}
          </h1>
          <span
            v-if="subtitle"
            class="page-header__sub"
          >{{ subtitle }}</span>
        </div>
      </div>
    </div>
    <div
      v-if="$slots.default"
      class="page-header__actions"
    >
      <slot />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-4);
  padding: var(--lims-page-header-py) var(--lims-sp-6);
  border-radius: var(--lims-r-lg);
}

.page-header__main {
  display: flex;
  flex: 1;
  align-items: center;
  gap: var(--lims-sp-4);
  min-width: 0;
}

.page-header__icon {
  display: grid;
  width: 40px;
  height: 40px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-sm);
  background: rgba(var(--lims-accent-rgb), 0.08);
  color: var(--lims-accent);
  place-items: center;
  box-shadow: var(--lims-inner-hair);
}

.page-header__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.page-header__crumb {
  color: var(--lims-breadcrumb-fg);
  font-size: var(--lims-fs-xs);
}

.page-header__title-row {
  display: flex;
  align-items: baseline;
  gap: var(--lims-sp-3);
  min-width: 0;
}

.page-header__title {
  font-size: var(--lims-page-title-size);
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--lims-ink);
  line-height: 1.2;
  margin: 0;
}

.page-header__sub {
  color: var(--lims-muted);
  font-size: var(--lims-page-sub-size);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60ch;
}

.page-header__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--lims-sp-2);
}
</style>