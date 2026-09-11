import axios, { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'

/** Token 在 localStorage 中的键名（禁止 Token 出现在 URL 中） */
export const TOKEN_KEY = 'lims_access_token'

/** 业务错误：携带后端统一响应的 code/msg */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, msg: string) {
    super(msg)
    this.name = 'ApiError'
    this.code = code
  }
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// 请求拦截：附加 JWT
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/** 401 未认证：清除本地凭证并跳转登录页（带回跳地址） */
function handleUnauthorized(): void {
  localStorage.removeItem(TOKEN_KEY)
  if (!window.location.pathname.startsWith('/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
  }
}

// 响应拦截：解包统一响应结构 { code, msg, data }
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data
    if (body.code === 401) {
      handleUnauthorized()
      return Promise.reject(new ApiError(401, body.msg || '登录已过期，请重新登录'))
    }
    if (body.code !== 0) {
      const msg = body.msg || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new ApiError(body.code, msg))
    }
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401) {
      handleUnauthorized()
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
