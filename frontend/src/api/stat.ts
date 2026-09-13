import { get } from '@/utils/request'

/**
 * T-803 统计看板接口封装。
 *
 * 设计约束（DECISIONS 2026-09-13）：
 * - 全部为真实 SQL 聚合，**禁止 mock 假数据**；无数据时后端返回 0 / null / 空数组，前端显示空态。
 * - 后端不加缓存，前端按需调用 + 页面提供「刷新」按钮；如需高频复用由页面自行决定是否缓存。
 */

/** 总览指标 */
export interface StatOverview {
  totalSamples: number
  testingSamples: number
  completedSamples: number
  totalItems: number
  reportCount: number
  judgedSamples: number
  qualifiedSamples: number
  unqualifiedSamples: number
  /** 合格率（%）；无有效结论时为 null，与 0% 语义不同 */
  qualifiedRate: number | null
  pendingSamples: number
}

/** 名称-数值型统计项（饼图/柱图通用） */
export interface StatNameValue {
  name: string
  value: number
  percent: number | null
}

/** 月度趋势项 */
export interface StatTrend {
  month: string
  sampleCount: number
  completedCount: number
}

/** 总览 */
export const getStatOverviewApi = () => get<StatOverview>('/stat/overview')

/** 样品状态分布（按状态机阶段） */
export const getSampleStatusStatApi = () => get<StatNameValue[]>('/stat/sample-status')

/** 检验类别分布 */
export const getInspectTypeStatApi = () => get<StatNameValue[]>('/stat/inspect-type')

/** 送检单位 Top N */
export const getTopClientsStatApi = (limit = 10) =>
  get<StatNameValue[]>('/stat/top-clients', { limit })

/** 样品大类分布 */
export const getCategoryStatApi = (limit = 10) => get<StatNameValue[]>('/stat/category', { limit })

/** 检验员工作量 */
export const getTesterWorkloadStatApi = (limit = 10) =>
  get<StatNameValue[]>('/stat/tester-workload', { limit })

/** 部门工作量 */
export const getDeptStatApi = () => get<StatNameValue[]>('/stat/dept')

/** 不合格项目 Top N */
export const getUnqualifiedItemsStatApi = (limit = 10) =>
  get<StatNameValue[]>('/stat/unqualified-items', { limit })

/** 月度趋势（近 N 个月，补零） */
export const getMonthlyTrendStatApi = (months = 6) =>
  get<StatTrend[]>('/stat/monthly-trend', { months })
