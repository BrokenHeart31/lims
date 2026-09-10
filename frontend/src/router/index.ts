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
 * - 无菜单权限 → /403（待 T-002 契约落地后按 /me 菜单动态生成路由时启用）
 */
router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} - LIMS 实验室信息管理系统` : 'LIMS 实验室信息管理系统'
})

export default router
