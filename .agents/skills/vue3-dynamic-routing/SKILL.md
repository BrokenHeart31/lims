---
name: vue3-dynamic-routing
description: 在 Vue 3 + Vue Router 4 + RBAC 后台项目中，按后端返回的菜单树落地「路径注册表 + 中间件转换」动态路由：建立 menu path → 组件的稳定映射、旧路径别名兼容、路由与侧栏菜单同源产出、404 catch-all 动态重排、切换账号清理。当需要做动态路由、按权限生成菜单/路由、或排查「刷新变 404」「菜单点了没反应」「切账号菜单残留」等问题时使用。
agent_created: true
---

# Vue 3 动态路由：路径注册表模式

## 触发场景

- 要把后端菜单树（`/api/auth/me` 之类）变成路由 + 侧栏
- 遇到以下任一现象：
  - 从菜单点击能进页面，**F5 刷新变 404**
  - 侧栏有菜单但点了没反应（页面不存在）
  - 切换账号后看到上一个角色的菜单
  - `vue-tsc` 报几百条 `TS1109: Expression expected`（注释里有多个 `*/` 序列）
  - `RouteRecordRaw` 赋值报 `redirect is missing`
- 后端菜单表的 `path` 与前端路由**不完全一致**

## 前置判断：先用 glob 还是显式注册表？

**先查路径约定是否统一**。执行：

```bash
# 列出后端菜单 path
mysql ... -e "SELECT id,path,title FROM sys_menu WHERE deleted=0 AND menu_type IN (1,2);"
# 列出实际页面文件
find src/views -name "*.vue" | sort
```

逐条对齐后判断：

| 情况 | 选择 |
|---|---|
| path 与文件路径**严格一对一**（去掉扩展名即可） | 可以用 `import.meta.glob` |
| 存在 `index.vue` 但 URL 不含 index（或反之，且**规则不统一**） | **用显式注册表** |
| 有页面需要渲染在布局之外 / 有额外 meta | **用显式注册表**（glob 表达不了） |

> 判断标准：**能否写出一个不会误判的规则？不能就显式登记。**
>
> 实测反例：某项目 `/sample` → `sample/index.vue`（去 index），
> 但 `/assign/index` → `assign/index.vue`（留 index）。两种约定并存时 glob 必然误判。

## 落地五步

### Step 1 — `src/router/routeRegistry.ts`（注册表 + 别名）

```ts
import type { RouteRecordRaw } from 'vue-router'

export interface RouteEntry {
  path: string
  name: string
  component: RouteRecordRaw['component']
  title: string
  permissions?: string[]      // 省略 = 登录即可访问
  standalone?: boolean        // 渲染在布局之外（如打印页）
  navVisible?: boolean        // 是否出现在侧栏
}

export const ROUTE_REGISTRY: RouteEntry[] = [
  { path: '/dashboard', name: 'dashboard',
    component: () => import('@/views/dashboard/index.vue'), title: '工作台' },
  { path: '/sys/user', name: 'sys-user',
    component: () => import('@/views/system/user.vue'), title: '用户管理',
    permissions: ['sys:user:list'] },
  // ⚠️ 每个页面都必须在此登记，否则动态路由无法生成（会有 fail-loud 警告）
]

export const ROUTE_BY_PATH = new Map(ROUTE_REGISTRY.map((e) => [e.path, e]))

/** 旧路径/别名 → 规范路径。兼容层，不是常态 */
export const PATH_ALIAS: Readonly<Record<string, string>> = {
  '/sample/register': '/sample',
}

export function normalizeMenuPath(raw?: string | null): string | null {
  if (!raw) return null
  const cleaned = raw.split('?')[0].replace(/\/+$/, '') || '/'
  const canonical = PATH_ALIAS[cleaned] ?? cleaned
  return ROUTE_BY_PATH.has(canonical) ? canonical : null
}
```

> ⚠️ **注释里不要写 glob 模式或正则**（`**/*.vue` 中的 `*/` 会提前闭合块注释，
> 导致整个文件语法错误且报错行号指向注释之后，极难定位）。

### Step 2 — `src/router/dynamicRoutes.ts`（纯函数构建器）

要点：**路由与侧栏树一次产出**（分成两处必然漂移）+ **目录剪枝** + **fail-loud**。

```ts
export interface MenuTreeNode {
  id: number; title: string; path?: string; icon?: string; children: MenuTreeNode[]
}

export function buildNavigation(menus: MenuNode[], permissions: string[]) {
  const permissionSet = new Set(permissions)
  const routes: RouteRecordRaw[] = []
  const standaloneRoutes: RouteRecordRaw[] = []
  const unresolved: { path: string; title: string }[] = []
  const menuTree: MenuTreeNode[] = []

  for (const node of menus) {
    const built = buildNode(node, permissionSet, routes, standaloneRoutes, unresolved)
    if (built) menuTree.push(built)        // 剪枝：返回 null 的整枝丢弃
  }

  if (unresolved.length) {
    console.warn(
      '[dynamicRoutes] 以下菜单在路由注册表中无对应页面，已在侧栏忽略：\n' +
      unresolved.map((u) => `  · ${u.title}（${u.path}）`).join('\n') +
      '\n如需展示请登记到 routeRegistry.ts；页面未开发请把菜单设 visible=0。')
  }
  return { routes, standaloneRoutes, menuTree, unresolved }
}
```

递归 `buildNode` 的三种情况：
- **path 命中注册表** → 生成路由 + 菜单项；无权限返回 `null`
- **path 为空或未命中，但有子节点** → 目录容器（`path: undefined`）
- **既无有效 path 也无子节点** → 加入 `unresolved`，返回 `null`

组件类型断言（避开 union 报错）：
```ts
type RouteComponent = NonNullable<RouteRecordRaw['component']>
return { path, name, component: entry.component as RouteComponent, meta } as RouteRecordRaw
```

### Step 3 — `src/router/index.ts`（注册 + 404 重排）

```ts
const staticRoutes: RouteRecordRaw[] = [
  { path: '/login', name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true } },
  { path: '/', name: 'root',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: '403', name: 'forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { title: '无权限访问' } },
    ] },
  // ⚠️ catch-all 不放这里！见下方「坑 1」
]

const router = createRouter({ history: createWebHistory(), routes: staticRoutes })

let registeredRouteNames: string[] = []

/** 注册 catch-all，确保它在所有路由之后 */
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
  const { routes, standaloneRoutes, menuTree } =
    buildNavigation(authStore.menus, authStore.permissions)

  // ① 幂等：清掉上次注册的
  for (const name of registeredRouteNames) {
    if (router.hasRoute(name)) router.removeRoute(name)
  }
  registeredRouteNames = []

  // ② 业务路由挂到根布局
  for (const r of routes) {
    const record = { ...r, meta: { ...r.meta, dynamic: true } }
    router.addRoute('root', record)
    registeredRouteNames.push(String(record.name))
  }
  // ③ 布局外独立页挂顶层
  for (const r of standaloneRoutes) {
    const record = { ...r, meta: { ...r.meta, dynamic: true } }
    router.addRoute(record)
    registeredRouteNames.push(String(record.name))
  }
  // ④ 把 catch-all 挪回末尾（关键！）
  registerNotFound()
  // ⑤ 回填侧栏树
  authStore.setNavMenus(menuTree)
  return menuTree
}

export function resetDynamicRoutes(): void {
  for (const name of registeredRouteNames) {
    if (router.hasRoute(name)) router.removeRoute(name)
  }
  registeredRouteNames = []
}
```

### Step 4 — store（导航状态）

```ts
const navMenus = shallowRef<MenuTreeNode[]>([])
const navReady = ref(false)

async function login(payload) {
  const result = await loginApi(payload)
  token.value = result.accessToken
  localStorage.setItem(TOKEN_KEY, result.accessToken)
  // ⚠️ 切账号必须清空，否则沿用上一个角色的菜单与路由
  me.value = null; navMenus.value = []; navReady.value = false
}

function logout() {
  token.value = ''; me.value = null
  navMenus.value = []; navReady.value = false
  localStorage.removeItem(TOKEN_KEY)
  resetDynamicRoutes()
}

// 供 router 回填（保证菜单与路由同源）
function setNavMenus(tree: MenuTreeNode[]) { navMenus.value = tree }
```

### Step 5 — 路由守卫（五步，顺序不可换）

```ts
router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) return true
  if (!authStore.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }

  if (!authStore.navReady) {
    try {
      await authStore.fetchMe()
      registerDynamicRoutes()
      authStore.navReady = true
      // ⚠️ 必须 replace 重解析：本次导航在路由注册前已解析，很可能命中 404
      return { ...to, replace: true }
    } catch { return true }
  }

  const required = to.meta.permissions
  if (required?.length && !required.some((p) => authStore.hasPermission(p))) {
    return { name: 'forbidden' }
  }
  return true
})
```

## ⚠️ 四个高频坑

### 坑 1：catch-all 先注册 → F5 刷新必 404

vue-router 4 按**注册顺序**匹配，`addRoute` 一律**追加**。
catch-all 若先注册，动态路由全排在它后面 → 永远匹配不到。

- **症状**：菜单点击正常（路由已在内存），**F5 刷新变 404**
- **解法**：catch-all 用 `registerNotFound()` 函数，每次注册动态路由后移除重加
- **验证**：**必须按 F5**，只测点击发现不了

### 坑 2：切账号菜单残留

`login()` 只更新 token 不清导航状态 → R100 登出、R3 登录后看到 R100 菜单。
**解法**：`login()` 与 `logout()` 都清 `me`/`navMenus`/`navReady` + `resetDynamicRoutes()`。

### 坑 3：注释里的 `*/` 序列毁掉整个文件

```ts
/** 为什么不用 import.meta.glob('@/views/**/*.vue') ？ */   // ❌ `**/` 提前闭合注释
```
报几百条 `TS1109: Expression expected`，**行号全挤在注释之后**。
**解法**：注释里不写通配路径；报错行号异常密集时往上找注释。

### 坑 4：`RouteRecordRaw` union 报 `redirect is missing`

TS 从字面量反推时匹配到 `RouteRecordRedirect` 分支，报错误导。
**解法**：`component as NonNullable<RouteRecordRaw['component']>` + 整体 `as RouteRecordRaw`。

## 后端菜单表要求

```sql
-- 查菜单树必须同时过滤三者
SELECT * FROM sys_menu
WHERE deleted = 0 AND visible = 1 AND menu_type IN (1, 2)   -- 按钮(3)不出树
ORDER BY sort_order, id;
```

- **无前端页面的菜单设 `visible = 0`**，避免侧栏死链；页面做好后改回 1（无需改代码）
- `icon` 存**图标名**（组件无法序列化进 DB）

前端图标解析用**白名单**（不用 `(ElIcons as any)[name]`）：
```ts
const ICON_MAP: Record<string, Component> = { Monitor: markRaw(Monitor), /* ... */ }
const FALLBACK_ICON = markRaw(Files)
const resolveIcon = (name?: string) => (name && ICON_MAP[name]) || FALLBACK_ICON
```
理由：① 动态取会把 400+ 图标打进产物且无法 tree-shake；② 后端拼错名字会渲染空白且无提示。

## 验证 Checklist

| # | 项目 | 方法 |
|---|---|---|
| 1 | **F5 刷新深链接** | 在 `/sys/user` 按 F5（catch-all 问题唯一暴露方式） |
| 2 | **直接粘贴 URL** | 新标签访问，未登录应登录后回跳 |
| 3 | **切换账号** | R100 登出 → 其他角色登录，菜单应完全不同 |
| 4 | **无权限页面** | 低权角色访问高权页 → 403（非 404/白屏） |
| 5 | 旧路径兼容 | 若有别名，测旧 path |
| 6 | 侧栏无死链 | 无页面的菜单应已隐藏 |
| 7 | Console 无 fail-loud 警告 | 有警告 = 有菜单没登记 |
| 8 | 不存在路径 → 404 页 | 非空白 |
| 9 | `vue-tsc --noEmit` | 0 错误 |
| 10 | `vite build` | 成功，主包体积无明显增长 |

### 离线纯逻辑断言（强烈建议写，不需要浏览器）

对 `normalizeMenuPath` 断言，取**真实的** DB path 列表跑一遍：
- 全部注册表 path 解析为自己
- 全部别名转换到目标
- 无页面的 path → `null`
- 目录节点（`undefined`）→ `null`
- 清洗：`/sample/` → `/sample`、`/task?x=1` → `/task`

实测 31 条断言 / 0 失败，覆盖约 90% 的路由生成 bug，且无需浏览器环境。
**优先写这个，再考虑端到端。**

## 数据迁移配套（当 DB path 与前端不一致时）

改动要**最小**（用户通常明确要求「不为动态路由大规模改数据库」）：

```sql
-- ① 只改真正不一致的 path（先 SELECT 比对出来）
UPDATE sys_menu SET path='/sample', icon='Document', updated_at=NOW()
WHERE id=3 AND deleted=0;
-- ② 无页面菜单隐藏
UPDATE sys_menu SET visible=0, updated_at=NOW() WHERE id IN (91,92,115) AND deleted=0;
-- ③ 补缺失菜单（有页面无菜单项的情况）
INSERT IGNORE INTO sys_menu (id,parent_id,title,path,icon,menu_type,permission,sort_order,visible,created_by,created_at,updated_by,updated_at,deleted)
VALUES (94, 9, '项目标准库', '/base/product-lib', 'Files', 2, NULL, 4, 1, 'seed', NOW(), 'seed', NOW(), 0);
-- ④ 同步授权（R100 全量 + 相关角色）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 1, m.id FROM sys_menu m;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (3,94);
```

**seed 文件与活库都要改**：seed 是重建环境的真相，活库是当前环境的真相，缺一不可。
