/**
 * 会话令牌存储统一封装（access / refresh token 的**唯一**读写入口）。
 * -----------------------------------------------------------------------------
 * 为什么用 sessionStorage（而不是 localStorage）：
 *   · localStorage 的作用域是「同源共享」——同一浏览器**所有标签页共享同一个键空间**。
 *     多标签页登录不同账号时会互相覆盖；任一标签页 `removeItem` = 全部标签页同时失去凭证，
 *     现象即「退出任一账号，所有标签页一起掉线」。
 *   · sessionStorage 的作用域是「标签页私有」——每个标签页拥有**独立副本**，页内刷新保留，
 *     关闭标签页即自动清除。由此天然实现「多标签页会话隔离、退出互不牵连」。
 *
 * 维护约束（务必遵守）：
 *   · App 内**禁止**绕开本模块直接 `localStorage`/`sessionStorage` 读写
 *     `lims_access_token` / `lims_refresh_token` 这两个键——所有读写一律走下方导出的函数，
 *     以保证「改一处、全局一致」。
 *   · 本模块**刻意不做**「localStorage → sessionStorage 迁移」：令牌属于会话数据，
 *     升级后各标签页应重新登录一次，避免把旧键里的历史会话静默带入新作用域造成混淆。
 */

/** Access Token 的会话存储键名（禁止 Token 出现在 URL 中） */
const ACCESS_TOKEN_KEY = 'lims_access_token'

/** Refresh Token 的会话存储键名（F8 静默续期用，与 access 成对读写） */
const REFRESH_TOKEN_KEY = 'lims_refresh_token'

/**
 * 旧版残留清理：会话断代到 sessionStorage 后，localStorage 里可能仍留有升级前写入的
 * `lims_access_token` / `lims_refresh_token`。此处幂等地将其移除，防止旧键与新作用域
 * 混用造成混淆（例如残留旧 token 被别的代码误读）。**不做迁移**——升级后需重新登录一次。
 */
function purgeLegacyLocalStorage(): void {
  try {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  } catch {
    // 隐私模式等场景下 localStorage 可能不可用：清理失败不影响主流程，静默忽略。
  }
}

// 模块加载时执行一次（幂等）
purgeLegacyLocalStorage()

/** 读取当前标签页的 Access Token；不存在返回空串 `''`。 */
export function getAccessToken(): string {
  try {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
  } catch {
    // sessionStorage 不可用（极端隐私模式）：退化为「无 token」，交由后端 401 兜底。
    return ''
  }
}

/** 写入当前标签页的 Access Token（仅影响本标签页）。 */
export function setAccessToken(token: string): void {
  try {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token)
  } catch {
    // 写入失败（配额/不可用）：静默忽略，避免因存储异常中断登录主流程。
  }
}

/** 读取当前标签页的 Refresh Token；不存在返回空串 `''`。 */
export function getRefreshToken(): string {
  try {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

/** 写入当前标签页的 Refresh Token（仅影响本标签页）。 */
export function setRefreshToken(token: string): void {
  try {
    sessionStorage.setItem(REFRESH_TOKEN_KEY, token)
  } catch {
    // 同 setAccessToken：写入失败静默忽略。
  }
}

/** 清除当前标签页的两个令牌键（access + refresh）；**不影响其他标签页**。 */
export function clearAuthTokens(): void {
  try {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY)
    sessionStorage.removeItem(REFRESH_TOKEN_KEY)
  } catch {
    // 清理失败静默忽略。
  }
}
