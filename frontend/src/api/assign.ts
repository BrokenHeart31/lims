/**
 * 检验任务安排（Assign）接口封装 —— T-501
 *
 * 契约见 docs/api/api-spec.md 第 5 章（/api/assign/*）：
 *   - 待安排列表 GET  /assign/pending         权限 assign:confirm
 *   - 安排明细 GET    /assign/detail/{id}     权限 assign:confirm
 *   - 自动分配 POST   /assign/auto            权限 assign:confirm（可重跑；已人工改派项不覆盖）
 *   - 人工改派 POST   /assign/reassign        权限 assign:reassign（仅允许有资质者）
 *   - 安排确认 POST   /assign/confirm         权限 assign:confirm（S30→S40，要求全部已指派）
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 */
import { get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 指派类型枚举（与后端 common/enums/AssignType 对齐） */
export const ASSIGN_TYPE_OPTIONS = [
  { value: 0, label: '未指派', type: 'info' as const },
  { value: 1, label: '分类规则', type: 'primary' as const },
  { value: 2, label: '方法资质', type: 'success' as const },
  { value: 3, label: '人工改派', type: 'warning' as const },
] as const

/** 指派状态枚举 */
export const ASSIGN_STATUS_OPTIONS = [
  { value: 0, label: '待指派', type: 'info' as const },
  { value: 1, label: '已指派', type: 'success' as const },
] as const

/** 候选来源 */
export const CANDIDATE_SOURCE_OPTIONS = {
  CATEGORY: '分类规则',
  METHOD: '方法资质',
} as const

/** 待安排样品（5.2 列表项） */
export interface AssignPendingRow {
  id: number
  sampleNo: string
  sampleName: string
  clientName: string
  taskNo: string
  inspectType: string
  samplingDate: string
  status: number
  statusLabel: string
  assignTotal: number
  assignDone: number
}

/** 候选检验员（5.3 明细中的 candidates[]） */
export interface AssignCandidate {
  testerNo: string
  testerName: string
  matchedMethodNo?: string | null
  source: 'CATEGORY' | 'METHOD'
}

/** 样品单项（5.3 明细中的 items[]） */
export interface AssignItemRow {
  id: number
  itemOrder: number
  itemName: string
  methods?: string | null
  unit?: string | null
  stdValue?: string | null
  judgeType?: number | null
  isReference?: number | null
  assignStatus: number
  assignType: number
  testerNo?: string | null
  testerName?: string | null
  assignedAt?: string | null
}

/** 安排明细（5.3） */
export interface AssignDetail {
  sampleId: number
  sampleNo: string
  sampleName: string
  status: number
  statusLabel: string
  assignTotal: number
  assignDone: number
  inputPermitted: boolean
  items: AssignItemRow[]
  candidates: AssignCandidate[]
}

/** 自动分配结果（5.4） */
export interface AssignAutoResult {
  sampleId: number
  total: number
  assigned: number
  pending: number
  details: Array<{
    itemOrder: number
    itemName: string
    assignStatus: number
    assignType: number
    testerNo?: string | null
    testerName?: string | null
    reason?: string | null
  }>
}

/** 自动分配请求（5.4） */
export interface AssignAutoQuery {
  sampleId: number
}

/** 人工改派请求（5.5） */
export interface AssignReassignBody {
  itemId: number
  testerNo: string
}

/** 安排确认请求（5.6） */
export interface AssignConfirmBody {
  sampleId: number
}

/** 待安排列表查询参数（5.2） */
export interface AssignPendingQuery {
  current?: number
  size?: number
  sampleNo?: string
  sampleName?: string
}

// ---------------- API functions ----------------

/** 5.2 待安排样品分页 */
export function pagePendingAssignApi(q: AssignPendingQuery): Promise<PageResult<AssignPendingRow>> {
  return get<PageResult<AssignPendingRow>>('/assign/pending', q as Record<string, unknown>)
}

/** 5.3 安排明细 */
export function getAssignDetailApi(sampleId: number): Promise<AssignDetail> {
  return get<AssignDetail>(`/assign/detail/${sampleId}`)
}

/** 5.4 执行自动分配（可重跑） */
export function autoAssignApi(q: AssignAutoQuery): Promise<AssignAutoResult> {
  return post<AssignAutoResult>('/assign/auto', q)
}

/** 5.5 人工改派 */
export function reassignApi(body: AssignReassignBody): Promise<AssignItemRow> {
  return post<AssignItemRow>('/assign/reassign', body)
}

/** 5.6 安排确认（S30 → S40） */
export function confirmAssignApi(body: AssignConfirmBody): Promise<{ sampleId: number; status: number; statusLabel: string }> {
  return post<{ sampleId: number; status: number; statusLabel: string }>('/assign/confirm', body)
}