<script setup lang="ts">
/**
 * AppEmpty — 空态（提示词 §二十七）
 * ----------------------------------------------------------------------------
 * 用法：
 *   <AppEmpty title="暂无检测报告" hint="当前筛选条件下没有找到匹配数据">
 *     <el-button @click="reset">清除筛选</el-button>
 *   </AppEmpty>
 */
import { Document } from '@element-plus/icons-vue'
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    title?: string
    hint?: string
    /** 自定义图标 */
    icon?: Component | string
  }>(),
  { title: '暂无数据', hint: '', icon: undefined },
)
</script>

<template>
  <div class="app-empty">
    <div
      class="app-empty__icon"
      aria-hidden="true"
    >
      <el-icon :size="46">
        <component :is="icon ?? Document" />
      </el-icon>
    </div>
    <p class="app-empty__title">
      {{ title }}
    </p>
    <p
      v-if="hint"
      class="app-empty__hint"
    >
      {{ hint }}
    </p>
    <div
      v-if="$slots.default"
      class="app-empty__actions"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.app-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 16px;
  text-align: center;
}

.app-empty__icon {
  display: grid;
  width: 72px;
  height: 72px;
  border: 1px solid var(--lims-hair);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.03);
  color: var(--lims-faint);
  place-items: center;
  margin-bottom: 4px;
}

.app-empty__title {
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-base);
  font-weight: 500;
}

.app-empty__hint {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  max-width: 40ch;
  line-height: 1.5;
}

.app-empty__actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
</style>