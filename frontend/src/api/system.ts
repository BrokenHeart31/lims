/**
 * 系统管理域（System）接口封装 —— T-107
 *
 * 契约见 docs/api/api-spec.md 第 13 章：
 *   用户管理 /api/sys/user/*   权限 sys:user:list/add/edit/remove
 *   角色管理 /api/sys/role/*   权限 sys:role:list/add/edit/remove
 *   菜单管理 /api/sys/menu/*   权限 sys:menu:list/add/edit/remove
 *   部门管理 /api/sys/dept/*   权限 sys:dept:list/add/edit/remove
 *
 * 安全红线：用户相关接口**永不返回密码**（后端 VO 无该字段）。
 * 重置密码走独立入口 PUT /sys/user/{id}/password，请求体单向进入。
 */
import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/types/api'

// ============================================================================
// 用户管理
// ============================================================================

/** 用户查询参数 */
export interface SysUserParams {
  current: number
  size: number
  username?: string
  nickname?: string
  deptId?: number
  status?: number
}

/** 用户行 */
export interface SysUserRow {
  id: number
  username: string
  nickname: string
  deptId?: number | null
  deptName?: string | null
  email?: string | null
  phone?: string | null
  signatureUrl?: string | null
  /** 1=启用 0=停用 */
  status: number
  remark?: string | null
  roleIds: number[]
  roleNames: string[]
  createdAt?: string | null
}

/** 用户保存请求 */
export interface SysUserSaveBody {
  id?: number
  username: string
  /** 仅新增时必填；编辑时不传表示不改密码 */
  password?: string
  nickname: string
  deptId?: number | null
  email?: string
  phone?: string
  signatureUrl?: string
  status: number
  remark?: string
  roleIds: number[]
}

/** 用户分页：GET /sys/user/page */
export function pageSysUserApi(params: SysUserParams): Promise<PageResult<SysUserRow>> {
  return get<PageResult<SysUserRow>>('/sys/user/page', params as unknown as Record<string, unknown>)
}

/** 用户详情：GET /sys/user/{id} */
export function getSysUserApi(id: number): Promise<SysUserRow> {
  return get<SysUserRow>(`/sys/user/${id}`)
}

/** 用户新增：POST /sys/user */
export function createSysUserApi(body: SysUserSaveBody): Promise<number> {
  return post<number>('/sys/user', body)
}

/** 用户更新：PUT /sys/user（不含密码） */
export function updateSysUserApi(body: SysUserSaveBody): Promise<void> {
  return put<void>('/sys/user', body)
}

/** 重置密码：PUT /sys/user/{id}/password */
export function resetSysUserPasswordApi(id: number, password: string): Promise<void> {
  return put<void>(`/sys/user/${id}/password`, { password })
}

/** 用户删除：DELETE /sys/user/{id} */
export function removeSysUserApi(id: number): Promise<void> {
  return del<void>(`/sys/user/${id}`)
}

// ============================================================================
// 角色管理
// ============================================================================

/** 角色查询参数 */
export interface SysRoleParams {
  current: number
  size: number
  roleCode?: string
  roleName?: string
}

/** 角色行 */
export interface SysRoleRow {
  id: number
  roleCode: string
  roleName: string
  description?: string | null
  menuIds?: number[] | null
  userCount?: number | null
  createdAt?: string | null
}

/** 角色保存请求（menuIds 为全量覆盖式） */
export interface SysRoleSaveBody {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  menuIds: number[]
}

/** 角色分页：GET /sys/role/page */
export function pageSysRoleApi(params: SysRoleParams): Promise<PageResult<SysRoleRow>> {
  return get<PageResult<SysRoleRow>>('/sys/role/page', params as unknown as Record<string, unknown>)
}

/** 全部角色（下拉）：GET /sys/role/list */
export function listAllSysRoleApi(): Promise<SysRoleRow[]> {
  return get<SysRoleRow[]>('/sys/role/list')
}

/** 角色详情（含 menuIds）：GET /sys/role/{id} */
export function getSysRoleApi(id: number): Promise<SysRoleRow> {
  return get<SysRoleRow>(`/sys/role/${id}`)
}

/** 角色新增：POST /sys/role */
export function createSysRoleApi(body: SysRoleSaveBody): Promise<number> {
  return post<number>('/sys/role', body)
}

/** 角色更新：PUT /sys/role */
export function updateSysRoleApi(body: SysRoleSaveBody): Promise<void> {
  return put<void>('/sys/role', body)
}

/** 角色删除：DELETE /sys/role/{id} */
export function removeSysRoleApi(id: number): Promise<void> {
  return del<void>(`/sys/role/${id}`)
}

// ============================================================================
// 菜单管理
// ============================================================================

/** 菜单/权限节点（树） */
export interface SysMenuRow {
  id: number
  parentId: number
  title: string
  path?: string | null
  icon?: string | null
  /** 1=目录 2=菜单 3=按钮 */
  menuType: number
  permission?: string | null
  sortOrder: number
  /** 1=显示 0=隐藏 */
  visible: number
  children?: SysMenuRow[]
}

/** 菜单保存请求 */
export interface SysMenuSaveBody {
  id?: number
  parentId: number
  title: string
  path?: string
  icon?: string
  menuType: number
  permission?: string
  sortOrder: number
  visible: number
}

/** 菜单树：GET /sys/menu/tree（含按钮权限行，亦供角色授权树复用） */
export function treeSysMenuApi(params?: { title?: string; menuType?: number }): Promise<SysMenuRow[]> {
  return get<SysMenuRow[]>('/sys/menu/tree', params as unknown as Record<string, unknown>)
}

/** 菜单详情：GET /sys/menu/{id} */
export function getSysMenuApi(id: number): Promise<SysMenuRow> {
  return get<SysMenuRow>(`/sys/menu/${id}`)
}

/** 菜单新增：POST /sys/menu */
export function createSysMenuApi(body: SysMenuSaveBody): Promise<number> {
  return post<number>('/sys/menu', body)
}

/** 菜单更新：PUT /sys/menu */
export function updateSysMenuApi(body: SysMenuSaveBody): Promise<void> {
  return put<void>('/sys/menu', body)
}

/** 菜单删除：DELETE /sys/menu/{id} */
export function removeSysMenuApi(id: number): Promise<void> {
  return del<void>(`/sys/menu/${id}`)
}

// ============================================================================
// 部门管理
// ============================================================================

/** 部门节点（树） */
export interface DeptRow {
  id: number
  parentId: number
  deptCode: string
  deptName: string
  leader?: string | null
  remark?: string | null
  userCount?: number | null
  children?: DeptRow[]
}

/** 部门保存请求 */
export interface DeptSaveBody {
  id?: number
  parentId: number
  deptCode: string
  deptName: string
  leader?: string
  remark?: string
}

/** 部门树：GET /sys/dept/tree */
export function treeDeptApi(): Promise<DeptRow[]> {
  return get<DeptRow[]>('/sys/dept/tree')
}

/** 部门扁平列表（下拉）：GET /sys/dept/list */
export function listAllDeptApi(): Promise<DeptRow[]> {
  return get<DeptRow[]>('/sys/dept/list')
}

/** 部门详情：GET /sys/dept/{id} */
export function getDeptApi(id: number): Promise<DeptRow> {
  return get<DeptRow>(`/sys/dept/${id}`)
}

/** 部门新增：POST /sys/dept */
export function createDeptApi(body: DeptSaveBody): Promise<number> {
  return post<number>('/sys/dept', body)
}

/** 部门更新：PUT /sys/dept */
export function updateDeptApi(body: DeptSaveBody): Promise<void> {
  return put<void>('/sys/dept', body)
}

/** 部门删除：DELETE /sys/dept/{id} */
export function removeDeptApi(id: number): Promise<void> {
  return del<void>(`/sys/dept/${id}`)
}
