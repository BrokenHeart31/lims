/**
 * 流程回溯（feature B）前端类型 —— 与后端 `com.lims.vo` / `com.lims.dto` 一一对应。
 *
 * 契约见 docs/api/api-spec.md 第 17 章、设计 §4.2。红线：**禁止 any**，字段与 VO 严格一致。
 */

/** 状态流水事件类型（对应后端 StatusEventType：1 正向 2 退回 3 签发 4 回退 5 恢复 6 作废 7 报告） */
export const EVENT_TYPE_FORWARD = 1
export const EVENT_TYPE_RETURN = 2
export const EVENT_TYPE_SIGN = 3
export const EVENT_TYPE_ROLLBACK = 4
export const EVENT_TYPE_RECOVER = 5
export const EVENT_TYPE_VOID = 6
export const EVENT_TYPE_REPORT = 7

/** 回退分组：1 常规 / 2 敏感 */
export const ROLLBACK_GROUP_NORMAL = 1
export const ROLLBACK_GROUP_SENSITIVE = 2

/** 回退业务码（与后端 ResultCode 一致，前端仅用于分类提示文案） */
export const ROLLBACK_CODE = {
  ILLEGAL: 4101,
  SIGNED: 4102,
  REPORTED: 4103,
  SENSITIVE_DENIED: 4104,
  NEED_SECOND_CONFIRM: 4105,
  REASON_REQUIRED: 4106,
  NOT_RECOVERABLE: 4107,
  CONFLICT: 4108,
} as const

/** 作废类型：1 作废 / 2 召回 */
export const VOID_TYPE = { VOID: 1, RECALL: 2 } as const

/** 一条允许的回退边 */
export interface RollbackEdge {
  from: number
  to: number
  group: number
  groupLabel?: string | null
  reasonRequired: boolean
}

/** 一条被拒的回退边（如 S80→S70、S90→S80），带业务码与说明 */
export interface RollbackRejectedEdge {
  from: number
  to: number
  code: number
  msg: string
}

/** 一条状态流水事件（正向 + 逆向） */
export interface RollbackEvent {
  id: number
  eventType: number
  eventTypeLabel?: string | null
  fromStatus?: number | null
  fromStatusLabel?: string | null
  toStatus?: number | null
  toStatusLabel?: string | null
  actionLabel?: string | null
  reason?: string | null
  dataDisposition?: string | null
  rollbackId?: number | null
  /** 回退事件专用：是否可再撤销 */
  canRecover?: boolean | null
  /** 回退事件专用：是否已被恢复 */
  recovered?: boolean | null
  source?: string | null
  operatedBy?: string | null
  operatedAt?: string | null
}

/** B1 流程回溯时间线 */
export interface RollbackTimelineVO {
  sampleId: number
  sampleNo: string
  currentStatus: number
  currentStatusLabel: string
  canRollbackTo: number[]
  rollbackEdges: RollbackEdge[]
  rejectedEdges: RollbackRejectedEdge[]
  events: RollbackEvent[]
}

/** 单条待失效数据 */
export interface RollbackInvalidationItem {
  id: number
  label: string
}

/** 一类下游数据的失效预览 */
export interface RollbackInvalidation {
  /** sample_item / sample_result / assign_fields */
  type: string
  typeLabel: string
  count: number
  items: RollbackInvalidationItem[]
}

/** B2 回退前「下游影响预览」 */
export interface RollbackPreviewVO {
  sampleId: number
  sampleNo?: string | null
  fromStatus: number
  fromStatusLabel?: string | null
  toStatus: number
  toStatusLabel?: string | null
  allowed: boolean
  /** 被拒时的业务码；允许时为 null */
  code?: number | null
  /** 被拒时的可展示文案；允许时为 null */
  msg?: string | null
  group?: number | null
  groupLabel?: string | null
  reasonRequired: boolean
  needSensitive: boolean
  needSecondConfirm: boolean
  irreversible: boolean
  invalidations: RollbackInvalidation[]
  hint?: string | null
}

/** B3/B4 回退 / 恢复执行结果 */
export interface RollbackActionResultVO {
  sampleId: number
  sampleNo?: string | null
  status: number
  statusLabel?: string | null
  rollbackId?: number | null
  canRecover?: boolean | null
  affectedItemCount?: number | null
  affectedResultCount?: number | null
  invalidatedSummary?: string | null
}

/** B5 回退记录行（跨样品） */
export interface RollbackHistoryVO {
  id: number
  sampleId: number
  sampleNo: string
  fromStatus: number
  fromStatusLabel?: string | null
  toStatus: number
  toStatusLabel?: string | null
  edgeGroup?: number | null
  edgeGroupLabel?: string | null
  reason?: string | null
  secondConfirmed?: number | null
  affectedItemCount?: number | null
  affectedResultCount?: number | null
  canRecover?: number | null
  recovered?: number | null
  operatedBy?: string | null
  operatedAt?: string | null
  recoverBy?: string | null
  recoverAt?: string | null
}

/** B6 作废 / 召回结果 */
export interface ReportVoidResultVO {
  sampleId: number
  sampleNo: string
  voidType: number
  voidTypeLabel?: string | null
  statusAtVoid?: number | null
  statusAtVoidLabel?: string | null
  reason?: string | null
  secondConfirmed?: number | null
  operatedBy?: string | null
  operatedAt?: string | null
}

/** B2 预览请求 */
export interface RollbackPreviewDTO {
  sampleId: number
  targetStatus: number
}

/** B3 执行回退请求 */
export interface RollbackExecuteDTO {
  sampleId: number
  targetStatus: number
  reason: string
  secondConfirmed?: boolean
}

/** B4 恢复请求 */
export interface RollbackRecoverDTO {
  rollbackId: number
  reason?: string
}

/** B5 回退记录查询条件（分页参数单独传） */
export interface RollbackHistoryQueryDTO {
  sampleNo?: string
  fromStatus?: number
  toStatus?: number
}

/** B6 作废 / 召回请求 */
export interface ReportVoidDTO {
  sampleNo: string
  voidType: number
  reason: string
  secondConfirmed?: boolean
}
