/**
 * 监抽任务（SuperviseTask）接口封装
 *
 * ⚠️ 契约说明：api-spec.md 监抽任务域（/api/task/*）由 Copilot 终审落地（T-201），
 * 本文件先按第 0 章通用约定 + 旧 sjtask 表字段先行实现：
 *   - 分页 GET  /task/page?pageNum&pageSize&taskNo&taskName&status
 *   - 详情 GET  /task/{id}
 *   - 新建 POST /task
 *   - 更新 PUT  /task
 *   - 删除 DELETE /task/{id}
 * 字段 camelCase，统一响应 { code, msg, data }，分页 data.records/total/current/size。
 * 若 Copilot 契约字段名有调整，仅需改本文件与 views/task。
 */
import { del, get, post, put } from '@/utils/request'
import type { PageResult } from '@/types/api'

/** 任务性质字典 */
export const TASK_NATURE_OPTIONS = ['监督抽检', '委托抽样', '委托送样'] as const
/** 区域级别字典 */
export const TASK_REGION_OPTIONS = ['省级', '市级', '区级'] as const
/** 抽样环节字典 */
export const SAMPLING_STAGE_OPTIONS = ['生产', '流通', '餐饮'] as const
/** 任务状态字典 */
export const TASK_STATUS_OPTIONS = ['草稿', '进行中', '已完成', '已中止'] as const

/** 监抽任务（与后端 VO 对齐，camelCase） */
export interface SuperviseTask {
  id?: number
  /** 任务编号（唯一，如 RW-SA-20230101） */
  taskNo: string
  /** 任务名称 */
  taskName: string
  /** 任务性质：监督抽检/委托抽样/委托送样 */
  taskNature: string
  /** 任务来源（下达单位） */
  taskSource?: string
  /** 区域级别：省级/市级/区级 */
  regionLevel?: string
  /** 负责人 */
  leader?: string
  /** 批次 */
  batchNo?: string
  /** 任务接受日期 yyyy-MM-dd */
  receiveDate?: string
  /** 任务下达日期 */
  issueDate?: string
  /** 任务完成日期 */
  completeDate?: string
  /** 任务等级 */
  priority?: string
  /** 阳性率要求 */
  positiveRateRequirement?: string
  /** 抽样环节：生产/流通/餐饮 */
  samplingStage?: string
  /** 检测项目范围 */
  testScope?: string
  /** 任务状态 */
  status?: string
  /** 任务说明/备注 */
  remark?: string
  /** 审计字段（只读） */
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

/** 列表查询参数 */
export interface TaskQuery {
  pageNum: number
  pageSize: number
  taskNo?: string
  taskName?: string
  status?: string
}

/** 分页查询监抽任务：GET /task/page */
export function pageTaskApi(params: TaskQuery): Promise<PageResult<SuperviseTask>> {
  return get<PageResult<SuperviseTask>>('/task/page', params as unknown as Record<string, unknown>)
}

/** 任务详情：GET /task/{id} */
export function getTaskApi(id: number): Promise<SuperviseTask> {
  return get<SuperviseTask>(`/task/${id}`)
}

/** 新建任务：POST /task */
export function createTaskApi(payload: Omit<SuperviseTask, 'id'>): Promise<SuperviseTask> {
  return post<SuperviseTask>('/task', payload)
}

/** 更新任务：PUT /task */
export function updateTaskApi(payload: SuperviseTask): Promise<void> {
  return put<void>('/task', payload)
}

/** 删除任务：DELETE /task/{id} */
export function deleteTaskApi(id: number): Promise<void> {
  return del<void>(`/task/${id}`)
}
