/**
 * AI 流式对话（SSE）解析工具 —— 原生 fetch + ReadableStream。
 *
 * <p>为什么不复用 axios：浏览器端 axios 无法以增量方式消费 `text/event-stream`
 * （需 `onDownloadProgress` + 手工切帧，且看不到「流何时结束」）。原生 fetch 的
 * `body.getReader()` 是唯一干净的做法。</p>
 *
 * <p>为什么不用 EventSource：EventSource 只能发 GET，而 A3 是 POST 且要带 JWT 头 +
 * JSON body。故采用 fetch 手工切帧（SSE 帧以空行 `\n\n` 分隔）。</p>
 */
import { TOKEN_KEY } from '@/utils/request'
import type {
  AiChatDTO,
  AiCitationVO,
  AiStreamDonePayload,
  AiStreamErrorPayload,
  AiStreamRefsPayload,
  AiSuggestion,
  AiValueAnchor,
  FlowGuide,
} from '@/types/ai'

/** `event: refs` 的解析结果（字段名与后端载荷一致） */
export interface AiStreamRefs {
  citations: AiCitationVO[]
  domain: string
  refused: boolean
  /** 增量：业务域流程引导（事实层确定性装配） */
  flowGuide?: FlowGuide | null
  /** 增量：标准域数值对齐卡片（数值来自系统标准库） */
  valueAnchors?: AiValueAnchor[]
}

/** `event: done` 的解析结果 */
export interface AiStreamDone {
  messageId?: number | null
  conversationId?: number | null
  elapsedMs?: number | null
  confidence?: string | null
  refused?: boolean
  suggestions?: AiSuggestion[]
}

/** 流式事件回调集合 */
export interface AiStreamHandlers {
  onRefs?: (refs: AiStreamRefs) => void
  onToken?: (text: string) => void
  onDone?: (done: AiStreamDone) => void
  onError?: (err: { code: number; msg: string }) => void
  /**
   * 流**结束但没收到 done 帧**时回调（2026-09-18 新增）。
   *
   * <p>为什么必须有：`done` 是后端保证「回答完整」的唯一信号；若连接被中途掐断（代理、
   * 容器、异常），读取循环会正常退出却没有任何终结事件，界面就会**永久停在「正在生成」**
   * ——这正是用户实测遇到的问题。此处把它显式暴露给上层，让 UI 一定能收敛。</p>
   */
  onIncomplete?: () => void
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * 静默超时（ms）：连续这么久没收到任何字节即判定链路挂死，主动中止。
 *
 * <p>为什么需要：HTTP 连接可能「既不断开也不发数据」（实测：后端异常掐断流时，开发代理
 * 会保持浏览器侧连接不闭，`reader.read()` 永久挂起，连 `finally` 都执行不到）。
 * 单纯依赖网络错误不足以脱困，必须由客户端兜底。</p>
 *
 * <p>取值依据：真实推理首字延迟实测约 1~3 秒（74 tok/s），检索 + 装配为毫秒级；
 * 30 秒无任何数据已远超正常波动，不会误杀长回答（生成中会持续有 token 帧，计时被重置）。</p>
 */
const STALL_TIMEOUT_MS = 30_000

/** 解析单帧 SSE 文本并派发到对应回调 */
function dispatchFrame(raw: string, handlers: AiStreamHandlers): void {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    const trimmed = line.replace(/\r$/, '')
    if (trimmed.startsWith('event:')) {
      event = trimmed.slice(6).trim()
    } else if (trimmed.startsWith('data:')) {
      dataLines.push(trimmed.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return
  let payload: unknown
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch {
    // 半帧或非 JSON：忽略该帧（SSE 允许注释/心跳，不应打断整体流）
    return
  }
  switch (event) {
    case 'refs': {
      const refs = payload as AiStreamRefsPayload
      handlers.onRefs?.({
        citations: refs.citations ?? [],
        domain: refs.domain,
        refused: refs.refused ?? false,
        flowGuide: refs.flowGuide ?? null,
        valueAnchors: refs.valueAnchors ?? [],
      })
      break
    }
    case 'token': {
      const token = payload as { t?: string }
      handlers.onToken?.(token.t ?? '')
      break
    }
    case 'done':
      handlers.onDone?.(payload as AiStreamDonePayload)
      break
    case 'error':
      handlers.onError?.(payload as AiStreamErrorPayload)
      break
    default:
      // 未知事件（如后端心跳）忽略，保持前向兼容
      break
  }
}

/**
 * 发起一次流式问答。
 *
 * @param body    对话请求（含可选上下文）
 * @param handlers 事件回调
 * @param signal   中止信号（用于「停止生成」）
 */
export async function streamAiChat(
  body: AiChatDTO,
  handlers: AiStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY)

  // 内部中止控制器：把「调用方停止」与「静默超时」统一收敛到一个 signal 上，
  // 同时保留「谁中止的」这一信息，避免把用户主动停止误报成超时错误。
  const internal = new AbortController()
  let abortedByCaller = false
  if (signal) {
    if (signal.aborted) {
      abortedByCaller = true
      internal.abort()
    } else {
      signal.addEventListener(
        'abort',
        () => {
          abortedByCaller = true
          internal.abort()
        },
        { once: true },
      )
    }
  }
  let stalled = false
  let settled = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const clearStall = (): void => {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }
  /** 收到任何数据就重置静默计时（长回答不会被误杀） */
  const armStall = (): void => {
    clearStall()
    timer = setTimeout(() => {
      stalled = true
      internal.abort()
    }, STALL_TIMEOUT_MS)
  }

  // 包一层：把 done/error 标记为「已终结」，供读取循环结束后判定是否异常收尾
  const sink: AiStreamHandlers = {
    onRefs: handlers.onRefs,
    onToken: handlers.onToken,
    onDone: (done) => {
      settled = true
      handlers.onDone?.(done)
    },
    onError: (err) => {
      settled = true
      handlers.onError?.(err)
    },
  }

  let resp: Response
  try {
    resp = await fetch(`${BASE_URL}/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal: internal.signal,
    })
  } catch (e) {
    clearStall()
    if (abortedByCaller) return
    if (stalled || (e as Error).name === 'AbortError') {
      handlers.onError?.({
        code: 4203,
        msg: 'AI 连接超时（长时间无响应），请重试；若持续失败请检查本地模型是否已启动。',
      })
      return
    }
    handlers.onError?.({
      code: 4201,
      msg: '无法连接 AI 服务，请确认本地模型已启动（见右下角状态提示）。',
    })
    return
  }

  if (!resp.ok || !resp.body) {
    clearStall()
    handlers.onError?.({
      code: resp.status,
      msg: `AI 服务响应异常（HTTP ${resp.status}）。`,
    })
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  armStall()
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      armStall()
      buffer += decoder.decode(value, { stream: true })
      let sep = buffer.indexOf('\n\n')
      while (sep >= 0) {
        const frame = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        if (frame.trim()) dispatchFrame(frame, sink)
        sep = buffer.indexOf('\n\n')
      }
    }
    clearStall()
    if (buffer.trim()) dispatchFrame(buffer, sink)
    // 连接正常结束但始终没有终结事件 ⇒ 回答可能不完整，必须让 UI 收敛（不能停在「生成中」）
    if (!settled && !abortedByCaller) {
      handlers.onIncomplete?.()
    }
  } catch (e) {
    clearStall()
    if (abortedByCaller) return
    if (stalled || (e as Error).name === 'AbortError') {
      handlers.onError?.({
        code: 4203,
        msg: 'AI 响应超时（长时间无数据），已停止等待，请重试。',
      })
      return
    }
    handlers.onError?.({ code: 4203, msg: 'AI 流式响应中断，请重试。' })
  }
}
