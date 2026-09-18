/**
 * 流程回溯接口封装（feature B，api-spec 第 17/18 章）。
 *
 * 说明：预览（B2）在「被拒边」时**不抛 HTTP 错误**，而是返回 `allowed=false + code + msg`——
 * 因此本层不做特殊判断，原样把 `RollbackPreviewVO` 交给调用方渲染。
 */
import { get, post } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  ReportVoidDTO,
  ReportVoidResultVO,
  RollbackActionResultVO,
  RollbackExecuteDTO,
  RollbackHistoryQueryDTO,
  RollbackHistoryVO,
  RollbackPreviewDTO,
  RollbackPreviewVO,
  RollbackRecoverDTO,
  RollbackTimelineVO,
} from '@/types/rollback'

/** B1 该样品全链路事件时间线 */
export function getRollbackTimelineApi(sampleId: number): Promise<RollbackTimelineVO> {
  return get<RollbackTimelineVO>(`/rollback/timeline/${sampleId}`)
}

/** B2 回退前「下游影响预览」（纯读不落库） */
export function previewRollbackApi(body: RollbackPreviewDTO): Promise<RollbackPreviewVO> {
  return post<RollbackPreviewVO>('/rollback/preview', body)
}

/** B3 执行一次逐级回退 */
export function executeRollbackApi(body: RollbackExecuteDTO): Promise<RollbackActionResultVO> {
  return post<RollbackActionResultVO>('/rollback/execute', body)
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
