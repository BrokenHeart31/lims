/**
 * 检验结果录入 / 自动判定接口封装 —— T-601
 *
 * 契约见 docs/api/api-spec.md 第 6 章（/api/result/*）：
 *   - 待录入列表 GET  /result/pending            权限 result:entry
 *   - 录入明细   GET  /result/detail/{sampleId}  权限 result:entry
 *   - 判定预览   POST /result/judge              权限 result:entry（纯计算不落库）
 *   - 保存录入   PUT  /result/save               权限 result:entry（首次保存 S40→S50）
 *   - 提交       POST /result/submit             权限 result:entry（全部录齐 S50→S60）
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 */
import { get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 单项结论（与后端 common/enums/ResultConclusion 一致） */
export const CONCLUSION_OPTIONS = [
  { value: 1, label: '合格' },
  { value: 2, label: '不合格' },
  { value: 3, label: '待判定' },
] as const

/** 结论来源 */
export const CONCLUSION_SOURCE_OPTIONS = [
  { value: 1, label: '自动判定' },
  { value: 2, label: '人工判定' },
] as const

/** 待录入样品行 */
export interface ResultPendingRow {
  id: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  taskBatchNo?: string | null
  inspectType?: string | null
  samplingDate?: string | null
  /** 样品状态 code：40=已安排 50=检验中 */
  status: number
  statusLabel?: string
  /** 检测单项总数 */
  itemTotal: number
  /** 已录入项数 */
  enteredCount: number
  /** 整体结论 code（1 合格 / 2 不合格 / 3 待判定；未录齐为 3 或 null） */
  conclusion?: number | null
  conclusionLabel?: string | null
}

/** 录入明细中的检测单项（含量规依据快照与已录入结果） */
export interface ResultDetailItem {
  id: number
  itemOrder: number
  itemName: string
  unit?: string | null
  basisCode?: string | null
  methods?: string | null
  /** 标准值（判定依据参数，快照） */
  stdValue?: string | null
  /** 判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
  judgeType: number
  judgeTypeLabel?: string
  /** 是否参考性限量 0/1（参考项不计入整体结论） */
  isReference: number
  lowerLimit?: string | null
  testerNo?: string | null
  testerName?: string | null
  // ---- 已录入结果 ----
  testValue?: string | null
  conclusion?: number | null
  conclusionLabel?: string | null
  conclusionSource?: number | null
  conclusionSourceLabel?: string | null
  judgeBasis?: string | null
  enteredBy?: string | null
  enteredAt?: string | null
  remark?: string | null
  /** 后端派生：是否已录入结果 */
  entered?: boolean
}

/** 样品录入明细 */
export interface ResultDetail {
  sampleId: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  status: number
  statusLabel?: string
  itemTotal: number
  enteredCount: number
  conclusion?: number | null
  conclusionLabel?: string | null
  /** 是否允许录入（状态 ∈ {S40, S50}） */
  allowEdit: boolean
  items: ResultDetailItem[]
}

/** 判定预览结果 */
export interface ResultJudgeResult {
  itemId: number
  itemName?: string
  unit?: string | null
  stdValue?: string | null
  lowerLimit?: string | null
  judgeType?: number
  testValue?: string | null
  conclusion: number
  conclusionLabel: string
  conclusionSource: number
  conclusionSourceLabel: string
  judgeBasis: string
}

/** 保存/提交时提交的单条录入 */
export interface ResultSaveItemPayload {
  itemId: number
  testValue?: string | null
  manualConclusion?: number | null
  remark?: string | null
}

/** 单项判定结果（保存响应） */
export interface ResultSaveItem {
  itemId: number
  itemOrder: number
  itemName: string
  testValue?: string | null
  conclusion: number
  conclusionLabel: string
  conclusionSource: number
  conclusionSourceLabel: string
  judgeBasis: string
}

/** 保存/提交响应 */
export interface ResultSaveResult {
  sampleId: number
  sampleNo: string
  status: number
  statusLabel: string
  itemTotal: number
  enteredCount: number
  conclusion: number
  conclusionLabel: string
  items: ResultSaveItem[]
}

/** 待录入列表查询参数 */
export interface ResultPendingQuery {
  current: number
  size: number
  sampleNo?: string
  sampleName?: string
}

/** 分页查询待录入样品：GET /result/pending */
export function pagePendingResultApi(params: ResultPendingQuery): Promise<PageResult<ResultPendingRow>> {
  return get<PageResult<ResultPendingRow>>('/result/pending', params as unknown as Record<string, unknown>)
}

/** 查询样品录入明细：GET /result/detail/{sampleId} */
export function getResultDetailApi(sampleId: number): Promise<ResultDetail> {
  return get<ResultDetail>(`/result/detail/${sampleId}`)
}

/** 实时判定预览（不落库）：POST /result/judge */
export function judgeResultApi(
  itemId: number,
  testValue: string | null,
  manualConclusion: number | null,
): Promise<ResultJudgeResult> {
  return post<ResultJudgeResult>('/result/judge', { itemId, testValue, manualConclusion })
}

/** 保存录入结果（可分次）：PUT /result/save */
export function saveResultApi(sampleId: number, items: ResultSaveItemPayload[]): Promise<ResultSaveResult> {
  return put<ResultSaveResult>('/result/save', { sampleId, items })
}

/** 提交（全部录齐 → S60）：POST /result/submit */
export function submitResultApi(sampleId: number): Promise<ResultSaveResult> {
  return post<ResultSaveResult>('/result/submit', { sampleId })
}
