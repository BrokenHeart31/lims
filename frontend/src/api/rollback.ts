/**
 * 流程回溯接口封装（feature B，api-spec 第 17 章）。
 *
 * 说明：预览（B2）在「被拒目标」时**不抛 HTTP 错误**，而是返回 `allowed=false + code + msg`——
 * 因此本层不做特殊判断，原样把 `RollbackPreviewVO` 交给调用方渲染。
 *
 * 2026-09-30：回退能力下沉到各业务页面内嵌使用；B3 改为**批量 + 可选目标步**，
 * 因此返回类型由 `RollbackActionResultVO` 变为 `RollbackBatchResultVO`（逐条明细）。
 * 本层的 `RollbackActionResultVO` 仍被 B4（撤销回退）使用。
 */
import { get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  ReportVoidDTO,
  ReportVoidResultVO,
  RollbackActionResultVO,
  RollbackBatchResultVO,
  RollbackExecuteDTO,
  RollbackHistoryQueryDTO,
  RollbackHistoryVO,
  RollbackPreviewDTO,
  RollbackPreviewVO,
  RollbackRecoverDTO,
  RollbackTargetsVO,
  RollbackTimelineVO,
} from '@/types/rollback'

/** B1 该样品全链路事件时间线 */
export function getRollbackTimelineApi(sampleId: number): Promise<RollbackTimelineVO> {
  return get<RollbackTimelineVO>(`/rollback/timeline/${sampleId}`)
}

/**
 * B7 该样品**可达的全部回退目标步** + 每步的下游影响预览（纯读）。
 *
 * 环节内嵌入口的核心读接口：打开回退框时一次拿全「能退到哪几步、退到每步会动什么」，
 * 避免「查时间线 + 逐个 preview」的 N+1 请求与两处口径漂移。
 */
export function getRollbackTargetsApi(sampleId: number): Promise<RollbackTargetsVO> {
  return get<RollbackTargetsVO>(`/rollback/targets/${sampleId}`)
}

/** B2 单个目标步的「下游影响预览」（纯读不落库；被拒目标返回 allowed=false） */
export function previewRollbackApi(body: RollbackPreviewDTO): Promise<RollbackPreviewVO> {
  return post<RollbackPreviewVO>('/rollback/preview', body)
}

/** B3 执行回退（支持可选目标步 + 批量 ids；返回逐条明细） */
export function executeRollbackApi(body: RollbackExecuteDTO): Promise<RollbackBatchResultVO> {
  return post<RollbackBatchResultVO>('/rollback/execute', body)
}

/** B4 恢复某次未产生新下游数据的回退 */
export function recoverRollbackApi(body: RollbackRecoverDTO): Promise<RollbackActionResultVO> {
  return post<RollbackActionResultVO>('/rollback/recover', body)
}

/** B5 分页查询回退记录（跨样品） */
export function pageRollbackHistoryApi(
  current: number,
  size: number,
  query: RollbackHistoryQueryDTO = {},
): Promise<PageResult<RollbackHistoryVO>> {
  return get<PageResult<RollbackHistoryVO>>('/rollback/history', {
    current,
    size,
    ...query,
  })
}

/** B6 报告作废 / 召回（S80/S90 专用治理动作，不改 status） */
export function voidReportApi(body: ReportVoidDTO): Promise<ReportVoidResultVO> {
  return post<ReportVoidResultVO>('/report/void', body)
}
