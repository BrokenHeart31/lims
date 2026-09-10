import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchMeApi,
  loginApi,
  type LoginPayload,
  type MeResult,
} from '@/api/auth'
import { TOKEN_KEY } from '@/utils/request'

/**
 * 认证状态
 * - token 持久化于 localStorage；401 时由 request.ts 拦截器清除并跳转登录
 * - 动态路由（菜单权限）待 T-002 /me 契约落地后接入 router
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const me = ref<MeResult | null>(null)

  const isLoggedIn = computed(() => token.value !== '')
  const userInfo = computed(() => me.value?.user ?? null)
  /** 权限标识集合（resource:action） */
  const permissions = computed<string[]>(() => me.value?.permissions ?? [])
  /** 菜单树（动态路由数据源） */
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
  }

  /** 拉取当前用户信息（角色 / 权限标识 / 菜单树） */
  async function fetchMe(): Promise<void> {
    me.value = await fetchMeApi()
  }

  /** 退出登录：清空本地状态 */
  function logout(): void {
    token.value = ''
    me.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    me,
    isLoggedIn,
    userInfo,
    permissions,
    menus,
    hasPermission,
    login,
    fetchMe,
    logout,
  }
})
