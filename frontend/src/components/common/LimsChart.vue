<script setup lang="ts">
/**
 * LimsChart — ECharts 通用封装（T-803）
 * ----------------------------------------------------------------------------
 * 设计要点：
 *   ① **按需引入**（tree-shaking）：只注册本系统用到的图表类型与组件，
 *      bundle 增量远小于 `import * as echarts from 'echarts'`（后者约 +1MB）。
 *   ② **响应式**：ResizeObserver 监听容器尺寸，侧栏折叠/窗口缩放自动 reflow。
 *   ③ **主题跟随设计令牌**：坐标轴/文字/分割线颜色一律从 CSS 变量读取，
 *      避免出现「图表是白色系、页面是暗色」的割裂；同时不硬编码色值。
 *   ④ **空态优先**：`empty` 为真时渲染 AppEmpty，不画空坐标系
 *      （「禁 mock 假数据」的视觉延伸——没数据就说没数据）。
 *   ⑤ 切换 tab / 折叠面板时容器尺寸为 0 会导致 ECharts 警告，故 resize 前判宽高。
 */
import { onActivated, onBeforeUnmount, onMounted, ref, shallowRef, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart, GaugeChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import AppEmpty from '@/components/common/AppEmpty.vue'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GaugeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  CanvasRenderer,
])

const props = withDefaults(
  defineProps<{
    /** ECharts option；父组件用 computed 生成 */
    option: EChartsCoreOption
    /** 容器高度（px） */
    height?: number
    /** 无数据时展示空态并销毁实例 */
    empty?: boolean
    /** 空态文案 */
    emptyTitle?: string
    emptyHint?: string
    /** 加载中（骨架遮罩） */
    loading?: boolean
  }>(),
  {
    height: 300,
    empty: false,
    emptyTitle: '暂无数据',
    emptyHint: '当前筛选条件下没有可统计的记录',
    loading: false,
  },
)

const el = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()
let observer: ResizeObserver | undefined

/** 从 CSS 变量取色，保证图表与页面主题一致（禁止硬编码） */
function token(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

function init(): void {
  if (!el.value || props.empty) return
  if (chart.value) return
  chart.value = echarts.init(el.value, undefined, { renderer: 'canvas' })
  chart.value.setOption(props.option)
  observer = new ResizeObserver(() => {
    const node = el.value
    // 容器被隐藏时宽高为 0，此时 resize 会触发 ECharts 警告，故跳过
    if (node && node.clientWidth > 0 && node.clientHeight > 0) {
      chart.value?.resize()
    }
  })
  observer.observe(el.value)
}

function destroy(): void {
  observer?.disconnect()
  observer = undefined
  chart.value?.dispose()
  chart.value = undefined
}

async function render(): Promise<void> {
  if (props.empty) {
    destroy()
    return
  }
  await nextTick()
  init()
  // notMerge=true：数据集整体替换，避免上一批维度残留（例如从 5 项变为 3 项）
  chart.value?.setOption(props.option, true)
}

onMounted(render)
onActivated(() => chart.value?.resize())
onBeforeUnmount(destroy)

watch(() => props.option, render, { deep: true })
watch(() => props.empty, render)

/** 供父组件手动触发（如卡片的「刷新」按钮重算尺寸） */
defineExpose({
  resize: () => chart.value?.resize(),
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
  >
    <AppEmpty
      v-if="empty"
      :title="emptyTitle"
      :hint="emptyHint"
    />
    <div
      v-else
      ref="el"
      class="lims-chart__canvas"
      :class="{ 'is-loading': loading }"
    />
  </div>
</template>

<style scoped>
.lims-chart {
  position: relative;
  width: 100%;
}

.lims-chart__canvas {
  width: 100%;
  height: 100%;
  transition: opacity var(--lims-dur) var(--lims-ease-out);
}

.lims-chart__canvas.is-loading {
  opacity: 0.45;
}
</style>
