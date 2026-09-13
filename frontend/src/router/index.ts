import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ROUTE_BY_PATH } from './routeRegistry'
import { buildNavigation, type MenuTreeNode } from './dynamicRoutes'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题（用于 document.title） */
    title?: string
    /** 公开路由（无需登录即可访问，如登录页） */
    public?: boolean
    /** 访问所需权限标识（任一命中即可；省略 = 登录即可访问） */
    permissions?: string[]
    /** 标记为动态注册的路由，便于登出时精确清理 */
    dynamic?: boolean
  }
}

/**
 * 静态路由：应用外壳 + 无需权限的兜底页。
 *
 * 设计：这里**只**保留与权限/菜单无关的骨架路由。
 * 所有业务页面由 `/api/auth/me` 的菜单树动态注册（见 registerDynamicRoutes）。
 *
 * 为什么不把业务路由留在静态表里「动态和静态并存」？——两份来源必然漂移：
 * 用户从菜单点进 A 是动态生成的路由，手敲地址进 A 命中的是静态路由，
 * 两者的 meta/权限判断一旦不一致就会出「同一页面两种行为」的怪 bug。
 */
const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    name: 'root',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { title: '无权限访问' },
      },
    ],
  },
  // ⚠️ 404 catch-all 刻意**不放在这里**。
  //    vue-router 4 按「先注册者优先」匹配；动态路由是 addRoute 追加的，
  //    若 catch-all 先注册，所有业务页面都会被它吃掉（表现为「刷新即 404」）。
  //    正确做法：先把 catch-all 注册在最后（registerNotFound 函数），
  //    每次注册动态路由后再把它移除重加，始终保持它在末尾。
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
})

/** 已动态注册的路由名集合 —— 登出/切换账号时据此精确移除，避免残留 */
let registeredRouteNames: string[] = []

/**
 * 注册（或重新注册）404 兜底，确保它排在所有路由之后。
 *
 * 为什么每次都要移除重加：vue-router 4 内部维护一个有序的 matcher 列表，
 * `addRoute` 一律追加到末尾。动态路由注册后 catch-all 就不再是最后一个了，
 * 必须挪一次，否则深链接刷新会先进 404。
 */
function registerNotFound(): void {
  if (router.hasRoute('not-found')) router.removeRoute('not-found')
  router.addRoute({
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  } as RouteRecordRaw)
}

// 应用启动即注册一次，保证「未登录访问任意地址」也能得到 404 而不是空白
registerNotFound()

/**
 * 按当前用户的菜单树注册业务路由。
 *
 * 幂等：重复调用会先移除上一次注册的路由。这是必需的——
 *   · 刷新页面后守卫会再调一次；
 *   · 切换账号（不同角色）后菜单不同，必须换成新的一套。
 *
 * @returns 侧栏菜单树（供 MainLayout 消费），保证与路由同源
 */
export function registerDynamicRoutes(): MenuTreeNode[] {
  const authStore = useAuthStore()
  const { routes, standaloneRoutes, menuTree } = buildNavigation(
    authStore.menus,
    authStore.permissions,
  )

  // ① 清理上一次注册的路由（含可能残留的 top-level 独立页）
  for (const name of registeredRouteNames) {
    if (router.hasRoute(name)) router.removeRoute(name)
  }
  registeredRouteNames = []

  // ② 业务路由挂在根布局的 children 下
  const root = router.getRoutes().find((r) => r.name === 'root')
  if (root) {
    const rootRecord = router.options.routes.find((r) => r.name === 'root')
    if (rootRecord) {
      rootRecord.children = (rootRecord.children ?? []).filter((c) => !c.meta?.dynamic)
      for (const r of routes) {
        const record = { ...r, meta: { ...r.meta, dynamic: true } }
        rootRecord.children.push(record)
        router.addRoute('root', record)
        registeredRouteNames.push(String(record.name))
      }
    }
  }

  // ③ 独立于外壳的页面（如报告打印）直接挂顶层
  for (const r of standaloneRoutes) {
    const record = { ...r, meta: { ...r.meta, dynamic: true } }
    router.addRoute(record)
    registeredRouteNames.push(String(record.name))
  }

  // ④ 把 404 兜底挪回末尾（动态路由刚追加在它后面，必须重排）
  registerNotFound()

  // ⑤ 回填侧栏菜单树：与刚注册的路由同源，从根本上排除「菜单与路由两套配置」的漂移
  authStore.setNavMenus(menuTree)

  return menuTree
}

/** 登录成功后由 stores/auth 调用，标记导航数据已就绪 */
export function resetDynamicRoutes(): void {
  for (const name of registeredRouteNames) {
    if (router.hasRoute(name)) router.removeRoute(name)
  }
  registeredRouteNames = []
}

/**
 * 全局路由守卫。
 *
 * 顺序（每一步都有必要，不可调换）：
 *   ① 公开路由直接放行
 *   ② 未登录 → /login（带 redirect 回跳）
 *   ③ 已登录但导航未就绪 → 先 await fetchMe（拿菜单/权限）
 *   ④ 注册动态路由（刷新后必须重新注册，否则深链接命中 404）
 *   ⑤ 权限不足 → /403
 */
router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.public) return true

  if (!authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // ③ + ④ 只在需要时加载一次：用 authStore.navReady 标记，避免每次跳转都重算路由
  if (!authStore.navReady) {
    try {
      await authStore.fetchMe()
      registerDynamicRoutes()
      authStore.navReady = true
      // 动态路由刚注册完，当前这次导航是「未注册前」解析的（很可能命中 404），
      // 必须用 replace 重新解析一次，否则刷新深链接会看到 404 闪一下。
      return { ...to, replace: true }
    } catch {
      // /me 失败（如 token 过期）：请求层已统一处理 401 跳转，这里放行走兜底
      return true
    }
  }

  // ⑤ 路由级权限校验（与动态路由构建时的过滤口径一致）
  const required = to.meta.permissions
  if (required && required.length > 0 && !required.some((p) => authStore.hasPermission(p))) {
    return { name: 'forbidden' }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} - LIMS 实验室信息管理系统` : 'LIMS 实验室信息管理系统'
})

/** 供 MainLayout / 侧栏使用：某 path 是否属于已登记路由（用于 isActive 等判断） */
export function isRegisteredPath(path: string): boolean {
  return ROUTE_BY_PATH.has(path)
}

export default router
