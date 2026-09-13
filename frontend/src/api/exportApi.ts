/**
 * 导出域（Export）接口封装 —— T-802 省平台上报 / T-603 检验员任务
 *
 * 契约见 docs/api/api-spec.md 第 8 章（/api/export/*）：
 *   - 省平台上报  GET /export/province?taskNo=   权限 export:province
 *   - 我的任务    GET /export/my-tasks           权限 result:export-excel
 * 返回二进制流（xlsx），非统一 { code, msg, data } 结构。
 *
 * 为何单独建一个 axios 实例：request.ts 的响应拦截器假定响应体是 JSON 信封（读 body.code），
 * 对 { responseType: 'blob' } 的成功响应会误判为业务错误。这里用一条带 JWT 的裸调用专走下载，
 * 并把响应头里的文件名随 Blob 带回（见 utils/download.ts），保持各 export*Api 返回 Promise<Blob>。
 */
import axios, { AxiosError, type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY } from '@/utils/request'
import { parseContentDispositionFilename, rememberFilename } from '@/utils/download'

/** 下载专用 axios 实例（同源经 vite 代理 /api → 后端） */
const downloadInstance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60000,
})

downloadInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

/** 失败响应体可能是 JSON（后端统一 R 封装）被当作 Blob 返回，此处尝试解析出 msg */
async function extractErrorMsg(error: AxiosError<Blob>): Promise<string> {
  const data = error.response?.data
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      const parsed = JSON.parse(text) as { msg?: string }
      if (parsed.msg) {
        return parsed.msg
      }
    } catch {
      // 非 JSON（如空体/二进制错误页），忽略，走默认信息
    }
  }
  return error.message || '下载失败，请稍后重试'
}

downloadInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<Blob>) => {
    const msg = await extractErrorMsg(error)
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
)

/** 省平台上报导出参数 */
export interface ProvinceExportParams {
  /** 任务编号（精确筛选，可选） */
  taskNo?: string
}

/** 发起一次 blob 请求，并把 Content-Disposition 文件名随 Blob 记录后返回 */
async function requestBlob(url: string, params?: Record<string, unknown>): Promise<Blob> {
  const res = await downloadInstance.get<Blob>(url, { params, responseType: 'blob' })
  const disposition = res.headers['content-disposition']
  const filename = parseContentDispositionFilename(typeof disposition === 'string' ? disposition : null)
  return rememberFilename(res.data, filename)
}

/** 省平台上报导出：GET /export/province（返回 xlsx Blob） */
export function exportProvinceApi(params?: ProvinceExportParams): Promise<Blob> {
  return requestBlob('/export/province', params ? { taskNo: params.taskNo } : undefined)
}

/** 检验员任务导出：GET /export/my-tasks（返回 xlsx Blob） */
export function exportMyTasksApi(): Promise<Blob> {
  return requestBlob('/export/my-tasks')
}
