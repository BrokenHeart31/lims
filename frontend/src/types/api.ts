/**
 * 统一响应结构（契约见 AGENTS.md 4.1 / docs/api/api-spec.md）
 * code 约定：0 成功；401 未认证；403 无权限；400 参数错误；500 系统异常
 */
export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

/**
 * 统一分页响应（api-spec 0.3）
 * 请求：pageNum（从 1 起）/ pageSize（默认 10，上限 500）
 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}
