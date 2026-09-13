import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchMeApi,
  loginApi,
  type LoginPayload,
  type MeResult,
} from '@/api/auth'
import type { MenuTreeNode } from '@/router/dynamicRoutes'
import { resetDynamicRoutes } from '@/router'
import { TOKEN_KEY } from '@/utils/request'

/**
 * 认证状态
 * -----------------------------------------------------------------------------
 * 职责划分（避免循环依赖，务必保持）：
 *   · 本 store 只负责「保存 token / 用户 / 权限 / 后端原始菜单树」
 *   · 「菜单树 → 路由 + 侧栏树」的转换在 router/dynamicRoutes.ts（纯函数）
 *   · 「把路由注册进 router」由 router/index.ts 的 registerDynamicRoutes 完成
 * 因此这里不 import router 的具体注册函数（只 import 清理函数，方向是 store→router 单向）。
 *
 * navReady 的含义：本次登录会话内，动态路由是否已注册完成。
 * 路由守卫据此决定是否需要等待 fetchMe + registerDynamicRoutes。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const me = ref<MeResult | null>(null)

  /** 侧栏菜单树（由 router 注册动态路由时回填，保证与路由同源） */
  const navMenus = shallowRef<MenuTreeNode[]>([])
  /** 动态路由是否已注册（路由守卫据此决定是否等待） */
  const navReady = ref(false)

  const isLoggedIn = computed(() => token.value !== '')
  const userInfo = computed(() => me.value?.user ?? null)
  /** 权限标识集合（resource:action） */
  const permissions = computed<string[]>(() => me.value?.permissions ?? [])
  /** 后端原始菜单树（未规范化；侧栏请用 navMenus） */
  const menus = computed(() => me.value?.menus ?? [])

  /** 按钮级权限判断：前端只做显隐，安全由后端兜底 */
  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  /** 登录成功后保存 token */
  async function login(payload: LoginPayload): Promise<void> {
    const result = await loginApi(payload)
    token.value = result.accessToken
    localStorage.setItem(TOKEN_KEY, result.accessToken)
    // 切换账号必须清空旧导航状态，否则会沿用上一个角色的菜单/路由
    me.value = null
    navMenus.value = []
    navReady.value = false
  }

  /** 拉取当前用户信息（角色 / 权限标识 / 菜单树） */
  async function fetchMe(): Promise<void> {
    me.value = await fetchMeApi()
  }

  /** 退出登录：清空本地状态 + 移除已注册的动态路由 */
  function logout(): void {
    token.value = ''
    me.value = null
    navMenus.value = []
    navReady.value = false
    localStorage.removeItem(TOKEN_KEY)
    resetDynamicRoutes()
  }

  return {
    token,
    me,
    navMenus,
    navReady,
    isLoggedIn,
    userInfo,
    permissions,
    menus,
    hasPermission,
    login,
    fetchMe,
    logout,
    /** 供 router 在注册动态路由后回填侧栏树（保证菜单与路由同源） */
    setNavMenus(tree: MenuTreeNode[]): void {
      navMenus.value = tree
    },
  }
})