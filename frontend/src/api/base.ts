/**
 * 基础数据域（Base）接口封装 —— T-105 方法资质库 / T-106 项目标准库
 *
 * 契约见 docs/api/api-spec.md 第 11/12 章：
 *   方法-检验员资质 /api/base/tester-method/*   权限 base:tester-method:list/add/edit/remove
 *   项目标准库     /api/base/lib/*              权限 base:lib:list/add/edit/remove
 * 统一响应 { code, msg, data }，分页 data.records/total/current/size，字段 camelCase。
 */
import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

// ============================================================================
// T-105 方法-检验员资质
// ============================================================================

/** 资质查询参数 */
export interface TesterMethodParams {
  current: number
  size: number
  /** 检验方法名称（模糊匹配） */
  methodName?: string
  /** 方法编号（模糊匹配） */
  methodNo?: string
  /** 检验员工号（模糊匹配） */
  testerNo?: string
  /** 资质状态 1=有效 0=失效 */
  qualStatus?: number
}

/** 资质行 */
export interface TesterMethodRow {
  id: number
  methodName: string
  methodNo?: string | null
  testerNo: string
  /** 工号反查姓名（后端批量填充，避免 N+1） */
  testerName?: string | null
  deptName?: string | null
  /** 1=有效 0=失效 */
  qualStatus: number
  qualStatusLabel?: string | null
  remark?: string | null
  createdAt?: string | null
}

/** 资质保存请求 */
export interface TesterMethodSaveBody {
  id?: number
  methodName: string
  methodNo?: string
  testerNo: string
  qualStatus: number
  remark?: string
}

/** 导入结果（部分失败不回滚） */
export interface TesterMethodImportResult {
  successCount: number
  updateCount: number
  failCount: number
  errors: string[]
}

/** 资质分页：GET /base/tester-method/page */
export function pageTesterMethodApi(params: TesterMethodParams): Promise<PageResult<TesterMethodRow>> {
  return get<PageResult<TesterMethodRow>>('/base/tester-method/page', params as unknown as Record<string, unknown>)
}

/** 资质新增：POST /base/tester-method */
export function createTesterMethodApi(body: TesterMethodSaveBody): Promise<number> {
  return post<number>('/base/tester-method', body)
}

/** 资质更新：PUT /base/tester-method */
export function updateTesterMethodApi(body: TesterMethodSaveBody): Promise<void> {
  return put<void>('/base/tester-method', body)
}

/** 资质删除：DELETE /base/tester-method/{id} */
export function removeTesterMethodApi(id: number): Promise<void> {
  return del<void>(`/base/tester-method/${id}`)
}

/** 资质 Excel 导入：POST /base/tester-method/import（multipart，字段名 file） */
export function importTesterMethodApi(file: File): Promise<TesterMethodImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return post<TesterMethodImportResult>('/base/tester-method/import', formData)
}

// ============================================================================
// T-106 项目标准库
// ============================================================================

/** 产品库查询参数 */
export interface ProductLibParams {
  current: number
  size: number
  /** 产品编号（模糊匹配） */
  productCode?: string
  /** 产品名称（模糊匹配） */
  productName?: string
  /** 食品大类（模糊匹配） */
  category?: string
}

/** 产品库行 */
export interface ProductLibRow {
  id: number
  productCode?: string | null
  productName?: string | null
  category?: string | null
  /** 检测单项数 */
  itemCount: number
  /** 明细列表（仅 detail 接口填充） */
  items?: ProductLibItemRow[] | null
  createdAt?: string | null
}

/** 检测单项行 */
export interface ProductLibItemRow {
  id?: number
  productLibId?: number | null
  /** 项次（排序） */
  itemOrder: number
  itemName: string
  unit?: string | null
  /** 判定依据标准号 */
  basisCode?: string | null
  /** 检验方法 */
  methods?: string | null
  /** 标准值/限量值 */
  stdValue?: string | null
  /** 判定类型 1=限量比较 2=不得检出/不得使用 3=文本/感官人工 */
  judgeType: number
  judgeTypeLabel?: string | null
  /** 是否参考项 0/1 */
  isReference: number
  /** 最低检出限 */
  lowerLimit?: string | null
  /** 方法备注 */
  methodNote?: string | null
}

/** 产品保存请求 */
export interface ProductLibSaveBody {
  id?: number
  productCode: string
  productName: string
  category?: string
}

/** 检测单项保存请求（替换式提交时后端按路径上的 productLibId 绑定，故此处可省） */
export interface ProductLibItemSaveBody {
  id?: number
  productLibId?: number
  itemOrder: number
  itemName: string
  unit?: string
  basisCode?: string
  methods?: string
  stdValue?: string
  judgeType: number
  isReference: number
  lowerLimit?: string
  methodNote?: string
}

/** 项目库导入结果 */
export interface ProductLibImportResult {
  productCount: number
  productUpdated: number
  itemCount: number
  failCount: number
  errors: string[]
}

/** 产品分页：GET /base/lib/page */
export function pageProductLibApi(params: ProductLibParams): Promise<PageResult<ProductLibRow>> {
  return get<PageResult<ProductLibRow>>('/base/lib/page', params as unknown as Record<string, unknown>)
}

/** 产品详情（含明细）：GET /base/lib/{id} */
export function getProductLibApi(id: number): Promise<ProductLibRow> {
  return get<ProductLibRow>(`/base/lib/${id}`)
}

/** 产品新增：POST /base/lib */
export function createProductLibApi(body: ProductLibSaveBody): Promise<number> {
  return post<number>('/base/lib', body)
}

/** 产品更新：PUT /base/lib */
export function updateProductLibApi(body: ProductLibSaveBody): Promise<void> {
  return put<void>('/base/lib', body)
}

/** 产品删除：DELETE /base/lib/{id}（有明细时后端拒绝） */
export function removeProductLibApi(id: number): Promise<void> {
  return del<void>(`/base/lib/${id}`)
}

/** 某产品明细列表：GET /base/lib/{productLibId}/items */
export function listProductLibItemsApi(productLibId: number): Promise<ProductLibItemRow[]> {
  return get<ProductLibItemRow[]>(`/base/lib/${productLibId}/items`)
}

/** 明细新增：POST /base/lib/item */
export function createProductLibItemApi(body: ProductLibItemSaveBody): Promise<number> {
  return post<number>('/base/lib/item', body)
}

/** 明细更新：PUT /base/lib/item */
export function updateProductLibItemApi(body: ProductLibItemSaveBody): Promise<void> {
  return put<void>('/base/lib/item', body)
}

/** 明细删除：DELETE /base/lib/item/{id} */
export function removeProductLibItemApi(id: number): Promise<void> {
  return del<void>(`/base/lib/item/${id}`)
}

/** 明细覆盖式替换：PUT /base/lib/{productLibId}/items */
export function replaceProductLibItemsApi(
  productLibId: number,
  items: ProductLibItemSaveBody[],
): Promise<number> {
  return put<number>(`/base/lib/${productLibId}/items`, items)
}

/** 项目库 Excel 导入：POST /base/lib/import（multipart，字段名 file） */
export function importProductLibApi(file: File): Promise<ProductLibImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return post<ProductLibImportResult>('/base/lib/import', formData)
}
