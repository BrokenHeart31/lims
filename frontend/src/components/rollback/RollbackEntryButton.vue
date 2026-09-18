<script setup lang="ts">
/**
 * RollbackEntryButton — 「回退」入口按钮（列表行 / 详情区复用）。
 *
 * 权限：`v-permission="'rollback:execute'"` 无权限时整体隐藏（前端只做显隐，安全由后端兜底）。
 * 行为：点击 → 打开 `RollbackDialog`（内含 preview + execute 全流程）。
 */
import { ref } from 'vue'
import RollbackDialog from '@/components/rollback/RollbackDialog.vue'
import type { RollbackActionResultVO } from '@/types/rollback'

withDefaults(
  defineProps<{
    sampleId: number
    sampleNo?: string
    label?: string
    /** 是否渲染为链接按钮（表格行内用 link，详情区用实心） */
    link?: boolean
    size?: 'small' | 'default' | 'large'
  }>(),
  { sampleNo: '', label: '回退', link: true, size: 'small' },
)

const emit = defineEmits<{
  (e: 'done', result: RollbackActionResultVO): void
}>()

const open = ref(false)
</script>

<template>
  <span class="rb-entry">
    <el-button
      v-permission="'rollback:execute'"
      type="danger"
      :link="link"
      :size="size"
      @click="open = true"
    >
      {{ label }}
    </el-button>
    <RollbackDialog
      v-model="open"
      :sample-id="sampleId"
      :sample-no="sampleNo"
      @done="emit('done', $event)"
    />
  </span>
</template>

<style scoped>
.rb-entry {
  display: inline-flex;
}
</style>
