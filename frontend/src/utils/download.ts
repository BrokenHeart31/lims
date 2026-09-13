/**
 * 下载工具（T-802 / T-603 导出复用）
 * ----------------------------------------------------------------------------
 * 职责：
 *   - 从 Content-Disposition 响应头解析文件名（支持 RFC 5987 的 filename*=UTF-8''xxx 与普通 filename=xxx）；
 *   - 触发浏览器保存 Blob。
 *
 * 为何需要「文件名旁路」：下载接口返回的是二进制 Blob，文件名只存在于响应头里；
 * 为了让 exportApi 仍按契约返回 Promise<Blob>，这里用一个以 Blob 为键的 WeakMap 把文件名随行携带，
 * downloadBlob(blob) 时可自动取回，无需调用方再持有响应头。
 */

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

/** 生成带时间戳的兜底文件名（解析不到响应头时使用） */
function fallbackFilename(): string {
  const d = new Date()
  const pad = (n: number): string => String(n).padStart(2, '0')
  const ts =
    `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}` +
    `${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `导出数据${ts}.xlsx`
}

/**
 * 触发浏览器下载。
 *
 * @param blob 下载内容
 * @param filename 文件名；缺省时依次取「随 Blob 记录的文件名」→「时间戳兜底名」
 */
export function downloadBlob(blob: Blob, filename?: string): void {
  const name = filename ?? filenameOfBlob(blob) ?? fallbackFilename()
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
