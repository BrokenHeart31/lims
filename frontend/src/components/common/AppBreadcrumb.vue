<script setup lang="ts">
/**
 * AppBreadcrumb — 面包屑
 * ----------------------------------------------------------------------------
 * 用法：
 *   <AppBreadcrumb :items="[{ title: '实验室业务', to: '/sample' }, { title: '样品登记' }]" />
 */
import { useRouter } from 'vue-router'

defineProps<{
  items: { title: string; to?: string }[]
}>()

const router = useRouter()

function go(to?: string): void {
  if (to) void router.push(to)
}
</script>

<template>
  <nav
    class="app-breadcrumb"
    aria-label="breadcrumb"
  >
    <template
      v-for="(item, i) in items"
      :key="i"
    >
      <span
        class="app-breadcrumb__item"
        :class="{ 'is-last': i === items.length - 1, 'is-link': !!item.to }"
        :role="item.to ? 'button' : undefined"
        :tabindex="item.to ? 0 : undefined"
        @click="go(item.to)"
        @keyup.enter="go(item.to)"
      >{{ item.title }}</span>
      <span
        v-if="i < items.length - 1"
        class="app-breadcrumb__sep"
        aria-hidden="true"
      >/</span>
    </template>
  </nav>
</template>

<style scoped>
.app-breadcrumb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--lims-fs-xs);
  color: var(--lims-breadcrumb-fg);
}

.app-breadcrumb__item {
  color: var(--lims-breadcrumb-fg);
  font-weight: 500;
  cursor: default;
  transition: color var(--lims-dur-fast) var(--lims-ease-out);
}

.app-breadcrumb__item.is-link {
  cursor: pointer;
}

.app-breadcrumb__item.is-link:hover {
  color: var(--lims-accent);
}

.app-breadcrumb__item.is-last {
  color: var(--lims-breadcrumb-fg-active);
  font-weight: 600;
}

.app-breadcrumb__sep {
  color: var(--lims-breadcrumb-sep);
  font-weight: 400;
  margin: 0 2px;
}
</style>