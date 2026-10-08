import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'

/** Access Token 在 localStorage 中的键名（禁止 Token 出现在 URL 中） */
export const TOKEN_KEY = 'lims_access_token'

/** Refresh Token 在 localStorage 中的键名（F8 静默续期用，与 TOKEN_KEY 成对读写） */
export const REFRESH_TOKEN_KEY = 'lims_refresh_token'

/** 业务错误：携带后端统一响应的 code/msg */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, msg: string) {
    super(msg)
    this.name = 'ApiError'
    this.code = code
  }
}

/** 可标记「已重放过」的请求配置（防 401 重放死循环） */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// 请求拦截：附加 JWT（续期成功后会覆盖为最新 token）
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/** 认证端点：命中即直接硬登出（不尝试续期，避免递归与无谓重试） */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh']

function isAuthEndpoint(url?: string): boolean {
  if (!url) return false
  return AUTH_ENDPOINTS.some((endpoint) => url.includes(endpoint))
}

/** 401 未认证：清除本地凭证（access + refresh）并跳转登录页（带回跳地址） */
function handleUnauthorized(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  if (!window.location.pathname.startsWith('/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
  }
}

// ---------------------------------------------------------------------------
// F8 静默续期：accessToken 过期（401）时用 refreshToken 换新，并重放原请求一次
// ---------------------------------------------------------------------------

/**
 * 单飞锁：并发多个 401 只触发一次 `/auth/refresh`，其余共同等待同一个 Promise。
 * 刷新结束（无论成败）后归零，允许下一轮过期再次刷新。
 */
let refreshPromise: Promise<string | null> | null = null

/**
 * 用 refreshToken 换取新的 accessToken。
 *
 * 用**裸 axios**（函数式 `axios.post`，非本 `instance`）发起，避免再次经过本实例的
 * 响应拦截器造成递归；成功时写回两个 token（access + 轮换后的 refresh），失败返回 `null`。
 */
async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken) return null
  try {
    const base = import.meta.env.VITE_API_BASE_URL ?? ''
    const res = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
      `${base}/auth/refresh`,
      { refreshToken },
      { timeout: 15000 },
    )
    const body = res.data
    if (body.code !== 0 || !body.data?.accessToken) return null
    localStorage.setItem(TOKEN_KEY, body.data.accessToken)
    if (body.data.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, body.data.refreshToken)
    }
    return body.data.accessToken
  } catch {
    return null
  }
}

/** 取（或触发）单飞刷新 Promise */
function getRefreshedToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

/**
 * 401 处理：尝试静默续期并重放原请求一次；失败或认证端点则硬登出。
 *
 * @returns 成功时返回**重放请求**的响应；失败时以 `ApiError(401)` 拒绝
 */
async function retryAfterRefresh(
  config: InternalAxiosRequestConfig,
  fallbackMsg: string,
): Promise<AxiosResponse> {
  const retriable = config as RetriableConfig
  if (retriable._retry || isAuthEndpoint(config.url)) {
    handleUnauthorized()
    return Promise.reject(new ApiError(401, fallbackMsg))
  }
  const token = await getRefreshedToken()
  if (!token) {
    handleUnauthorized()
    return Promise.reject(new ApiError(401, fallbackMsg))
  }
  // 标记已重放：请求拦截器会用新 token 重新附加 Authorization，避免死循环
  retriable._retry = true
  return instance.request(retriable)
}

// 响应拦截：解包统一响应结构 { code, msg, data }
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data
    if (body.code === 401) {
      // 业务码 401：先尝试静默续期（认证端点除外），失败再硬登出
      return retryAfterRefresh(response.config, body.msg || '登录已过期，请重新登录')
    }
    if (body.code !== 0) {
      const msg = body.msg || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new ApiError(body.code, msg))
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401 && error.config) {
      // HTTP 401：同样走静默续期 + 重放
      return retryAfterRefresh(
        error.config,
        error.response.data?.msg || '登录已过期，请重新登录',
      )
    }
    const msg = error.response?.data?.msg || error.message || '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(new ApiError(error.response?.status ?? 500, msg))
  },
)

/** GET 请求：返回解包后的 data */
export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await instance.get<ApiResponse<T>>(url, { params })
  return res.data.data
}

/** POST 请求：返回解包后的 data */
export async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await instance.post<ApiResponse<T>>(url, data)
  return res.data.data
}

/** PUT 请求：返回解包后的 data */
export async function put<T>(url: string, data?: unknown): Promise<T> {
  const res = await instance.put<ApiResponse<T>>(url, data)
  return res.data.data
}

/** DELETE 请求：返回解包后的 data */
export async function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await instance.delete<ApiResponse<T>>(url, { params })
  return res.data.data
}

export default instance
