# 动态路由：路径注册表模式（Path Registry + Middleware Transform）

> 来源：2026-09-13 GLM 在 LIMS 项目落地动态路由的实测经验。
> 适用：Vue 3 + Vue Router 4 + 后端返回菜单树的 RBAC 后台系统。

## 一、要解决的问题

后端 `/api/auth/me` 返回「当前用户可见的菜单树」，前端要据此：
1. 动态注册路由（用户没权限的页面不该存在于路由表）
2. 生成侧栏菜单
3. 支持 F5 刷新、直接粘贴 URL 访问、权限校验

## 二、核心设计：三层分离

```
后端 DB 菜单树  ──►  路径注册表（前端）  ──►  路由 + 侧栏菜单
  只提供：           提供：                    同源产出
  · 结构/层级        · path → 组件映射
  · 标题/图标        · title / permissions
  · 排序             · standalone / navVisible
```

**为什么 DB 不该存组件路径**（三条独立理由，任一条都足够）：
1. **构建期概念**：组件路径是前端目录结构，写进 DB 意味着前端重构要改数据库。
2. **构建优化**：DB 里的字符串无法被 Vite 静态分析，失去 tree-shaking / 预加载。
3. **安全**：让 DB 决定加载哪个组件 = 把「代码执行入口」交给数据，是危险设计。

**为什么前端不该让 DB 决定一切**：DB 只应描述「业务上谁能看什么」，
不应该描述「代码在哪里」——后者是工程细节，不是业务配置。

## 三、什么时候**不要**用 glob 自动匹配

常见做法是 `import.meta.glob('@/views/**/*.vue')` 然后按路径匹配。**先确认你的路径约定是否真的统一**。

实测反例（LIMS 项目真实情况）：

| DB path | 实际页面文件 | 问题 |
|---|---|---|
| `/sample` | `views/sample/index.vue` | 文件名多了 index |
| `/assign/index` | `views/assign/index.vue` | **保留** index 段 |
| `/report/print` | `views/report/print.vue` | 需渲染在布局**之外** |

`/sample` 与 `/assign/index` 一个带 index 一个不带——**同一项目两种约定并存**。
glob 无法用统一规则推出映射；`standalone` 这类元信息 glob 更拿不到。

**判断标准：能否写出一个不会误判的规则？不能就该显式登记。**

显式登记的成本（21 行）远低于「正则猜测 + 改错无编译期保护」的长期代价。

## 四、落地五步（可复制流程）

### Step 1 — 路径注册表 `router/routeRegistry.ts`

```ts
export interface RouteEntry {
  path: string                       // 规范化路径，也是侧栏跳转地址
  name: string                       // 路由 name
  component: RouteRecordRaw['component']
  title: string
  permissions?: string[]             // 省略 = 登录即可访问
  standalone?: boolean               // 渲染在布局之外
  navVisible?: boolean               // 是否出现在侧栏
}

export const ROUTE_REGISTRY: RouteEntry[] = [
  { path: '/dashboard', name: 'dashboard',
    component: () => import('@/views/dashboard/index.vue'), title: '工作台' },
  // ...
]

export const ROUTE_BY_PATH = new Map(ROUTE_REGISTRY.map((e) => [e.path, e]))
```

**旧路径兼容层**（当 DB 里已有历史路径时必需）：

```ts
export const PATH_ALIAS: Readonly<Record<string, string>> = {
  '/sample/register': '/sample',
  '/assign': '/assign/index',
}

export function normalizeMenuPath(raw?: string | null): string | null {
  if (!raw) return null
  // 去查询串 + 尾斜杠，避免 /sample/ 与 /sample 被当成两个路由
  const cleaned = raw.split('?')[0].replace(/\/+$/, '') || '/'
  const canonical = PATH_ALIAS[cleaned] ?? cleaned
  return ROUTE_BY_PATH.has(canonical) ? canonical : null
}
```

> ⚠️ 别名表是**兼容层，不是常态**。若后端补了真实页面，优先改 DB path 使其规范化，
> 而不是往别名表继续加条目。

### Step 2 — 构建器 `router/dynamicRoutes.ts`（纯函数）

**关键：路由与侧栏树一次产出，杜绝两套配置漂移。**

```ts
export function buildNavigation(menus: MenuNode[], permissions: string[]) {
  const permissionSet = new Set(permissions)
  const routes: RouteRecordRaw[] = []
  const standaloneRoutes: RouteRecordRaw[] = []
  const unresolved: { path: string; title: string }[] = []
  const menuTree: MenuTreeNode[] = []

  for (const node of menus) {
    const built = buildNode(node, permissionSet, routes, standaloneRoutes, unresolved)
    if (built) menuTree.push(built)
  }

  // fail-loud：不静默吞掉无法识别的菜单
  if (unresolved.length > 0) {
    console.warn('[dynamicRoutes] 以下菜单在路由注册表中没有对应页面：\n' +
      unresolved.map((u) => `  · ${u.title}（${u.path}）`).join('\n') +
      '\n如需展示请登记到 routeRegistry.ts；若页面未开发请设 visible=0。')
  }
  return { routes, standaloneRoutes, menuTree, unresolved }
}
```

递归要点：
- **目录剪枝**：子节点全部被过滤掉 → 该目录整枝丢弃（否则侧栏出现空分组）
- **权限过滤**：`permissions` 任一命中即可见；与路由守卫口径**必须一致**
- **去重**：同一 path 被多份菜单引用时只注册一次路由（否则 vue-router 报 duplicate）

### Step 3 — router 注册（含 404 重排）

```ts
let registeredRouteNames: string[] = []

function registerNotFound(): void {
  if (router.hasRoute('not-found')) router.removeRoute('not-found')
  router.addRoute({
    path: '/:pathMatch(.*)*', name: 'not-found',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  } as RouteRecordRaw)
}
registerNotFound()   // 启动即注册

export function registerDynamicRoutes(): MenuTreeNode[] {
  const authStore = useAuthStore()
  const { routes, standaloneRoutes, menuTree } = buildNavigation(
    authStore.menus, authStore.permissions)

  // ① 幂等：先清理上次注册（刷新会重进、切账号菜单不同）
  for (const name of registeredRouteNames) {
    if (router.hasRoute(name)) router.removeRoute(name)
  }
  registeredRouteNames = []

  // ② 业务路由挂到根布局 children
  for (const r of routes) {
    const record = { ...r, meta: { ...r.meta, dynamic: true } }
    router.addRoute('root', record)
    registeredRouteNames.push(String(record.name))
  }
  // ③ 布局之外的独立页挂顶层
  for (const r of standaloneRoutes) {
    const record = { ...r, meta: { ...r.meta, dynamic: true } }
    router.addRoute(record)
    registeredRouteNames.push(String(record.name))
  }

  // ④ ⚠️ 把 catch-all 挪回末尾 —— 见第五节「最易踩的坑」
  registerNotFound()

  // ⑤ 回填侧栏树，保证与路由同源
  authStore.setNavMenus(menuTree)
  return menuTree
}
```

### Step 4 — store（导航状态 + 幂等标记）

```ts
const navMenus = shallowRef<MenuTreeNode[]>([])
const navReady = ref(false)

async function login(payload) {
  const result = await loginApi(payload)
  token.value = result.accessToken
  // ⚠️ 切账号必须清空导航状态，否则沿用上一个角色的菜单与路由
  me.value = null; navMenus.value = []; navReady.value = false
}

function logout() {
  token.value = ''; me.value = null
  navMenus.value = []; navReady.value = false
  localStorage.removeItem(TOKEN_KEY)
  resetDynamicRoutes()          // 移除已注册路由
}
```

### Step 5 — 路由守卫（五步，顺序不可换）

```ts
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.public) return true                                  // ①

  if (!authStore.isLoggedIn) {                                     // ②
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!authStore.navReady) {                                       // ③ + ④
    try {
      await authStore.fetchMe()
      registerDynamicRoutes()
      authStore.navReady = true
      // ⚠️ 动态路由刚注册，本次导航是「注册前」解析的（很可能命中 404），
      //    必须 replace 重新解析，否则刷新深链接会闪 404
      return { ...to, replace: true }
    } catch {
      return true   // /me 失败：请求层已处理 401 跳转，这里放行走兜底
    }
  }

  const required = to.meta.permissions                              // ⑤
  if (required?.length && !required.some((p) => authStore.hasPermission(p))) {
    return { name: 'forbidden' }
  }
  return true
})
```

## 五、⚠️ 最易踩的四个坑

### 坑 1（最隐蔽）：catch-all 先注册 → **刷新即 404**

vue-router 4 按**注册顺序**匹配，`addRoute` 一律追加到末尾。
若 `/:pathMatch(.*)*` 在静态路由里先注册，之后动态注册的业务路由全部排在它后面
→ **永远匹配不到**。

**症状**：从菜单点击进入页面正常（路由已存在于内存），**F5 刷新变 404**。

**为什么点击正常而刷新失败**：点击时 `registerDynamicRoutes` 已执行，
vue-router 的 matcher 可能已被内部重排；刷新是全新进程，顺序问题必然暴露。

**解法**：catch-all 不写在静态路由表里，改用 `registerNotFound()` 函数，
每次注册完动态路由后**移除重加**一次。

**验证方法**：**必须测 F5 刷新，不能只测点击**。这是本项目 3.4 节验证清单的第一条。

### 坑 2：切账号残留上一个角色的菜单

`login()` 里若只更新 token 不清 `me`/`navMenus`/`navReady`，
R100 登出后 R3 登录会看到 R100 的菜单（路由表里还留着已注册的动态路由）。

**解法**：`login()` 与 `logout()` 都要清空导航状态 + 调用 `resetDynamicRoutes()`。

### 坑 3：TSDoc 注释里写 glob 模式 → 整个文件语法错误

```ts
/** 为什么不用 `import.meta.glob('@/views/**/*.vue')` ？ */   // ❌
```

`**/*.vue` 中的 `*/` **提前闭合了块注释**，后续 `*.vue')\`` 变成裸代码。
TS 会报几百条 `TS1109: Expression expected`，**行号全部指向注释之后**，极易误判。

**解法**：注释里避免 `*/` 序列（改写表述，不写通配路径）。
**教训**：报错行号 ≠ 出错行。行号挤在一起且指向无关行时，往上看注释。

### 坑 4：`RouteRecordRaw` 联合类型赋值报「redirect is missing」

`RouteRecordRaw` 是联合类型（含 `RouteRecordRedirect`），TS 从字面量反推时
可能匹配到**错误的分支**，报错信息指向 `redirect` 造成误导。

**解法**：
```ts
type RouteComponent = NonNullable<RouteRecordRaw['component']>
return { path, name, component: entry.component as RouteComponent, meta } as RouteRecordRaw
```

## 六、后端配合：菜单表该有哪些字段

| 字段 | 用途 | 注意 |
|---|---|---|
| `id` / `parent_id` | 树结构 | parent_id=0 为根 |
| `title` | 显示名 | |
| `path` | **必须与前端注册表一致** | 不一致就靠前端别名表兜底 |
| `icon` | 图标**名**（非组件） | 组件无法序列化进 DB |
| `menu_type` | 1=目录 / 2=菜单 / 3=按钮 | 查询菜单树时**必须过滤 1/2**，按钮不出树 |
| `permission` | 权限标识 `resource:action` | |
| `sort_order` | 排序 | |
| `visible` | 是否显示 | **无页面的菜单设 0**，避免死链 |
| `deleted` | 逻辑删除 | 所有查询必须带 `deleted = 0` |

后端查询菜单树的 SQL 必须同时满足：`deleted=0 AND visible=1 AND menu_type IN (1,2)`。

**图标名 → 组件映射**用**白名单**，不用 `(ElIcons as any)[name]`：

```ts
const ICON_MAP: Record<string, Component> = { Monitor: markRaw(Monitor), /* ... */ }
const FALLBACK_ICON = markRaw(Files)

function resolveIcon(name?: string): Component {
  return (name && ICON_MAP[name]) || FALLBACK_ICON
}
```
理由：① 动态取会把 400+ 图标全打进产物且无法 tree-shake；
② 后端 icon 是自由文本，拼错会渲染空白且无任何提示。

## 七、验证 Checklist（务必逐条执行）

| # | 验证项 | 方法 | 为什么必须测 |
|---|---|---|---|
| 1 | **F5 刷新深链接** | 在 `/sys/user` 按 F5 | catch-all 顺序问题的唯一暴露方式 |
| 2 | **直接粘贴 URL** | 新开标签访问 `/result/my-tasks` | 未登录 → 登录 → 应回跳原地址 |
| 3 | **切换账号** | R100 登出 → R3 登录 | 菜单/路由残留 |
| 4 | **无权限页面** | R3 访问 `/sys/user` | 应 403，不是 404 也不是白屏 |
| 5 | **旧路径兼容** | 若有别名，测 `/sample/register` | 别名转换是否生效 |
| 6 | **无页面菜单** | 确认侧栏无 `/base/basis` 等死链 | fail-loud 与 visible=0 是否生效 |
| 7 | **控制台无 fail-loud 警告** | 打开 Console | 警告代表有菜单没登记 |
| 8 | **catch-all 在末尾** | 任意不存在的路径 → 404 页 | 不是空白页 |
| 9 | 类型检查 | `vue-tsc --noEmit` | 0 错误 |
| 10 | 构建 | `vite build` | 成功且主包体积无明显增长 |

### 离线纯逻辑断言（不依赖浏览器，建议保留为脚本）

对 `normalizeMenuPath` 做断言，覆盖：
- 全部注册表 path 应解析为自己
- 全部别名应转换到目标
- 无页面的 path 应返回 `null`
- 目录节点（`undefined`）应返回 `null`
- 路径清洗：`/sample/` → `/sample`、`/task?x=1` → `/task`

本项目实测 **31 断言 / 0 失败**。这类断言能覆盖 90% 的路由生成 bug，
且不需要浏览器环境——**优先写这个，再考虑端到端**。
