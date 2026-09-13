/**
 * 图表 option 工厂（T-803）
 * ----------------------------------------------------------------------------
 * 为什么单独抽一层：
 *   stat.ts 只负责「取数」，页面只负责「布局」，本文件只负责「把数变成图形」。
 *   三者分离后，新增一个图表 = 新增一个工厂函数 + 页面加一个卡片，不需要动接口层。
 *
 * 主题约定（**禁止硬编码色值**）：
 *   所有颜色从 CSS 变量读取，页面切到浅色主题（html.lims-theme-light）时图表自动跟随。
 *   读取时机在函数调用时（而非模块加载时），确保主题切换后重新取色。
 */
import type { EChartsCoreOption } from 'echarts/core'
import type { StatNameValue, StatTrend } from '@/api/stat'

/** 从 CSS 变量取色，取不到用 fallback */
function token(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

/** 语义色板：与设计令牌一一对应，供饼图/柱图循环取用 */
export function palette(): string[] {
  return [
    token('--lims-accent', '#00f5d4'),
    token('--lims-blue', '#2442ff'),
    token('--lims-success', '#22c55e'),
    token('--lims-warning', '#f5a524'),
    token('--lims-danger', '#ff5367'),
    token('--lims-info', '#7fd8ff'),
    token('--lims-purple', '#9b7cff'),
    token('--lims-gold', '#f4d28a'),
  ]
}

/** 图表通用文本/轴线样式 */
function chrome() {
  const axis = token('--lims-muted', '#8a9099')
  const split = token('--lims-hair', 'rgba(255,255,255,0.07)')
  const ink = token('--lims-ink', '#e8ecef')
  const surface = token('--lims-surface-3', 'rgba(36,41,48,0.86)')
  return { axis, split, ink, surface }
}

/** 统一 tooltip 外观（深色玻璃卡） */
function tooltipBase() {
  const c = chrome()
  return {
    backgroundColor: c.surface,
    borderColor: c.split,
    borderWidth: 1,
    padding: [8, 12] as [number, number],
    textStyle: { color: c.ink, fontSize: 12 },
    extraCssText: 'border-radius:10px;backdrop-filter:blur(10px);',
  }
}

/** 饼图（环形）——用于状态/类别/单位构成 */
export function pieOption(data: StatNameValue[], name = '数量'): EChartsCoreOption {
  const c = chrome()
  const colors = palette()
  return {
    color: colors,
    tooltip: {
      ...tooltipBase(),
      trigger: 'item',
      formatter: (p: { name: string; value: number; percent?: number }) =>
        `${p.name}<br/><b>${p.value}</b> ${name}（${p.percent ?? 0}%）`,
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 14,
      textStyle: { color: c.axis, fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: 'transparent', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 6,
          label: { show: true, color: c.ink, fontSize: 13, fontWeight: 'bold', formatter: '{b}\n{d}%' },
        },
        data: data.map((d) => ({ name: d.name, value: d.value })),
      },
    ],
  }
}

/** 横向条形图——用于 Top N 排名（名称较长时比纵向柱图易读） */
export function hBarOption(data: StatNameValue[], name = '数量'): EChartsCoreOption {
  const c = chrome()
  const colors = palette()
  // ECharts 横向柱图 y 轴自下而上，故反转使最大值在顶部
  const rows = [...data].reverse()
  return {
    color: colors,
    grid: { left: 8, right: 42, top: 8, bottom: 8, containLabel: true },
    tooltip: { ...tooltipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: c.split } },
      axisLabel: { color: c.axis, fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: rows.map((d) => d.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: c.axis,
        fontSize: 12,
        width: 120,
        overflow: 'truncate',
      },
    },
    series: [
      {
        type: 'bar',
        name,
        barMaxWidth: 16,
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: colors[1] },
              { offset: 1, color: colors[0] },
            ],
          },
        },
        label: { show: true, position: 'right', color: c.ink, fontSize: 11 },
        data: rows.map((d) => d.value),
      },
    ],
  }
}

/** 纵向柱图——用于类别对比（名称短时更直观） */
export function barOption(data: StatNameValue[], name = '数量'): EChartsCoreOption {
  const c = chrome()
  const colors = palette()
  return {
    color: colors,
    grid: { left: 8, right: 16, top: 20, bottom: 8, containLabel: true },
    tooltip: { ...tooltipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'category',
      data: data.map((d) => d.name),
      axisLine: { lineStyle: { color: c.split } },
      axisTick: { show: false },
      axisLabel: { color: c.axis, fontSize: 12, interval: 0, rotate: data.length > 5 ? 20 : 0 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: c.split } },
      axisLabel: { color: c.axis, fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        name,
        barMaxWidth: 34,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: colors[0] },
              { offset: 1, color: 'rgba(36,66,255,0.35)' },
            ],
          },
        },
        label: { show: true, position: 'top', color: c.ink, fontSize: 11 },
        data: data.map((d) => d.value),
      },
    ],
  }
}

/** 双线趋势图——样品数 vs 已完成数（月度） */
export function trendOption(data: StatTrend[]): EChartsCoreOption {
  const c = chrome()
  const colors = palette()
  return {
    color: [colors[0], colors[2]],
    grid: { left: 8, right: 20, top: 34, bottom: 8, containLabel: true },
    tooltip: { ...tooltipBase(), trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 10,
      itemHeight: 4,
      textStyle: { color: c.axis, fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map((d) => d.month),
      axisLine: { lineStyle: { color: c.split } },
      axisTick: { show: false },
      axisLabel: { color: c.axis, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: c.split } },
      axisLabel: { color: c.axis, fontSize: 11 },
    },
    series: [
      {
        name: '登记样品数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.14 },
        data: data.map((d) => d.sampleCount),
      },
      {
        name: '完成检验数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.1 },
        data: data.map((d) => d.completedCount),
      },
    ],
  }
}
