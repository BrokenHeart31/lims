/**
 * 样品登记（Sample）接口封装 —— T-301
 *
 * 契约见 docs/api/api-spec.md 样品域（/api/sample/*）：
 *   - 导入 POST   /sample/import   （multipart，字段名 file）   权限 sample:import
 *   - 分页 GET    /sample/page                                  权限 sample:query
 *   - 详情 GET    /sample/{id}                                  权限 sample:query
 *   - 维护 PUT    /sample                                       权限 sample:import
 *   - 确认 POST   /sample/confirm  （S10→S20，批量）            权限 sample:confirm
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 */
import { get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 样品状态机（AGENTS 7.2，与后端 common/enums/SampleStatus 的 code 严格一致，禁止私改） */
export const SAMPLE_STATUS_OPTIONS = [
  { code: 10, label: '已登记' },
  { code: 20, label: '登记确认' },
  { code: 30, label: '已分解' },
  { code: 40, label: '已安排' },
  { code: 50, label: '检验中' },
  { code: 60, label: '检验完成' },
  { code: 70, label: '已审核' },
  { code: 80, label: '已签发' },
  { code: 90, label: '已出报告' },
] as const

/** 状态 code → 标签色（仅体验层展示） */
export const SAMPLE_STATUS_TAG: Record<number, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
  10: 'info',
  20: 'primary',
  30: 'primary',
  40: 'warning',
  50: 'warning',
  60: 'success',
  70: 'success',
  80: 'success',
  90: 'success',
}

/** 样品登记（与后端 entity/VO 对齐，camelCase） */
export interface Sample {
  id?: number
  /** 样品编号（唯一，如 JK(2023)-SA-001） */
  sampleNo: string
  sampleName: string
  /** 受检单位 */
  clientName?: string
  /** 抽样地址 */
  samplingAddress?: string
  payee?: string
  /** 费用 */
  fee?: number
  /** 样品数量（文本，如 3kg） */
  sampleQuantity?: string
  /** 项目名称（如 市级例行） */
  projectName?: string
  /** 采样日期 yyyy-MM-dd */
  samplingDate?: string
  remark?: string
  sampler?: string
  /** 生产单位 */
  manufacturer?: string
  /** 抽样基数 */
  samplingBase?: string
  /** 样品状态（如 鲜活） */
  sampleState?: string
  spec?: string
  brand?: string
  grade?: string
  /** 原编号或生产日期 */
  originalNo?: string
  /** 检验类别（如 监督抽检） */
  inspectType?: string
  /** 要求完成日期 yyyy-MM-dd */
  requireCompleteDate?: string
  /** 关联监抽任务编号 */
  taskNo?: string
  taskBatchNo?: string
  /** 样品状态机 code（S10=10 … S90=90） */
  status: number
  /** 状态中文名（后端只读输出） */
  statusLabel?: string
  /** 登记确认人 / 时间（只读） */
  confirmedBy?: string
  confirmedAt?: string
  /** 审计字段（只读） */
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

/** 列表查询参数 */
export interface SampleQuery {
  pageNum: number
  pageSize: number
  sampleNo?: string
  sampleName?: string
  taskNo?: string
  status?: number
}

/** 导入失败明细行 */
export interface SampleImportErrorRow {
  /** Excel 行号（1 基） */
  rowNum: number
  /** 样品编号（可空） */
  sampleNo?: string
  message: string
}

/** 导入结果 */
export interface SampleImportResult {
  total: number
  successCount: number
  failCount: number
  errors: SampleImportErrorRow[]
}

/** 分页查询样品：GET /sample/page */
export function pageSampleApi(params: SampleQuery): Promise<PageResult<Sample>> {
  return get<PageResult<Sample>>('/sample/page', params as unknown as Record<string, unknown>)
}

/** 样品详情：GET /sample/{id} */
export function getSampleApi(id: number): Promise<Sample> {
  return get<Sample>(`/sample/${id}`)
}

/** 采样单 Excel 导入：POST /sample/import（multipart，字段名 file） */
export function importSampleApi(file: File): Promise<SampleImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return post<SampleImportResult>('/sample/import', formData)
}

/** 登记信息维护：PUT /sample */
export function updateSampleApi(payload: Sample): Promise<void> {
  return put<void>('/sample', payload)
}

/** 登记确认（S10→S20，批量）：POST /sample/confirm */
export function confirmSampleApi(ids: number[]): Promise<{ confirmedCount: number }> {
  return post<{ confirmedCount: number }>('/sample/confirm', { ids })
}
