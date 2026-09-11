import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题（用于 document.title） */
    title?: string
    /** 公开路由（无需登录即可访问，如登录页） */
    public?: boolean
    /** 访问所需权限标识（暂未启用，待动态路由落地后生效） */
    permissions?: string[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'task',
        name: 'supervise-task',
        component: () => import('@/views/task/index.vue'),
        meta: { title: '监抽任务' },
      },
      {
        path: 'sample',
        name: 'sample-register',
        component: () => import('@/views/sample/index.vue'),
        meta: { title: '样品登记' },
      },
      {
        path: 'item/decompose',
        name: 'item-decompose',
        component: () => import('@/views/item/index.vue'),
        meta: { title: '项目分解', permissions: ['item:decompose'] },
      },
      {
        path: 'assign/index',
        name: 'assign-index',
        component: () => import('@/views/assign/index.vue'),
        meta: { title: '任务安排', permissions: ['assign:confirm'] },
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/views/error/403.vue'),
        meta: { title: '无权限访问' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局路由守卫：
 * - 无 Token → /login（携带回跳地址）
 * - 已登录但权限数据未加载 → 先 await fetchMe（保证 v-permission 与动态路由数据就绪，T-102）
 * - 无菜单权限 → /403（待动态路由落地后按 /me 菜单生成路由时启用）
 */
router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!authStore.me) {
    try {
      await authStore.fetchMe()
    } catch {
      // /me 失败（如 token 过期）：请求层已统一处理 401 跳转，这里放行由后续拦截兜底
      return true
    }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} - LIMS 实验室信息管理系统` : 'LIMS 实验室信息管理系统'
})

export default router
