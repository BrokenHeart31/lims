<script setup lang="ts">
/** LimsChart — 按需加载 ECharts，明确区分加载、失败、空数据；容器可见后才初始化。 */
import { onActivated, onBeforeUnmount, onMounted, ref, shallowRef, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, GaugeChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent, DatasetComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppLoading from '@/components/common/AppLoading.vue'

echarts.use([BarChart, LineChart, PieChart, GaugeChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, DatasetComponent, CanvasRenderer])

const props = withDefaults(
  defineProps<{
    option: EChartsCoreOption
    height?: number
    empty?: boolean
    emptyTitle?: string
    emptyHint?: string
    loading?: boolean
    error?: string
  }>(),
  {
    height: 300,
    empty: false,
    emptyTitle: '暂无数据',
    emptyHint: '当前筛选条件下没有可统计的记录',
    loading: false,
    error: '',
  },
)
const emit = defineEmits<{ retry: [] }>()
const el = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()
let observer: ResizeObserver | undefined
let observed: HTMLDivElement | undefined
let mounted = false

function token(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback
}

function resize(): void {
  const node = el.value
  if (!mounted || !node || props.loading || props.error || props.empty) return
  if (node.clientWidth <= 0 || node.clientHeight <= 0) return
  if (!chart.value) {
    chart.value = echarts.init(node, undefined, { renderer: 'canvas' })
    chart.value.setOption(props.option, true)
  } else {
    chart.value.resize()
  }
}

function destroy(): void {
  observer?.disconnect()
  observer = undefined
  observed = undefined
  chart.value?.dispose()
  chart.value = undefined
}

async function render(): Promise<void> {
  if (!mounted) return
  if (props.loading || props.error || props.empty) {
    destroy()
    return
  }
  await nextTick()
  if (!mounted || props.loading || props.error || props.empty || !el.value) return
  if (observed !== el.value) {
    destroy()
    observed = el.value
    observer = new ResizeObserver(resize)
    observer.observe(observed)
  }
  resize()
  // 整体替换，避免图例、数据维度残留。
  chart.value?.setOption(props.option, true)
}

onMounted(() => { mounted = true; void render() })
onActivated(resize)
onBeforeUnmount(() => { mounted = false; destroy() })
watch(() => [props.option, props.empty, props.loading, props.error], render, { deep: true })

defineExpose({
  resize,
  palette: {
    axis: () => token('--lims-muted', '#8a9099'),
    split: () => token('--lims-hair', 'rgba(255,255,255,0.07)'),
    ink: () => token('--lims-ink', '#e8ecef'),
  },
})
</script>

<template>
  <div
    class="lims-chart"
    :style="{ height: `${height}px` }"
    :aria-busy="loading"
  >
    <AppLoading
      v-if="loading"
      :min-height="height"
      text="正在加载统计数据…"
    />
    <div
      v-else-if="error"
      role="alert"
    >
      <AppEmpty
        title="统计数据加载失败"
        :hint="error"
      >
        <el-button @click="emit('retry')">
          重新加载
        </el-button>
      </AppEmpty>
    </div>
    <AppEmpty
      v-else-if="empty"
      :title="emptyTitle"
      :hint="emptyHint"
    />
    <div
      v-else
      ref="el"
      class="lims-chart__canvas"
      role="img"
      aria-label="业务统计图表"
    />
  </div>
</template>

<style scoped>
.lims-chart { position: relative; width: 100%; min-width: 0; overflow: auto; }
.lims-chart__canvas { width: 100%; height: 100%; }
.lims-chart :deep(.app-empty) { padding: 20px 16px; }
</style>
