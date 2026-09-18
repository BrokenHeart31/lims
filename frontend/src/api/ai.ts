/**
 * AI 助手接口封装（feature A，api-spec 第 16 章 `/api/ai`）。
 *
 * 设计要点：
 *   · `/ai/status` **静默探测**——用原生 fetch 绕过 axios 响应拦截器，失败绝不弹全局错误
 *     （离线是正常状态，业务页照常工作；见设计 §2.7 / A1 fail-soft 契约）。
 *   · 其余接口走统一 `request`（携带 JWT、解包 `{code,msg,data}`、非 0 弹提示）。
 *   · 上传接口是 multipart，使用 axios 实例直发 FormData。
 */
import instance, { get, post, del, TOKEN_KEY } from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  AiAnswerVO,
  AiChatContext,
  AiChatDTO,
  AiCompanionFeedbackDTO,
  AiCompanionHint,
  AiConversationVO,
  AiMessageVO,
  AiStatusVO,
  FlowGuide,
  GbDocumentVO,
  GbImportJobVO,
  GbSearchHitVO,
  KbScanDTO,
  KbSearchDTO,
  ScanOcrJob,
  ScanOcrRetry,
} from '@/types/ai'

/** 与请求层一致的 API 基址（开发环境由 Vite 代理） */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/** A1 服务健康检查（**静默**：不弹全局错误，失败由调用方降级处理） */
export async function getAiStatusApi(): Promise<AiStatusVO> {
  const token = localStorage.getItem(TOKEN_KEY)
  const resp = await fetch(`${BASE_URL}/ai/status`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })
  if (!resp.ok) {
    throw new Error(`状态探测失败（HTTP ${resp.status}）`)
  }
  const body = (await resp.json()) as ApiResponse<AiStatusVO>
  if (body.code !== 0) {
    throw new Error(body.msg || '状态探测失败')
  }
  return body.data
}

/** A2 非流式问答（自检/降级） */
export function aiChatApi(body: AiChatDTO): Promise<AiAnswerVO> {
  return post<AiAnswerVO>('/ai/chat', body)
}

/** A4 分页查询会话（审计） */
export function pageAiConversationsApi(current: number, size: number): Promise<PageResult<AiConversationVO>> {
  return get<PageResult<AiConversationVO>>('/ai/conversations', { current, size })
}

/** A5 会话消息明细 */
export function listAiMessagesApi(conversationId: number): Promise<AiMessageVO[]> {
  return get<AiMessageVO[]>(`/ai/conversations/${conversationId}/messages`)
}

/** A6 上传文本文件建索引（multipart，字段名 file） */
export async function uploadKbFileApi(file: File, sourceType?: number): Promise<number> {
  const form = new FormData()
  form.append('file', file)
  const resp = await instance.post<ApiResponse<number>>('/ai/kb/import/upload', form, {
    params: sourceType == null ? undefined : { sourceType },
  })
  return resp.data.data
}

/** A7 扫描目录批量导入 */
export function scanKbDirApi(body?: KbScanDTO): Promise<number> {
  return post<number>('/ai/kb/import/scan', body ?? {})
}

/** A8 分页查询导入任务与进度 */
export function pageKbJobsApi(current: number, size: number): Promise<PageResult<GbImportJobVO>> {
  return get<PageResult<GbImportJobVO>>('/ai/kb/import/jobs', { current, size })
}

/** A9 单任务详情（含失败明细） */
export function getKbJobApi(id: number): Promise<GbImportJobVO> {
  return get<GbImportJobVO>(`/ai/kb/import/jobs/${id}`)
}

/** A10 失败重试 */
export function retryKbJobApi(id: number): Promise<boolean> {
  return post<boolean>(`/ai/kb/import/jobs/${id}/retry`)
}

/** A11 分页查询已入库标准 */
export function pageKbDocumentsApi(
  current: number,
  size: number,
  stdNo?: string,
): Promise<PageResult<GbDocumentVO>> {
  return get<PageResult<GbDocumentVO>>('/ai/kb/documents', {
    current,
    size,
    ...(stdNo ? { stdNo } : {}),
  })
}

/** A12 直接检索标准条款（页面联动/调试） */
export function searchKbApi(body: KbSearchDTO): Promise<GbSearchHitVO[]> {
  return post<GbSearchHitVO[]>('/ai/kb/search', body)
}

/** A13 删除文档索引（可重建） */
export function deleteKbDocumentApi(id: number): Promise<boolean> {
  return del<boolean>(`/ai/kb/documents/${id}`)
}

// ===========================================================================
// 增量 ai_flow_assistant：标准伴随 / 流程引导 / 扫描件 OCR 任务（A14~A19）
// ===========================================================================

/** A14 标准伴随查询（上下文触发；`itemName` 空 → 后端返回 `[]`，fail-soft 非错误） */
export function companionApi(context?: AiChatContext | null): Promise<AiCompanionHint[]> {
  return post<AiCompanionHint[]>('/ai/companion', context ?? {})
}

/** A15 建议条留痕（是否被点开） */
export function companionFeedbackApi(body: AiCompanionFeedbackDTO): Promise<boolean> {
  return post<boolean>('/ai/companion/feedback', body)
}

/** A16 业务全流程引导（确定性事实层，不调模型；参数皆可空 → 返回总览） */
export function flowGuideApi(sampleNo?: string, status?: number): Promise<FlowGuide> {
  return get<FlowGuide>('/ai/flow/guide', {
    ...(sampleNo ? { sampleNo } : {}),
    ...(status != null ? { status } : {}),
  })
}

/** A17 扫描件 OCR 任务列表（读侧车位，页级进度） */
export function scanOcrJobsApi(): Promise<ScanOcrJob[]> {
  return get<ScanOcrJob[]>('/ai/kb/scan/jobs')
}

/** A18 单个 OCR 任务详情（逐页进度 + 失败页清单） */
export function scanOcrJobApi(stdKey: string): Promise<ScanOcrJob> {
  return get<ScanOcrJob>(`/ai/kb/scan/jobs/${encodeURIComponent(stdKey)}`)
}

/** A19 复位失败页为待跑 + 返回待执行命令（系统**不代跑** OCR） */
export function retryScanOcrApi(stdKey: string): Promise<ScanOcrRetry> {
  return post<ScanOcrRetry>(`/ai/kb/scan/jobs/${encodeURIComponent(stdKey)}/retry`)
}
