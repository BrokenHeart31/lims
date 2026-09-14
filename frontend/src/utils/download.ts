/**
 * 下载工具（T-802 / T-603 导出复用；2026-09-14 升级为「可自选保存位置」）
 * =============================================================================
 * 两类下载能力：
 *   `saveBlobAs()`  —— **让用户选择保存位置**（File System Access API 的「另存为」对话框），
 *                       不支持该 API 的浏览器自动退回默认下载目录；
 *   `downloadBlob()` —— 直接落到浏览器默认下载目录（仅作 API 缺失时的兜底）。
 *
 * 为什么默认走「另存为」对话框（用户 2026-09-14 要求）：
 *   固定落到浏览器默认目录时，用户下载完不知道文件去了哪里（尤其"点了没反应"时更难判断），
 *   也无法把「省平台上报数据」「检验任务」这类文件直接存到工作目录。
 *   让用户显式选择位置，既符合桌面软件习惯，也把「存哪儿」的决定权交还给用户。
 *
 * ⚠️ 实现上最关键的坑：`showSaveFilePicker()` 依赖**瞬时用户激活态（transient activation）**，
 *    任何 `await` 之后用户激活态即失效并抛 `SecurityError`。
 *    因此本模块接受的是**数据工厂函数**（`() => Promise<Blob>`）而不是已完成的 Blob：
 *    **先弹对话框，再取数据**。调用方不得先 `await` 请求再把 Blob 传进来。
 *
 * 另一处历史坑：dev server 对 `.xlsx` 返回**空 Content-Type**，
 * 所以静态模板下载一律走 fetch-as-blob 后写入，而不是 `<a href download>` 直链——
 * 直链在新标签页打开时浏览器拿不到类型会「什么都不做」。
 */
import { ElMessage } from 'element-plus'

/** 一次保存动作的结果 */
export type SaveOutcome =
  /** 用户选定位置并写入成功 */
  | 'saved'
  /** 用户在保存对话框里点了取消 */
  | 'cancelled'
  /** 浏览器不支持「另存为」，已退回默认下载目录 */
  | 'fallback'

/** 允许的文件类型声明（与 File System Access API 的 types 字段一致） */
export interface SaveFileType {
  description?: string
  /** MIME → 扩展名数组，如 { 'text/csv': ['.csv'] } */
  accept: Record<string, string[]>
}

/* ---------------------------------------------------------------------------
 * File System Access API 的最小类型声明
 * （TS 内置 lib.dom 尚未包含 showSaveFilePicker；按需声明，避免使用 any）
 * ------------------------------------------------------------------------- */
interface WritableLike {
  write(data: Blob): Promise<void>
  close(): Promise<void>
}

interface FileHandleLike {
  createWritable(): Promise<WritableLike>
}

interface SavePickerOptions {
  suggestedName?: string
  types?: SaveFileType[]
}

type SavePicker = (options?: SavePickerOptions) => Promise<FileHandleLike>

/** 取 `window.showSaveFilePicker`（不存在返回 null） */
function getSavePicker(): SavePicker | null {
  const candidate = (window as unknown as { showSaveFilePicker?: unknown }).showSaveFilePicker
  return typeof candidate === 'function' ? (candidate.bind(window) as SavePicker) : null
}

/** 当前浏览器是否支持「另存为」对话框（Edge / Chrome 系支持；Safari / Firefox 暂不支持） */
export function canChooseSaveLocation(): boolean {
  return getSavePicker() !== null
}

/* ---------------------------------------------------------------------------
 * 文件名工具
 * ------------------------------------------------------------------------- */

/** Blob → 文件名 的弱映射（不阻止 Blob 被回收） */
const filenameByBlob = new WeakMap<Blob, string>()

/** 记录文件名并原样返回 Blob（便于在请求层链式调用） */
export function rememberFilename(blob: Blob, filename: string | null): Blob {
  if (filename) {
    filenameByBlob.set(blob, filename)
  }
  return blob
}

/** 取回随 Blob 记录的文件名（未记录返回 null） */
export function filenameOfBlob(blob: Blob): string | null {
  return filenameByBlob.get(blob) ?? null
}

/**
 * 解析 Content-Disposition 中的文件名。
 * 优先 filename*=UTF-8''（RFC 5987），其次 filename="..."。
 */
export function parseContentDispositionFilename(disposition?: string | null): string | null {
  if (!disposition) {
    return null
  }
  const starMatch = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)
  const starValue = starMatch?.[1]
  if (starValue) {
    const trimmed = starValue.trim()
    try {
      return decodeURIComponent(trimmed)
    } catch {
      return trimmed
    }
  }
  const plainMatch = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition)
  const plainValue = plainMatch?.[1]
  return plainValue ? plainValue.trim() : null
}

/** 生成 `yyyyMMddHHmmss` 时间戳后缀 */
export function timestampSuffix(): string {
  const d = new Date()
  const pad = (n: number): string => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}` +
    `${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  )
}

/**
 * 生成带时间戳的导出文件名，如 `检验任务20260914161500.xlsx`。
 *
 * 为什么要时间戳：导出内容是「当时的数据快照」，同名文件连续下载会互相覆盖，
 * 也不利于用户分辨哪一次是哪一次。
 *
 * 注：这只是「另存为」对话框里的**建议文件名**，用户可在对话框里改名。
 * （必须先弹对话框再发请求，因此此刻还拿不到服务端 Content-Disposition 里的名字。）
 */
export function suggestedExportName(prefix: string, ext: string): string {
  return `${prefix}${timestampSuffix()}.${ext}`
}

/** 常用类型声明：Excel / CSV */
export const XLSX_FILE_TYPE: SaveFileType = {
  description: 'Excel 工作簿',
  accept: {
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': ['.xlsx'],
  },
}

export const CSV_FILE_TYPE: SaveFileType = {
  description: 'CSV 文本',
  accept: { 'text/csv': ['.csv'] },
}

/* ---------------------------------------------------------------------------
 * 保存
 * ------------------------------------------------------------------------- */

/**
 * 触发浏览器默认下载（兜底路径，不弹对话框）。
 * 文件名优先级：入参 → 随 Blob 记录的名字 → 时间戳兜底名。
 */
export function downloadBlob(blob: Blob, filename?: string): void {
  const name = filename ?? filenameOfBlob(blob) ?? suggestedExportName('导出数据', 'xlsx')
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

/**
 * 「另存为」：弹出系统保存对话框让用户选择位置，确认后再写入数据。
 *
 * @param source 数据来源。传**函数**（推荐）时在对话框确认后才发起请求；
 *               传 Blob 用于纯前端生成的内容（如 CSV 模板）。
 *               ⚠️ 不得先 `await` 数据再传入——那会丢失用户激活态并抛 SecurityError。
 * @param suggestedName 对话框中的建议文件名
 * @param type 可选的文件类型声明（决定对话框里的类型筛选）
 * @returns 保存结果（成功 / 用户取消 / 退回默认下载）
 */
export async function saveBlobAs(
  source: Blob | (() => Promise<Blob>),
  suggestedName: string,
  type?: SaveFileType,
): Promise<SaveOutcome> {
  const resolveBlob = async (): Promise<Blob> =>
    typeof source === 'function' ? await source() : source

  const picker = getSavePicker()
  if (!picker) {
    // 浏览器不支持「另存为」：退回默认下载目录，功能不缺失
    downloadBlob(await resolveBlob(), suggestedName)
    return 'fallback'
  }

  // ① 先弹对话框（必须早于任何 await，否则抛 SecurityError: must be handling a user gesture）
  let handle: FileHandleLike
  try {
    handle = await picker({ suggestedName, types: type ? [type] : undefined })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return 'cancelled'
    }
    // 其它异常（策略限制 / 系统对话框不可用）：退回默认下载，保证「文件一定能拿到」
    downloadBlob(await resolveBlob(), suggestedName)
    return 'fallback'
  }

  // ② 用户已选定位置，再取数据并写入
  try {
    const blob = await resolveBlob()
    const writable = await handle.createWritable()
    await writable.write(blob)
    await writable.close()
    return 'saved'
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return 'cancelled'
    }
    downloadBlob(await resolveBlob(), suggestedName)
    return 'fallback'
  }
}

/**
 * 统一提示保存结果（避免各调用点重复写文案，也保证三种结果都有明确反馈）。
 *
 * @param outcome `saveBlobAs` 的返回值
 * @param label 业务名称，如「检验任务」「采样单导入模板」
 */
export function notifySaveOutcome(outcome: SaveOutcome, label: string): void {
  if (outcome === 'cancelled') {
    ElMessage.info(`已取消保存${label}`)
    return
  }
  if (outcome === 'fallback') {
    ElMessage.warning(
      `${label}已存到浏览器默认下载目录（当前浏览器不支持「另存为」对话框，可在浏览器设置中修改该目录）`,
    )
    return
  }
  ElMessage.success(`${label}已保存`)
}
