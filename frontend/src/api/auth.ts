/**
 * 认证域接口封装
 *
 * 契约见 api-spec.md 第 1 章（已定稿）：
 *   - 统一响应 { code, msg, data }
 *   - POST /api/auth/login           登录，返回 JWT（accessToken + refreshToken）
 *   - POST /api/auth/refresh         刷新令牌
 *   - GET  /api/auth/me              当前登录用户 + 角色 + 权限标识 + 菜单树
 *   - POST /api/auth/logout          退出（无状态 JWT：服务端不做强制失效）
 *   - POST /api/auth/change-password 自服务改密（BCrypt 校验旧密码）
 * 字段命名为 camelCase，与后端 DTO/VO 一致。
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

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
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

/** 当前用户自助修改密码：POST /api/auth/change-password */
export function changePasswordApi(payload: ChangePasswordPayload): Promise<void> {
  return post<void>('/auth/change-password', payload)
}
