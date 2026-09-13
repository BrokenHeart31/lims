# SKILL: vue3-crud-page —— Vue3 + Element Plus 标准 CRUD 页面

> 来源任务：T-201 监抽任务前端（豆包代 GLM 产出，Copilot 终审通过）｜ 提炼：Copilot ｜ 2026-09-11
> 2026-09-13 GLM 补充「§统计图表页（ECharts）」+
> 「§公共组件清单」「§树形表格页」「§master-detail 覆盖式提交」四个模式（T-105/106/107/803 实测）
> 状态：✅ lint 0 问题 + vue-tsc 通过，契约与后端核对一致

## 触发场景

任何业务模块的「列表页 + 查询栏 + 新建/编辑对话框 + 删除」前端开发。

## 前置

- 后端契约已在 `docs/api/api-spec.md` 对应域定稿（路径/字段/权限标识）
- 骨架已含：`utils/request.ts`（统一解包 R，401 跳登录）、`types/api.ts`（PageResult）、`stores/auth.ts`（hasPermission）、`directives/permission.ts`

## 权威示例文件

| 层 | 文件 |
|---|---|
| API 封装 | `frontend/src/api/task.ts`（接口函数 + 字典常量 + TS 接口，三合一） |
| 页面 | `frontend/src/views/task/index.vue`（查询栏/表格/分页/对话框/删除确认全模式） |
| 路由 | `frontend/src/router/index.ts`（meta.title，守卫自动鉴权） |

## 步骤

1. **API 封装**：`api/<模块>.ts`——导出 TS interface（与后端 Entity camelCase 对齐）、字典常量数组（`as const`）、五个函数（page/detail/create/update/del），全部走 `@/utils/request` 的 get/post/put/del，**禁止硬编码 baseURL**。
2. **页面**：`<script setup lang="ts">` + **TS strict 禁 any**：
   - 查询表单 ref + 列表 loading/tableData/total + 分页 `pageNum/pageSize`
   - 新建/编辑共用 `<el-dialog>` + `<el-form :rules>`，提交前 `formRef.validate()`
   - 按钮挂 `v-permission="'域:动作'"`（显隐用，安全后端兜底）
   - 删除 `ElMessageBox.confirm` 后调 API，成功 `ElMessage.success` + 刷新
   - 字典渲染用常量数组转 `<el-option>`，状态列用 `<el-tag :type>`
3. **路由登记**：静态路由加一条（动态路由改造见 HANDOFF，GLM 排期），meta.title 与菜单名一致。
4. **菜单/权限**：在 `db/seed/01_rbac_seed.sql` 对应菜单下补按钮权限行（后端 seed 范畴，提 TODO 给 Copilot/豆包）。

## 质量门禁（交付前必跑）

```bash
npm run lint      # ESLint 9 扁平配置，0 error 才准交；排版问题 npm run lint:fix
npm run build     # = vue-tsc --noEmit && vite build
```

## 踩坑

- **日期范围查询**：`<el-date-picker type="daterange" value-format="yyyy-MM-dd">` 出参是数组，拆 start/end 两个 query 参数，契约里分开定义。
- 路由守卫对已登录未加载 me 的导航会先 `await fetchMe()`——权限数据必在渲染前就绪，v-permission 不会误删按钮；**不要**在页面 onMounted 里再拉 me。
- 401 由 axios 拦截器统一处理（清 token 跳 /login?redirect=），页面层不 catch 401。
- Token 存 localStorage 键 `lims_access_token`（AGENTS 决策），禁止入 URL。
- 生产代码禁 `console.log`（lint 会拦）。
- **新增图标 import 后必须立即使用**：`noUnusedLocals: true` 会让未使用的 `import { Setting }`
  直接报错。（本项目 T-107 在 `MainLayout.vue` 踩到，新增 5 个图标时多带了一个。）

---

## §公共组件清单（**先查再建，禁止重复造**）

本项目已有 7 个公共组件，新页面前先核对能否复用：

| 组件 | 路径 | 用途 |
|---|---|---|
| `PageHeader` | `components/common/PageHeader.vue` | 页头：`title`/`subtitle`/`icon` + 默认插槽放操作按钮 + `#breadcrumb` 插槽 |
| `AppCard` | `components/common/AppCard.vue` | 卡片：`variant`('glass'/'panel'/'flat')/`padding`/`hoverable`/`accent` |
| `AppEmpty` | `components/common/AppEmpty.vue` | 空态：`title`/`hint`/`icon` + 默认插槽放操作 |
| `StatCard` | `components/common/StatCard.vue` | KPI 卡：`label`/`value`/`suffix`/`trend`/`trendTone`/`icon`/`iconTone`/`hint` |
| `StatusBadge` | `components/common/StatusBadge.vue` | 状态标签（统一 6 种 tone） |
| `AppBreadcrumb` | `components/common/AppBreadcrumb.vue` | 面包屑 |
| **`LimsChart`** | `components/common/LimsChart.vue` | **图表容器（2026-09-13 新增）** |

⚠️ 样式一律用 `var(--lims-*)` 设计令牌，**禁止硬编码色值**（保证换肤能力）。

---

## §模式 A：树形表格页（菜单/部门）

用于有 `parent_id` 层级的数据（本项目 `views/system/menu.vue`、`dept.vue`）。

```vue
<el-table :data="treeData" row-key="id" default-expand-all>
  <el-table-column prop="title" label="名称" />
</el-table>
```

**要点**：
1. `row-key="id"` **必须**（否则展开状态错乱、树形失效）
2. `default-expand-all` 视数据量决定；层级深+节点多时改为折叠
3. **上级选择器的选项要拍平并加缩进**，且**排除自身及其所有后代**（防成环）：
   ```ts
   const parentOptions = computed(() => {
     const out: { id: number; label: string }[] = []
     const walk = (nodes: Node[], depth: number) => {
       for (const n of nodes) {
         if (n.id === editingId.value) continue          // ⚠️ 排除自身分支
         out.push({ id: n.id, label: '　'.repeat(depth) + n.title })
         if (n.children?.length) walk(n.children, depth + 1)
       }
     }
     walk(tree.value, 0)
     return out
   })
   ```
   （后端也有成环校验兜底，但前端先排除能避免用户白提交一次。）

---

## §模式 B：master-detail 覆盖式提交（项目标准库）

用于「主表 + 明细列表」的编辑（本项目 `views/base/product-lib.vue`）。

**交互选择**：明细用**抽屉（`el-drawer`）内整表内联编辑**，保存时**一次性提交全量**。

```ts
// 客户端预检（真正的业务一致性校验留给后端）
function saveItems() {
  const errs: string[] = []
  items.value.forEach((it, i) => {
    if (!it.itemName?.trim()) errs.push(`第 ${i + 1} 行：检验项目不能为空`)
  })
  const orders = items.value.map(i => i.itemOrder)
  if (new Set(orders).size !== orders.length) errs.push('项次重复')
  if (errs.length) return ElMessage.error(errs.join('；'))

  await replaceProductLibItemsApi(productLibId, items.value)   // 覆盖式替换
}
```

**要点**：
1. **覆盖式优于增量**：前端提交「当前整表状态」，后端先删旧明细再重建。
   简单、天然幂等（同样输入重复提交结果一致），不需要 diff 算法。
2. **客户端只做「空值/重复」预检**，业务一致性（如 jt1 必填标准值）**必须**由后端做——
   前端校验可被绕过，且后端才是数据守门人。
3. 新增明细行时自动取最大项次 +1：`Math.max(0, ...items.map(i => i.itemOrder)) + 1`。
4. **明细为空时不要发空数组**（除非语义就是「清空」）——加确认框防误清。

---

## §模式 C：统计图表页（ECharts）

> 完整知识见 `docs/knowledge/2026-09-13-echarts-integration.md`，此处为速查。

### 三层文件结构

| 层 | 文件 | 职责 |
|---|---|---|
| 取数 | `api/stat.ts` | 只负责 HTTP，返回强类型 |
| 图形 | `utils/chartOptions.ts` | 数据 → `EChartsCoreOption`（纯函数） |
| 布局 | `views/query/analysis.vue` | 卡片排列 + 加载态 + 空态文案 |

新增一个图表 = 加一个工厂函数 + 加一个卡片，**不动接口层**。

### 页面骨架

```vue
<AppCard padding="20px">
  <header class="card-head">
    <h3 class="card-title">样品状态分布</h3>
    <span class="card-sub">按状态机阶段</span>
  </header>
  <LimsChart
    :option="statusOption"
    :height="296"
    :loading="loading"
    :empty="statusDist.length === 0"
    empty-title="暂无样品"
    empty-hint="登记样品后此处将显示各阶段分布"
  />
</AppCard>
```

```ts
const statusOption = computed(() => pieOption(statusDist.value, '份'))
```

### 取数：用 `Promise.allSettled`，不要 `all`

```ts
async function load() {
  loading.value = true
  await Promise.allSettled([
    getStatOverviewApi().then(d => (overview.value = d)),
    getSampleStatusStatApi().then(d => (statusDist.value = d ?? [])),
    // ...
  ])
  loading.value = false
}
```

**理由**：单接口异常不应导致整页空白。`all` 会在首个 reject 中断，
把「一个接口挂了」升级为「整页不可用」——而统计页有 8~9 个独立图表，
各自独立失败是常态。

### 强制要求

- 路由**必须懒加载**（`() => import('@/views/query/analysis.vue')`），否则 ECharts 进主包
- `API` 数据为 `null` 时兜底空数组（`d ?? []`），否则 `.length` 报错
- 数值指标支持**第三态**：`null` → 显示「暂无」（≠ `0`）：
  ```ts
  const rateText = computed(() => {
    const r = overview.value?.qualifiedRate
    return r === null || r === undefined ? '暂无' : r.toFixed(1)
  })
  ```
- **禁 mock 假数据**：无数据就显示空态，绝不填演示数字

### 体积验证（必做）

```bash
LIMS_BUILD_OUTDIR=dist npx vite build
ls -la dist/assets/ | grep -E "analysis|echarts"   # 确认图表页是独立 chunk
ls -la dist/assets/ | grep "index-"                 # 确认主包未增长
```
