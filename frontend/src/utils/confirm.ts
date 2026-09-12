/**
 * 通用确认弹窗（提示词 §二十八）
 * ----------------------------------------------------------------------------
 * 区别于 ElMessageBox.confirm：固定布局 + 二次确认语义更明确
 * 用法：
 *   await confirm({
 *     title: '审核通过？',
 *     message: '通过后报告将进入「待签发」状态。',
 *     tone: 'primary',
 *     confirmText: '确认审核',
 *   })
 */
import { ElMessageBox } from 'element-plus'

export type ConfirmTone = 'primary' | 'success' | 'warning' | 'danger'

export interface ConfirmOptions {
  title: string
  message: string
  tone?: ConfirmTone
  confirmText?: string
  cancelText?: string
  /** 输入框（仅用于「退回原因」「审批备注」等场景） */
  input?: boolean
  inputPlaceholder?: string
  inputValidator?: (value: string) => boolean | string
}

export async function confirm(options: ConfirmOptions): Promise<string | null> {
  const tone = options.tone ?? 'primary'
  try {
    const result = await ElMessageBox.confirm(options.message, options.title, {
      confirmButtonText: options.confirmText ?? '确认',
      cancelButtonText: options.cancelText ?? '取消',
      type: tone === 'danger' ? 'error' : tone === 'warning' ? 'warning' : 'info',
      inputPlaceholder: options.inputPlaceholder,
      showInput: options.input ?? false,
      inputValidator: options.inputValidator,
      inputErrorMessage: '请填写完整',
      customClass: 'lims-confirm-box',
      confirmButtonClass: 'lims-confirm-btn',
    })
    // 二次确认：用户在 message 输入框（input=false 时）无值，result 可能是空字符串
    if (options.input && (typeof result !== 'string' || !result.trim())) {
      return null
    }
    return typeof result === 'string' ? result : '__ok__'
  } catch {
    return null
  }
}

/**
 * 带输入的「退回原因」专用
 */
export async function confirmReturn(reasonLabel = '退回原因'): Promise<string | null> {
  return confirm({
    title: '退回报告',
    message: `请填写${reasonLabel}（不少于 4 字）`,
    tone: 'danger',
    confirmText: '确认退回',
    cancelText: '取消',
    input: true,
    inputPlaceholder: `${reasonLabel}…`,
    inputValidator: (value: string) => (value.trim().length >= 4 ? true : `${reasonLabel}至少 4 个字`),
  })
}

/**
 * 便捷别名：(message, title, type?) → return boolean
 * - 适用于「快速判断 yes/no」的二次确认场景
 * - return true 表示用户在弹窗点了「确认」，false 表示取消或被关掉
 */
export async function askConfirm(
  message: string,
  title = '确认',
  opts: { type?: 'primary' | 'success' | 'warning' | 'danger' } = {},
): Promise<boolean> {
  const tone = opts.type ?? 'primary'
  const r = await confirm({ title, message, tone })
  return r !== null
}