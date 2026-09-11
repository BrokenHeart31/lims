/**
 * 项目分解（Item）接口封装 —— T-401
 *
 * 契约见 docs/api/api-spec.md 第 4 章（/api/item/*）：
 *   - 套库预览 GET  /item/match/{sampleId}   权限 item:decompose
 *   - 明细查询 GET  /item/list/{sampleId}    权限 item:decompose | sample:query
 *   - 保存分解 PUT  /item/save               权限 item:decompose
 *   - 分解确认 POST /item/confirm            权限 item:decompose（S20→S30）
 *   - 待分解列表 GET /item/pending           权限 item:decompose
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 */
import { get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 判定类型（AGENTS 7.3 / 白名单定稿，与后端 judge_type 一致） */
export const JUDGE_TYPE_OPTIONS = [
  { value: 1, label: '限量比较' },
  { value: 2, label: '不得检出/不得使用' },
  { value: 3, label: '文本/感官人工' },
] as const

/** 来源类型 */
export const SOURCE_TYPE_OPTIONS = [
  { value: 1, label: '标准库套用' },
  { value: 2, label: '人工新增' },
] as const

/** 样品检验单项（与后端 entity/SampleItem 对齐，camelCase） */
export interface SampleItem {
  id?: number
  sampleId: number
  sampleNo?: string
  /** 项次（样品内排序，从 1 起，唯一） */
  itemOrder: number
  itemName: string
  /** 来源标准库明细ID；人工新增为 null */
  libItemId?: number | null
  unit?: string | null
  basisCode?: string | null
  /** 检验方法（多个以 # 分隔） */
  methods?: string | null
  stdValue?: string | null
  /** 判定类型 1/2/3 */
  judgeType: number
  /** 是否参考性限量 0/1 */
  isReference: number
  lowerLimit?: string | null
  methodNote?: string | null
  /** 来源 1=标准库套用 2=人工新增 */
  sourceType: number
  remark?: string | null
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

/** 套库预览返回的单项（字段与 sample_item 的下沉快照一致） */
export interface MatchedItem {
  itemOrder: number
  itemName: string
  libItemId: number | null
  unit: string | null
  basisCode: string | null
  methods: string | null
  stdValue: string | null
  judgeType: number
  isReference: number
  lowerLimit: string | null
  methodNote: string | null
}

/** 候选产品（命中多条时有值） */
export interface MatchCandidate {
  libId: number
  productName: string
  category: string | null
}

/** 套库预览结果 */
export interface ItemMatchResult {
  sampleId: number
  sampleName: string
  /** 是否匹配到产品标准库 */
  matched: boolean
  matchedLibId: number | null
  matchedProductName: string | null
  candidates: MatchCandidate[]
  items: MatchedItem[]
}

/** 待分解样品行 */
export interface ItemPendingRow {
  id: number
  sampleNo: string
  sampleName: string
  clientName?: string
  taskNo?: string
  taskBatchNo?: string
  status: number
  statusLabel?: string
  samplingDate?: string
  inspectType?: string
  /** 已保存明细数（分解进度） */
  itemCount: number
}

/** 待分解列表查询参数 */
export interface ItemPendingQuery {
  current: number
  size: number
  sampleNo?: string
  sampleName?: string
}

/** 套库预览：GET /item/match/{sampleId}（不落库） */
export function matchItemApi(sampleId: number): Promise<ItemMatchResult> {
  return get<ItemMatchResult>(`/item/match/${sampleId}`)
}

/** 查询已保存的分解明细：GET /item/list/{sampleId} */
export function listItemApi(sampleId: number): Promise<{ sampleId: number; items: SampleItem[] }> {
  return get<{ sampleId: number; items: SampleItem[] }>(`/item/list/${sampleId}`)
}

/** 保存分解（覆盖式）：PUT /item/save */
export function saveItemApi(sampleId: number, items: SampleItem[]): Promise<{ sampleId: number; itemCount: number }> {
  return put<{ sampleId: number; itemCount: number }>('/item/save', { sampleId, items })
}

/** 分解确认（S20→S30）：POST /item/confirm */
export function confirmItemApi(sampleId: number): Promise<{ sampleId: number; status: number; statusLabel: string }> {
  return post<{ sampleId: number; status: number; statusLabel: string }>('/item/confirm', { sampleId })
}

/** 分页查询待分解样品：GET /item/pending */
export function pagePendingItemApi(params: ItemPendingQuery): Promise<PageResult<ItemPendingRow>> {
  return get<PageResult<ItemPendingRow>>('/item/pending', params as unknown as Record<string, unknown>)
}
