# ECharts 在 Vue3 + TS + Vite 项目中的集成（按需引入 + 主题跟随）

> 结论层文档 ｜ 建立日期：2026-09-13 ｜ 作者：GLM ｜ 关联任务：T-803
>
> 推理过程见 `docs/journal/2026-09-13-glm-t105-107-603-803.md`；
> 选型裁决见 `DECISIONS.md`「2026-09-13 T-803 图表库选型」。
>
> **本文的目标**：后人照此文档可在 30 分钟内为任意 Vue3 后台项目接入 ECharts，
> 且不会踩体积、主题、尺寸三个最常见的坑。

---

## 1. 为什么是 ECharts（选型四问摘要）

| 问题 | 结论 |
|---|---|
| 零依赖可行吗？ | 7 个图表手写 SVG 需自研坐标/tooltip/hover/图例/标签避让，成本远超引入 |
| 能离线装吗？ | `npm i echarts@5.5.1` 仅 3 包（echarts + zrender + tslib），约 1 分钟 |
| 后人能接棒吗？ | 中文文档最完整，且是 RuoYi-Vue-Plus 等国内后台框架默认选择（与本项目 EasyExcel/MyBatis-Plus 选型生态一致） |
| 体积可控吗？ | **按需引入后路由级 chunk 183 kB gzip，主包零增长** ← 关键前提 |

**反例排除**：Chart.js（中文文档少、环形图表现弱）、AntV G2（图形语法学习曲线陡、与 option 配置风格不同路）、
手写 SVG（成本过高）、只做数字表格（不满足「可视化」要求）。

---

## 2. 安装

```bash
npm i echarts@5.5.1
```

仅安装 `echarts` 一个包，`zrender`（渲染引擎）与 `tslib` 会自动作为其依赖装入。

---

## 3. 按需引入最小集（关键）

**不要**写 `import * as echarts from 'echarts'` —— 全量引入会让 chunk 膨胀到 1 MB+。

本项目实际使用的最小集（`components/common/LimsChart.vue`）：

```ts
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

echarts.use([
  BarChart, LineChart, PieChart, GaugeChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent, DatasetComponent,
  CanvasRenderer,
])
```

### 引入清单对应关系（按图表类型取用）

| 你要画 | `echarts/charts` 需引入 | `echarts/components` 需引入 |
|---|---|---|
| 柱状图 | `BarChart` | `GridComponent` + `TooltipComponent` |
| 折线图/面积图 | `LineChart` | `GridComponent` + `TooltipComponent` |
| 饼图/环形图 | `PieChart` | `TooltipComponent` + `LegendComponent` |
| 仪表盘 | `GaugeChart` | `TooltipComponent` |
| 需要标题 | — | `TitleComponent` |
| 需要图例 | — | `LegendComponent` |
| 用 `dataset` 传数 | — | `DatasetComponent` |

**渲染器**：只需 `CanvasRenderer`。SVG 渲染器（`SVGRenderer`）会增加约 30 KB，
仅在「需要导出矢量图」或「DOM 层交互」时才需要——本系统零使用。

**`echarts/features`**（`LabelLayout` / `UniversalTransition` 等）**不要引入**：
环形图 `emphasis.scale` + 折线动画已由 `charts` 模块自带。引入会让体积白涨。

### TypeScript 类型

option 的类型用 `EChartsCoreOption`（来自 `echarts/core`），**不要**用 `echarts` 包的 `EChartsOption`
（后者会要求全量类型，且可能触发多余的模块解析）：

```ts
import type { EChartsCoreOption } from 'echarts/core'

export function pieOption(data: StatNameValue[]): EChartsCoreOption { /* ... */ }
```

好处：option 工厂函数返回值有类型约束，页面把它交给组件时无需断言。

---

## 4. 组件封装要点（`LimsChart.vue`）

### 4.1 必须用 `ResizeObserver`，不能用 `window.onresize`

**坑**：侧栏折叠（本项目 224px ↔ 68px）**不触发 `window.resize`**，但图表容器宽度会变。
用 `window.onresize` 会导致「折叠侧栏后图表错位且不重绘」，用户需刷新页面才能修正。

```ts
observer = new ResizeObserver(() => {
  const node = el.value
  // 容器被隐藏时宽高为 0，此时 resize() 会触发 ECharts 警告且可能算出 NaN 尺寸
  if (node && node.clientWidth > 0 && node.clientHeight > 0) {
    chart.value?.resize()
  }
})
observer.observe(el.value)
```

### 4.2 `setOption(option, true)` 用 notMerge

数据集整体替换时（如 Top N 从 5 项变 3 项），`merge` 模式会**残留上一批的维度**，
图表出现幽灵数据。`notMerge=true` 语义与「每次都是全量数据」的接口设计一致。

### 4.3 空态优先，不画空坐标系

`empty=true` 时不初始化 ECharts，直接渲染 `AppEmpty`，并 `dispose()` 已有实例。

理由：画一个空的坐标轴会让用户以为「加载失败」；「没数据」应该明确说「没数据」。
这也是本项目「**禁 mock 假数据**」纪律的视觉延伸。同时释放 canvas 内存。

### 4.4 卸载时释放

```ts
onBeforeUnmount(() => {
  observer?.disconnect()
  observer = undefined
  chart.value?.dispose()
  chart.value = undefined
})
```

`ResizeObserver` 必须 `disconnect()`，否则离开路由后仍持有 DOM 引用（内存泄漏）。

### 4.5 用 `shallowRef` 存实例

```ts
const chart = shallowRef<echarts.ECharts>()
```

ECharts 实例内部结构庞大，用 `ref` 会触发 Vue 的深度响应式代理包装，性能损耗明显且无收益。

---

## 5. 主题跟随：从 CSS 变量取色

本项目有「禁止硬编码色值以保证换肤能力」的令牌纪律（`styles/tokens.css` 头部注释）。
图表作为页面的一部分，若写死颜色，切到 `html.lims-theme-light` 时会出现
「暗色图表配浅色页面」的割裂。

**做法**：读取 CSS 变量，**在函数调用时取色**（不是模块加载时），确保主题切换后重新取色。

```ts
/** 从 CSS 变量取色，取不到用 fallback */
function token(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

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
```

⚠️ **坑**：`var(--lims-hair)` 的值形如 `rgba(255,255,255,0.07)`，`getPropertyValue` 返回的
字符串可直接用于 ECharts 的 `lineStyle.color`。但**不要**在 SVG/canvas 里用 `var(...)` 字面量——
ECharts 不认识 CSS 变量语法，必须传实际色值。

---

## 6. option 工厂分层（`utils/chartOptions.ts`）

三层职责分离，避免页面变胖：

| 层 | 文件 | 职责 |
|---|---|---|
| 取数 | `api/stat.ts` | 只负责 HTTP，返回强类型数据 |
| 图形 | `utils/chartOptions.ts` | 把数据转成 `EChartsCoreOption`（纯函数，可单测） |
| 布局 | `views/query/analysis.vue` | 卡片排列、加载态、空态文案 |

好处：新增一个图表 = 新增一个工厂函数 + 页面加一个卡片，**不需要动接口层**。

### 已实现的 4 个工厂

| 函数 | 图形 | 适用 |
|---|---|---|
| `pieOption(data, name)` | 环形图 | 构成分析（状态分布、大类占比） |
| `hBarOption(data, name)` | **横向**条形图 | Top N 排名（名称长时比纵向柱图易读） |
| `barOption(data, name)` | 纵向柱图 | 类别对比（名称短时更直观） |
| `trendOption(data)` | 双线趋势图 | 时间序列对比（样品数 vs 完成数） |

⚠️ **横向柱图的 y 轴是自下而上**，故需 `[...data].reverse()` 使最大值在顶部。

---

## 7. 体积验证（必做，不能只看构建日志）

构建日志会显示 chunk 列表，但**不能确认 ECharts 是否被提升进主包**。必须实测：

```bash
cd frontend
LIMS_BUILD_OUTDIR=dist npx vite build

# ① 确认图表页是独立 chunk
ls -la dist/assets/ | grep -E "analysis|echarts"

# ② 确认主包体积未增长（与接入前对比）
ls -la dist/assets/ | grep "index-"
```

**本项目实测结果**（2026-09-13）：

| chunk | 体积 | gzip |
|---|---|---|
| `analysis-*.js`（含 ECharts + 图表页） | 542.83 kB | 183.12 kB |
| `index-*.js`（主包，**接入前后无变化**） | 1,272.40 kB | 410.98 kB |

`analysis` 是路由懒加载（`component: () => import('@/views/query/analysis.vue')`），
所以 183 kB gzip 只在用户实际访问质量分析页时才下载，**首屏零成本**。

> ⚠️ 前提：图表页**必须**是懒加载路由，否则 ECharts 会进主包。

---

## 8. 完整接入清单（Checklist）

- [ ] `npm i echarts@5.5.1`
- [ ] 建 `components/common/LimsChart.vue`：按需 `echarts.use([...])` + `ResizeObserver` +
      空态优先 + `shallowRef` + `onBeforeUnmount` dispose + `setOption(option, true)`
- [ ] 建 `utils/chartOptions.ts`：`token()` 取色 + 4 个 option 工厂
- [ ] 建 `api/stat.ts`：强类型接口封装
- [ ] 页面：`Promise.allSettled` 并行取数（**不要用 `all`**，单接口失败不应整页空白）
- [ ] 路由：**懒加载**（`() => import(...)`）
- [ ] 侧栏菜单 + 权限标识
- [ ] 实测：`ls dist/assets | grep analysis` 确认独立 chunk + 主包未增长
- [ ] 空库场景验证：所有图表显示空态而非空坐标系
