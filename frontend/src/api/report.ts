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
import type { ReportVO } from '@/types/report'

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

/**
 * 报告类型 code —— T-702（与后端 common/enums/ReportType 一致）。
 * 对外契约统一为数字 code：1=CMA / 2=CMA-CATL（请求体与响应 VO 同口径）。
 */
export type ReportTypeCode = 1 | 2

/** 报告类型下拉选项（前端展示用） */
export const REPORT_TYPE_OPTIONS: { value: ReportTypeCode; label: string }[] = [
  { value: 1, label: 'CMA检验报告' },
  { value: 2, label: 'CMA-CATL检验报告' },
]

/** 报告生成列表行（status ∈ {S80 已签发, S90 已出报告}） */
export interface ReportPendingRow {
  id: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  inspectType?: string | null
  samplingDate?: string | null
  /** 80=已签发 90=已出报告 */
  status: number
  statusLabel?: string
  conclusion?: number | null
  conclusionLabel?: string | null
  itemTotal: number
  auditBy?: string | null
  signBy?: string | null
  signAt?: string | null
  /** 报告类型 code（未生成时 null） */
  reportType?: number | null
  reportTypeLabel?: string | null
  reportGeneratedAt?: string | null
}

/** 报告生成列表查询参数 */
export interface ReportPendingQuery {
  current: number
  size: number
  sampleNo?: string
  sampleName?: string
  taskNo?: string
}

/** 分页查询可生成/可重打样品：GET /report/generate/pending */
export function pageReportPendingApi(params: ReportPendingQuery): Promise<PageResult<ReportPendingRow>> {
  return get<PageResult<ReportPendingRow>>(
    '/report/generate/pending',
    params as unknown as Record<string, unknown>,
  )
}

/** 生成检验报告（S80→S90）：POST /report/generate */
export function generateReportApi(sampleNo: string, reportType: ReportTypeCode): Promise<ReportVO> {
  return post<ReportVO>('/report/generate', { sampleNo, reportType })
}

/** 查询报告详情（供打印/重打，不改状态）：GET /report/detail */
export function getReportDetailApi(sampleNo: string, reportType?: ReportTypeCode): Promise<ReportVO> {
  return get<ReportVO>('/report/detail', { sampleNo, reportType })
}
