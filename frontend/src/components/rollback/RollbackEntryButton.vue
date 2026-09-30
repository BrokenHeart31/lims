<script setup lang="ts">
/**
 * RollbackEntryButton — 业务页内嵌的「回退」入口（列表行 / 详情区 / 批量工具栏复用）。
 *
 * 2026-09-30 改造（回退下沉为环节内嵌能力）：
 *   · 支持**单条**（`sample-id`）与**批量**（`samples`，长度 1 也可）两种用法；
 *   · 同状态校验、目标步选择、影响预览、逐条结果全部在 `RollbackDialog` 内完成；
 *   · 可选附带「留痕」入口（`show-trace`，默认开）——独立「流程回溯」页面已移除，
 *     时间线查看与撤销回退改由 `RollbackTraceDrawer` 就地提供，能力不丢。
 *
 * 权限：回退需 `rollback:execute`，留痕需 `rollback:view`。
 * **前端只做显隐，安全由后端 `@PreAuthorize` + 服务层二次鉴权兜底**（红线）。
 */
import { computed, ref } from 'vue'
import RollbackDialog from '@/components/rollback/RollbackDialog.vue'
import RollbackTraceDrawer from '@/components/rollback/RollbackTraceDrawer.vue'
import type { RollbackBatchResultVO, RollbackSampleRef } from '@/types/rollback'

const props = withDefaults(
  defineProps<{
    /** 批量入口：待回退样品（单条回退传长度为 1 的数组） */
    samples?: RollbackSampleRef[]
    /** 单条入口的兼容写法 */
    sampleId?: number
    sampleNo?: string
    label?: string
    /** 是否渲染为链接按钮（表格行内用 link，详情区用实心） */
    link?: boolean
    size?: 'small' | 'default' | 'large'
    /** 由调用方控制可用性（如批量工具栏在「未勾选」时禁用） */
    disabled?: boolean
    /** 是否同时渲染「留痕」入口 */
    showTrace?: boolean
    traceLabel?: string
  }>(),
  {
    samples: undefined,
    sampleId: undefined,
    sampleNo: '',
    label: '回退',
    link: true,
    size: 'small',
    disabled: false,
    showTrace: true,
    traceLabel: '留痕',
  },
)

const emit = defineEmits<{
  /** 回退执行完成（成功或部分失败都触发，携带逐条明细） */
  (e: 'done', result: RollbackBatchResultVO): void
  /** 留痕抽屉里撤销了一次回退 → 数据已变，请调用方刷新列表 */
  (e: 'refresh'): void
}>()

const dialogOpen = ref(false)
const traceOpen = ref(false)

/** 归一化后的待回退样品 */
const items = computed<RollbackSampleRef[]>(() => {
  if (props.samples && props.samples.length > 0) return props.samples
  if (props.sampleId != null) return [{ id: props.sampleId, sampleNo: props.sampleNo }]
  return []
})

const disabled = computed(() => props.disabled || items.value.length === 0)

/** 留痕入口只对单条样品有意义（一次看一条时间线） */
const traceSample = computed(() => items.value[0])
</script>

<template>
  <span class="rb-entry">
    <el-button
      v-permission="'rollback:execute'"
      type="danger"
      :link="link"
      :size="size"
      :disabled="disabled"
      @click="dialogOpen = true"
    >
      {{ label }}
    </el-button>
    <el-button
      v-if="showTrace"
      v-permission="'rollback:view'"
      :link="link"
      :size="size"
      :disabled="disabled"
      @click="traceOpen = true"
    >
      {{ traceLabel }}
    </el-button>

    <RollbackDialog
      v-model="dialogOpen"
      :samples="items"
      @done="emit('done', $event)"
    />
    <RollbackTraceDrawer
      v-if="showTrace && traceSample"
      v-model="traceOpen"
      :sample-id="traceSample.id"
      :sample-no="traceSample.sampleNo"
      @done="emit('refresh')"
    />
  </span>
</template>

<style scoped>
.rb-entry {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
</style>
