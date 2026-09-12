/**
 * 检验报告审核 / 签发接口封装 —— T-701
 *
 * 契约见 docs/api/api-spec.md 第 7 章（/api/report/*）：
 *   - 待审核列表 GET  /report/audit/pending     权限 report:audit（status=S60）
 *   - 待签发列表 GET  /report/sign/pending      权限 report:sign （status=S70）
 *   - 明细       GET  /report/detail/{sampleId} 权限 report:audit | report:sign
 *   - 审核通过   POST /report/audit/approve     权限 report:audit（S60→S70）
 *   - 审核退回   POST /report/audit/return      权限 report:audit（S60→S50，原因必填）
 *   - 签发       POST /report/sign              权限 report:sign （S70→S80）
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 */
import { get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 审核/签发动作 */
export const AUDIT_ACTION_OPTIONS = [
  { value: 1, label: '审核通过' },
  { value: 2, label: '审核退回' },
  { value: 3, label: '签发' },
] as const

/** 异常项类型 */
export const ABNORMAL_TYPE_BLANK = 'BLANK'
export const ABNORMAL_TYPE_PENDING = 'PENDING'

/** 待审核 / 待签发样品行 */
export interface AuditPendingRow {
  id: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  inspectType?: string | null
  samplingDate?: string | null
  /** 60=检验完成（待审核） 70=已审核（待签发） */
  status: number
  statusLabel?: string
  conclusion?: number | null
  conclusionLabel?: string | null
  itemTotal: number
  enteredCount: number
  /** 异常项数（未录入 + 待判定） */
  abnormalCount: number
  auditBy?: string | null
  auditAt?: string | null
  auditOpinion?: string | null
}

/** 明细中的检测单项 */
export interface AuditItem {
  id: number
  itemOrder: number
  itemName: string
  unit?: string | null
  basisCode?: string | null
  stdValue?: string | null
  judgeType: number
  judgeTypeLabel?: string
  isReference: number
  lowerLimit?: string | null
  testerNo?: string | null
  testerName?: string | null
  testValue?: string | null
  conclusion?: number | null
  conclusionLabel?: string | null
  conclusionSource?: number | null
  conclusionSourceLabel?: string | null
  judgeBasis?: string | null
  enteredBy?: string | null
  enteredAt?: string | null
  /** 是否已有效录入（false 时 conclusion 相关字段为 null） */
  entered: boolean
}

/** 异常项（未录入 / 待判定） */
export interface AbnormalItem {
  itemId: number
  itemOrder: number
  itemName: string
  /** BLANK=未录入 / PENDING=待判定 */
  type: string
  typeLabel: string
  reason: string
}

/** 审核流水 */
export interface AuditLogItem {
  id: number
  action: number
  actionLabel: string
  fromStatus: number
  fromStatusLabel: string
  toStatus: number
  toStatusLabel: string
  opinion?: string | null
  abnormalConfirmed?: number | null
  operatedBy?: string | null
  operatedAt?: string | null
}

/** 审核/签发明细 */
export interface AuditDetail {
  sampleId: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  status: number
  statusLabel?: string
  conclusion?: number | null
  conclusionLabel?: string | null
  itemTotal: number
  enteredCount: number
  blankCount: number
  pendingCount: number
  abnormalCount: number
  allowAudit: boolean
  allowSign: boolean
  auditBy?: string | null
  auditAt?: string | null
  auditOpinion?: string | null
  signBy?: string | null
  signAt?: string | null
  items: AuditItem[]
  abnormalItems: AbnormalItem[]
  logs: AuditLogItem[]
}

/** 审核/签发动作结果 */
export interface AuditActionResult {
  sampleId: number
  sampleNo: string
  status: number
  statusLabel: string
  action: number
  actionLabel: string
  opinion?: string | null
  abnormalConfirmed: number
  operatedBy?: string | null
  operatedAt?: string | null
}

/** 待审核 / 待签发查询参数 */
export interface AuditPendingQuery {
  current: number
  size: number
  sampleNo?: string
  sampleName?: string
}

/** 分页查询待审核样品：GET /report/audit/pending */
export function pagePendingAuditApi(params: AuditPendingQuery): Promise<PageResult<AuditPendingRow>> {
  return get<PageResult<AuditPendingRow>>('/report/audit/pending', params as unknown as Record<string, unknown>)
}

/** 分页查询待签发样品：GET /report/sign/pending */
export function pagePendingSignApi(params: AuditPendingQuery): Promise<PageResult<AuditPendingRow>> {
  return get<PageResult<AuditPendingRow>>('/report/sign/pending', params as unknown as Record<string, unknown>)
}

/** 审核/签发明细：GET /report/detail/{sampleId} */
export function getAuditDetailApi(sampleId: number): Promise<AuditDetail> {
  return get<AuditDetail>(`/report/detail/${sampleId}`)
}

/** 审核通过（S60→S70）：POST /report/audit/approve */
export function approveAuditApi(
  sampleId: number,
  opinion: string | null,
  abnormalConfirmed: boolean,
): Promise<AuditActionResult> {
  return post<AuditActionResult>('/report/audit/approve', { sampleId, opinion, abnormalConfirmed })
}

/** 审核退回（S60→S50）：POST /report/audit/return */
export function returnAuditApi(sampleId: number, reason: string): Promise<AuditActionResult> {
  return post<AuditActionResult>('/report/audit/return', { sampleId, reason })
}

/** 签发（S70→S80）：POST /report/sign */
export function signReportApi(sampleId: number, opinion: string | null): Promise<AuditActionResult> {
  return post<AuditActionResult>('/report/sign', { sampleId, opinion })
}
