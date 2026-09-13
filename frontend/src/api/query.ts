/**
 * 查询域（Query）接口封装 —— T-801
 *
 * 契约见 docs/api/api-spec.md 查询域（/api/query/*）：
 *   - 在检样品分页  GET /query/testing/page                     权限 query:testing
 *   - 历史样品分页  GET /query/history/page                     权限 query:history
 *   - 项目库分页    GET /query/library/page                     权限 base:lib:list
 *   - 库明细列表    GET /query/library/{productLibId}/items     权限 base:lib:list
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 * 新域统一分页参数 current/size（沿用报告域约定，不新增 pageNum）。
 */
import { get } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 在检样品查询参数（current/size 必填，其余可选） */
export interface TestingQueryParams {
  current: number
  size: number
  /** 样品编号（前缀匹配） */
  sampleNo?: string
  /** 样品名称（模糊匹配） */
  sampleName?: string
  /** 受检单位（模糊匹配） */
  clientName?: string
  /** 任务编号（精确匹配） */
  taskNo?: string
  /** 样品状态 code（10..70） */
  status?: number
  /** 抽样日期起 yyyy-MM-dd */
  samplingDateFrom?: string
  /** 抽样日期止 yyyy-MM-dd */
  samplingDateTo?: string
}

/** 在检样品查询行 */
export interface TestingQueryRow {
  id: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  inspectType?: string | null
  samplingDate?: string | null
  /** 样品状态 code（10..70） */
  status: number
  statusLabel?: string | null
  /** 检测单项总数 */
  itemTotal: number
  /** 已有效录入项数 */
  enteredCount: number
  /** 未录入项数 */
  blankCount: number
  /** 待判定项数（已录入但结论为「待判定」） */
  pendingCount: number
  /** 异常项数 = 未录入 + 待判定 */
  abnormalCount: number
  /** 整体结论 code（1/2/3） */
  conclusion?: number | null
  conclusionLabel?: string | null
  /** 当前处理人（后端按状态推导） */
  currentHandler?: string | null
  /** 进入当前阶段时间 */
  stageEnteredAt?: string | null
  /** 当前阶段停留小时数（一位小数） */
  stageStayHours?: number | null
  updatedAt?: string | null
}

/** 历史样品查询参数（在检参数 + 结论 + 是否已生成报告） */
export interface HistoryQueryParams extends TestingQueryParams {
  /** 整体结论 code（1=合格 2=不合格 3=待判定） */
  conclusion?: number
  /** true=仅已生成报告；false=仅未生成报告；不传=全部 */
  reportGenerated?: boolean
}

/** 历史样品查询行 */
export interface HistoryQueryRow {
  id: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  inspectType?: string | null
  samplingDate?: string | null
  /** 80=已签发 90=已出报告 */
  status: number
  statusLabel?: string | null
  conclusion?: number | null
  conclusionLabel?: string | null
  /** 检测单项总数 */
  itemTotal: number
  /** 未录入项数 */
  blankCount: number
  /** 待判定项数（已录入但结论为「待判定」） */
  pendingCount: number
  /** 异常项数 = 未录入 + 待判定 */
  abnormalCount: number
  /** 审核人姓名 */
  auditBy?: string | null
  auditAt?: string | null
  /** 签发人姓名 */
  signBy?: string | null
  signAt?: string | null
  /** 报告类型 code（1=CMA 2=CMA-CATL） */
  reportType?: number | null
  reportTypeLabel?: string | null
  reportGeneratedAt?: string | null
}

/** 项目库（产品）查询参数 */
export interface LibraryQueryParams {
  current: number
  size: number
  /** 产品名称（模糊匹配） */
  productName?: string
  /** 食品大类（精确匹配） */
  category?: string
}

/** 项目库（产品）查询行 */
export interface LibraryQueryRow {
  id: number
  productName?: string | null
  category?: string | null
  /** 该产品检测单项数 */
  itemCount: number
}

/** 项目库检测单项 */
export interface LibraryItemRow {
  id: number
  itemOrder: number
  itemName: string
  basisCode?: string | null
  methods?: string | null
  stdValue?: string | null
  /** 判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
  judgeType: number
  judgeTypeLabel?: string
  /** 是否参考性限量 0/1 */
  isReference: number
  lowerLimit?: string | null
  unit?: string | null
}

/** 在检样品分页：GET /query/testing/page */
export function pageTestingQueryApi(params: TestingQueryParams): Promise<PageResult<TestingQueryRow>> {
  return get<PageResult<TestingQueryRow>>('/query/testing/page', params as unknown as Record<string, unknown>)
}

/** 历史样品分页：GET /query/history/page */
export function pageHistoryQueryApi(params: HistoryQueryParams): Promise<PageResult<HistoryQueryRow>> {
  return get<PageResult<HistoryQueryRow>>('/query/history/page', params as unknown as Record<string, unknown>)
}

/** 项目库分页：GET /query/library/page */
export function pageLibraryQueryApi(params: LibraryQueryParams): Promise<PageResult<LibraryQueryRow>> {
  return get<PageResult<LibraryQueryRow>>('/query/library/page', params as unknown as Record<string, unknown>)
}

/** 某产品的检测单项列表：GET /query/library/{productLibId}/items */
export function getLibraryItemsApi(productLibId: number): Promise<LibraryItemRow[]> {
  return get<LibraryItemRow[]>(`/query/library/${productLibId}/items`)
}
