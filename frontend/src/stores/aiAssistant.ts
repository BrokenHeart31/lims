/**
 * AI 助手悬浮窗状态（Pinia）—— feature A，设计 §2.7。
 *
 * 职责：
 *   · 三态开合状态机（collapsed 气泡 / expanded 面板 / conversation 对话）；
 *   · 拖拽位置记忆（`localStorage['lims_ai_panel_pos']`，键风格与 `lims_access_token` 一致）；
 *   · 服务健康状态（离线静默降级）；
 *   · 会话消息与流式生成（含「停止」）；
 *   · **跨页上下文联动**：`openWithContext({sampleNo,status,stdNo,presetQuestion})`，
 *     供结果录入 / 报告审核等页面「一键带入当前样品」。
 *
 * 说明：本 store 只做「展示层编排」，不落任何业务数据；问答留痕由后端完成。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { companionApi, companionFeedbackApi, flowGuideApi, getAiStatusApi } from '@/api/ai'
import { streamAiChat } from '@/utils/aiStream'
import { useAuthStore } from '@/stores/auth'
import type {
  AiChatContext,
  AiChatMessage,
  AiCompanionHint,
  AiOpenContext,
  AiStatusVO,
  AiSuggestion,
  FlowGuide,
} from '@/types/ai'

/** 悬浮窗三态 */
export type AiPanelState = 'collapsed' | 'expanded' | 'conversation'

/** 位置记忆键（与 token 键同为 `lims_*` 风格） */
const POS_KEY = 'lims_ai_panel_pos'

/** 建议条会话静默集键（关闭过的 hintKey 本会话不再提示） */
const MUTED_KEY = 'lims_ai_muted_hint_keys'

/** 全局伴随开关键（默认开，可一键关并记忆） */
const COMPANION_ON_KEY = 'lims_ai_companion_on'

/** 建议条防抖间隔（页面聚焦/切换项目时避免抖动请求） */
const COMPANION_DEBOUNCE_MS = 400

interface StoredPos {
  x: number
  y: number
}

/** 读取本地记忆的位置；损坏/越界由调用方在挂载时矫正 */
function readStoredPos(): StoredPos | null {
  try {
    const raw = localStorage.getItem(POS_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<StoredPos>
    if (typeof parsed.x === 'number' && typeof parsed.y === 'number') {
      return { x: parsed.x, y: parsed.y }
    }
  } catch {
    // 损坏缓存直接忽略，回到默认位置
  }
  return null
}

/** 前端消息自增序号（仅用于 :key，不参与业务） */
let messageSeq = 0
function nextMessageId(): string {
  messageSeq += 1
  return `aim_${Date.now()}_${messageSeq}`
}

/** 读取会话静默集（损坏即视为空） */
function readMuted(): Set<string> {
  try {
    const raw = sessionStorage.getItem(MUTED_KEY)
    if (!raw) return new Set()
    const arr = JSON.parse(raw) as unknown
    if (Array.isArray(arr)) return new Set(arr.filter((v): v is string => typeof v === 'string'))
  } catch {
    // 损坏缓存忽略
  }
  return new Set()
}

/** 读取全局伴随开关（默认开） */
function readCompanionEnabled(): boolean {
  try {
    return localStorage.getItem(COMPANION_ON_KEY) !== '0'
  } catch {
    return true
  }
}

export const useAiAssistantStore = defineStore('aiAssistant', () => {
  const authStore = useAuthStore()

  /** 是否具备使用 AI 助手的权限（悬浮窗整体可见性由它决定） */
  const available = computed(() => authStore.hasPermission('ai:chat'))

  const panelState = ref<AiPanelState>('collapsed')
  const position = ref<StoredPos | null>(readStoredPos())
  const context = ref<AiChatContext | null>(null)
  const messages = ref<AiChatMessage[]>([])
  const conversationId = ref<number | null>(null)
  const status = ref<AiStatusVO | null>(null)
  const statusLoading = ref(false)
  const sending = ref(false)

  let controller: AbortController | null = null

  /** 服务是否在线 */
  const online = computed(() => status.value?.online ?? false)
  /** 模型是否就绪 */
  const modelReady = computed(() => status.value?.modelPresent ?? false)
  /** 状态提示文案 */
  const statusHint = computed(
    () => status.value?.hint ?? '正在探测本地 AI 服务状态…',
  )
  /** 离线时的启动脚本路径 */
  const startScript = computed(
    () => status.value?.startScript ?? 'ai/scripts/start-ollama.ps1',
  )
  /** 是否正在生成（用于输入区禁用与「停止」按钮） */
  const isGenerating = computed(() => messages.value.some((m) => m.streaming))

  // ---- 增量：标准伴随建议条 + 流程引导 ----
  /** 后端返回的建议条（含被静默者） */
  const companionHints = ref<AiCompanionHint[]>([])
  /** 会话级已展示键（同项目同会话只提示一次） */
  const shownHintKeys = ref<Set<string>>(new Set())
  /** 会话级静默集（用户关闭过的键） */
  const mutedHintKeys = ref<Set<string>>(readMuted())
  /** 全局伴随开关 */
  const companionEnabled = ref<boolean>(readCompanionEnabled())
  /** 缓存的流程引导（可空） */
  const flowGuide = ref<FlowGuide | null>(null)

  let companionTimer: ReturnType<typeof setTimeout> | null = null

  /** 当前可视建议条（剔除静默键） */
  const visibleHints = computed(() =>
    companionHints.value.filter((h) => !mutedHintKeys.value.has(h.hintKey)),
  )
  /** 气泡是否显示不打扰角标 */
  const hasCompanionBadge = computed(() => companionEnabled.value && visibleHints.value.length > 0)

  /** 记录拖拽后的位置（含本地持久化） */
  function setPosition(x: number, y: number): void {
    const next: StoredPos = { x: Math.round(x), y: Math.round(y) }
    position.value = next
    try {
      localStorage.setItem(POS_KEY, JSON.stringify(next))
    } catch {
      // 存储不可用（隐私模式等）时仅内存生效，不影响使用
    }
  }

  /** 打开面板（collapsed → expanded） */
  function expand(): void {
    if (panelState.value === 'collapsed') panelState.value = 'expanded'
  }

  /** 收起为气泡 */
  function collapse(): void {
    panelState.value = 'collapsed'
  }

  /** 切换气泡/面板 */
  function toggle(): void {
    if (panelState.value === 'collapsed') panelState.value = 'expanded'
    else panelState.value = 'collapsed'
  }

  /** 探测服务状态（静默：失败不弹全局错误，置 null 由 UI 显示未知/降级） */
  async function refreshStatus(): Promise<void> {
    statusLoading.value = true
    try {
      status.value = await getAiStatusApi()
    } catch {
      status.value = null
    } finally {
      statusLoading.value = false
    }
  }

  /** 离线降级：把状态置为「离线 + 可读提示」（用于 SSE 返回 4201 等场景） */
  function markOffline(hint: string): void {
    status.value = {
      online: false,
      baseUrl: status.value?.baseUrl ?? 'http://127.0.0.1:11434',
      model: status.value?.model ?? '',
      modelPresent: false,
      latencyMs: -1,
      hint,
      startScript: status.value?.startScript ?? 'ai/scripts/start-ollama.ps1',
      kbReady: status.value?.kbReady ?? false,
      kbDocCount: status.value?.kbDocCount ?? 0,
    }
  }

  /** 清空当前会话（保留服务状态与位置） */
  function clearConversation(): void {
    messages.value = []
    conversationId.value = null
  }

  /**
   * 发送一个问题（流式）。
   * @returns 是否真正发起了请求
   */
  async function send(question: string): Promise<boolean> {
    const q = question.trim()
    if (!q || sending.value) return false
    if (!available.value) return false

    panelState.value = 'conversation'
    messages.value.push({
      id: nextMessageId(),
      role: 'user',
      content: q,
      citations: [],
      refused: false,
      suggestions: [],
      flowGuide: null,
      valueAnchors: [],
      streaming: false,
    })
    messages.value.push({
      id: nextMessageId(),
      role: 'assistant',
      content: '',
      citations: [],
      refused: false,
      suggestions: [],
      flowGuide: null,
      valueAnchors: [],
      streaming: true,
    })
    // ⚠️ 必须从数组里**取回响应式代理**再改，不能保留 push 时那个裸对象引用（2026-09-18 修复）。
    //
    // 原因（已用实验证实，非推断）：`messages` 是 `ref([])`，`push` 进去的裸对象被原样存进数组，
    // 只有通过数组读出来才会被包成响应式代理。若继续改裸对象，**视图完全不会更新**：
    // effect 不重跑 ⇒ onToken 追加的正文、citations、streaming=false 全都看不见
    // ⇒ 界面永久停在「正在生成」，而关闭悬浮窗再打开（组件重新挂载、重新读一遍）才显示答案。
    // 这正是用户实测遇到的问题，务必保留此写法。
    const assistant = messages.value[messages.value.length - 1]

    sending.value = true
    controller = new AbortController()
    const body = {
      conversationId: conversationId.value,
      question: q,
      context: context.value,
    }
    try {
      await streamAiChat(
        body,
        {
          onRefs: (refs) => {
            assistant.citations = refs.citations
            assistant.domain = refs.domain
            if (refs.refused) assistant.refused = true
            assistant.flowGuide = refs.flowGuide ?? null
            assistant.valueAnchors = refs.valueAnchors ?? []
          },
          onToken: (text) => {
            assistant.content += text
          },
          onDone: (done) => {
            if (done.conversationId != null) conversationId.value = done.conversationId
            assistant.streaming = false
            assistant.elapsedMs = done.elapsedMs ?? null
            assistant.confidence = done.confidence ?? null
            if (done.refused) assistant.refused = true
            if (done.suggestions && done.suggestions.length > 0) {
              assistant.suggestions = done.suggestions
            }
          },
          onError: (err) => {
            assistant.streaming = false
            assistant.error = err.msg
            if (err.code === 4201) markOffline(err.msg)
          },
          onIncomplete: () => {
            // 连接结束但没收到 done 帧：回答可能被截断。
            // 必须显式收敛，否则界面会永久停在「正在生成」（用户实测过的问题）。
            assistant.error = 'AI 响应未正常结束，回答可能不完整，请重试。'
          },
        },
        controller.signal,
      )
    } finally {
      // 兜底：任何路径结束都不应残留「生成中」
      assistant.streaming = false
      sending.value = false
      controller = null
    }
    return true
  }

  /** 停止生成（中止当前流） */
  function stop(): void {
    controller?.abort()
    controller = null
    sending.value = false
    for (const m of messages.value) {
      if (m.streaming) m.streaming = false
    }
  }

  /**
   * 页面联动入口：带入上下文打开悬浮窗。
   * 若带 `presetQuestion`，则打开后立即提问。
   */
  function openWithContext(ctx: AiOpenContext): void {
    context.value = {
      sampleNo: ctx.sampleNo,
      status: ctx.status,
      stdNo: ctx.stdNo,
      itemName: ctx.itemName,
      basisCode: ctx.basisCode,
      pageKey: ctx.pageKey,
      roleCode: ctx.roleCode,
    }
    if (ctx.presetQuestion) {
      panelState.value = 'conversation'
      void send(ctx.presetQuestion)
    } else {
      panelState.value = 'expanded'
    }
    if (!status.value) void refreshStatus()
  }

  /** 清除上下文（新建会话时使用） */
  function clearContext(): void {
    context.value = null
  }

  /** 采纳一条拒答替代建议 */
  function useSuggestion(suggestion: AiSuggestion): void {
    void send(suggestion.query)
  }

  // =========================================================================
  // 增量：页面只读联动上下文 + 标准伴随建议条 + 流程引导
  // =========================================================================

  /**
   * 页面只读联动：带入当前上下文（**不主动展开面板、不聚焦输入框**）。
   * 若携带 `itemName`，触发一次防抖的建议条查询（误报率=0：无项目名绝不主动）。
   */
  function pushContext(ctx: AiChatContext): void {
    context.value = {
      sampleNo: ctx.sampleNo,
      status: ctx.status,
      stdNo: ctx.stdNo,
      itemName: ctx.itemName,
      basisCode: ctx.basisCode,
      pageKey: ctx.pageKey,
      roleCode: ctx.roleCode,
    }
    queueCompanionCheck()
  }

  /** 全局伴随开关（持久化） */
  function setCompanionEnabled(on: boolean): void {
    companionEnabled.value = on
    try {
      localStorage.setItem(COMPANION_ON_KEY, on ? '1' : '0')
    } catch {
      // 存储不可用时仅内存生效
    }
    if (!on) companionHints.value = []
  }

  /** 防抖触发建议条查询（页面可能快速切换项目） */
  function queueCompanionCheck(): void {
    if (companionTimer) clearTimeout(companionTimer)
    companionTimer = setTimeout(() => {
      void checkCompanion()
    }, COMPANION_DEBOUNCE_MS)
  }

  /**
   * 查询建议条（A14）。仅当「具备权限 + 全局开关开 + 上下文有 itemName」时发起；
   * 返回结果按 `shownHintKeys` / `mutedHintKeys` 过滤后置入 `visibleHints`。
   * 失败静默（伴随是旁路，绝不打扰业务）。
   */
  async function checkCompanion(): Promise<void> {
    if (!available.value || !companionEnabled.value) {
      companionHints.value = []
      return
    }
    const ctx = context.value
    if (!ctx?.itemName) {
      companionHints.value = []
      return
    }
    try {
      const hints = await companionApi(ctx)
      // 记录已展示键（同会话同项目只提示一次）
      for (const h of hints) shownHintKeys.value.add(h.hintKey)
      companionHints.value = hints.filter(
        (h) => !mutedHintKeys.value.has(h.hintKey),
      )
    } catch {
      companionHints.value = []
    }
  }

  /** 关闭（静默）一条建议条：本会话对该 hintKey 不再提示 */
  function dismissHint(hintKey: string): void {
    const next = new Set(mutedHintKeys.value)
    next.add(hintKey)
    mutedHintKeys.value = next
    companionHints.value = companionHints.value.filter((h) => h.hintKey !== hintKey)
    try {
      sessionStorage.setItem(MUTED_KEY, JSON.stringify([...next]))
    } catch {
      // 存储不可用时仅内存生效
    }
  }

  /**
   * 打开一条建议条：回执留痕（A15，clicked=true）+ 展开会话并提问（**只读，不代操作**）。
   */
  function openHint(hint: AiCompanionHint): void {
    void companionFeedbackApi({
      hintKey: hint.hintKey,
      sampleNo: hint.sampleNo ?? undefined,
      itemName: hint.itemName ?? undefined,
      stdNo: hint.stdNo ?? undefined,
      pageKey: context.value?.pageKey,
      clicked: true,
    }).catch(() => {
      // 留痕失败不影响阅读
    })
    const q = hint.itemName
      ? `${hint.itemName} 的相关限量标准是什么`
      : hint.oneLine
    panelState.value = 'conversation'
    void send(q)
  }

  /** 请求并缓存流程引导（A16，事实层确定性，不调模型） */
  async function openFlowGuide(ctx?: AiChatContext): Promise<void> {
    const c = ctx ?? context.value ?? undefined
    try {
      flowGuide.value = await flowGuideApi(c?.sampleNo, c?.status)
    } catch {
      flowGuide.value = null
    }
  }

  return {
    available,
    panelState,
    position,
    context,
    messages,
    conversationId,
    status,
    statusLoading,
    sending,
    online,
    modelReady,
    statusHint,
    startScript,
    isGenerating,
    companionHints,
    visibleHints,
    companionEnabled,
    hasCompanionBadge,
    flowGuide,
    setPosition,
    expand,
    collapse,
    toggle,
    refreshStatus,
    markOffline,
    clearConversation,
    clearContext,
    send,
    stop,
    openWithContext,
    useSuggestion,
    pushContext,
    setCompanionEnabled,
    queueCompanionCheck,
    checkCompanion,
    dismissHint,
    openHint,
    openFlowGuide,
  }
})
