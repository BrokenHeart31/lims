/**
 * 认证域接口封装
 *
 * ⚠️ 契约说明：api-spec.md 认证域尚未由 Copilot 落地（T-002），
 * 本文件按 AGENTS.md 4.1 / 8.3 已定义的约定实现：
 *   - 统一响应 { code, msg, data }
 *   - POST /api/auth/login 返回 JWT（access_token + refresh_token）
 *   - GET  /api/auth/me   返回 用户 + 角色 + 权限标识集合 + 菜单树
 * 字段命名（camelCase/snake_case）以 Copilot 终审后的 api-spec.md 为准，
 * 如有出入仅需调整本文件与 stores/auth.ts 中的类型定义。
 */
import { get, post } from '@/utils/request'

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  /** 所属部门名称（数据权限展示用） */
  deptName?: string
  /** 角色编码集合，如 ['R1'] */
  roles: string[]
}

export interface MenuNode {
  id: number
  title: string
  path?: string
  icon?: string
  children?: MenuNode[]
}

export interface MeResult {
  user: UserInfo
  /** 权限标识集合（resource:action），供 v-permission 使用 */
  permissions: string[]
  /** 菜单树，用于动态生成路由与侧边栏 */
  menus: MenuNode[]
}

/** 登录：POST /api/auth/login */
export function loginApi(payload: LoginPayload): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload)
}

/** 当前登录用户信息：GET /api/auth/me */
export function fetchMeApi(): Promise<MeResult> {
  return get<MeResult>('/auth/me')
}
