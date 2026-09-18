/**
 * AI 助手（feature A）前端类型 —— 与后端 `com.lims.vo` / `com.lims.dto` 一一对应。
 *
 * 契约见 docs/api/api-spec.md 第 16 章、设计 §4.1。
 * 增量 ai_flow_assistant：标准伴随（A14/A15）、流程引导（A16）、OCR 任务（A17~A19）。
 * 红线（AGENTS 5）：**禁止 any**；字段名 camelCase，与 VO 严格一致；时间字段按后端
 * 全局 Jackson 配置序列化为 `yyyy-MM-dd HH:mm:ss` 字符串。
 */

/** 回答领域（后端 DomainGuard 输出） */
export type AiDomain = 'business' | 'standard' | 'other'

/** A1 服务健康检查响应（离线仍是 HTTP 200 + code=0，`online=false`） */
export interface AiStatusVO {
  /** 服务是否在线（/api/tags 可达） */
  online: boolean
  /** 服务地址（回环口） */
  baseUrl: string
  /** 期望模型名 */
  model: string
  /** 模型是否已就绪 */
  modelPresent: boolean
  /** 健康检查往返延迟（毫秒）；离线为 -1 */
  latencyMs: number
  /** 提示文案（在线：「AI 服务正常」；离线：启动指引） */
  hint: string
  /** 离线时展示的启动脚本路径 */
  startScript: string
  /** 三就绪之「标准索引就绪」：是否已入库 ≥1 篇标准（增量 C-11） */
  kbReady: boolean
  /** 已入库标准文档数（增量 C-11） */
  kbDocCount: number
}

/** A2/A3 对话上下文（全部可选，对应后端 AiChatContextDTO） */
export interface AiChatContext {
  /** 当前样品编号 */
  sampleNo?: string
  /** 当前样品状态 code（前端已知时直传） */
  status?: number
  /** 当前标准号（限定检索范围） */
  stdNo?: string
  /** 当前正在看的检测项目名（增量：**标准伴随查询的触发条件**，为空则不主动提示） */
  itemName?: string
  /** 当前项目的判定依据标准号（增量：优先用于解析伴随查询的标准号） */
  basisCode?: string
  /** 来源页面标识（result-entry / item-decompose / assign-index / report-audit / sample-register） */
  pageKey?: string
  /** 当前角色编码（R1/R2/R3/R100，供引导文案与复盘） */
  roleCode?: string
}

/** 页面联动打开悬浮窗时使用的上下文（`presetQuestion` 为前端自有字段，不下发后端） */
export interface AiOpenContext extends AiChatContext {
  /** 预置问题：打开后立即提问（如「解释这个状态」） */
  presetQuestion?: string
}

/** A2 引用（标准条款卡片） */
export interface AiCitationVO {
  stdNo: string
  clauseNo?: string | null
  clauseTitle?: string | null
  snippet: string
  docId?: number | null
  sourceFile?: string | null
  score?: number | null
  /** 来源类型 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR（增量） */
  sourceType?: number | null
  /** 来源类型中文名（文本版 / 扫描件OCR）（增量） */
  sourceTypeLabel?: string | null
  /** 是否来自扫描件 OCR（增量）：true → 强制渲染警示标签 */
  ocrDerived?: boolean | null
}

/** 数值对齐卡片（增量 T02）：数值只来自系统标准库，**绝不取 OCR、绝不由模型断言** */
export interface AiValueAnchor {
  itemName: string
  unit?: string | null
  /** 系统标准值（权威：product_lib_item / sample_item 快照） */
  stdValue: string
  /** 判定类型中文名：限量比较 / 不得检出（不得使用）/ 文本或感官人工 */
  judgeTypeLabel?: string | null
  /** 是否参考性限量 */
  isReference?: boolean | null
  /** 判定依据标准号 */
  basisCode?: string | null
  /** 来源标准库明细 ID（product_lib_item.id） */
  libItemId?: number | null
  /** 跳转路径（仅当当前用户具备 `base:lib:list` 权限时给出；否则为 null → 只展示值） */
  jumpPath?: string | null
  /** 来源标签（固定「系统标准库（权威）」） */
  sourceLabel?: string | null
}

/** 标准伴随建议条（增量 A14） */
export interface AiCompanionHint {
  /** 去重键（= sampleNo|itemName|stdNo） */
  hintKey: string
  sampleNo?: string | null
  itemName?: string | null
  stdNo?: string | null
  stdTitle?: string | null
  /** 建议条一行文案 */
  oneLine: string
  /** 条款预览（top 1~2 条，含来源类型） */
  citationPreview: AiCitationVO[]
  /** 数值对齐卡片（可能为 null） */
  valueAnchor?: AiValueAnchor | null
}

/** A15 建议条留痕请求（点击/展示回执） */
export interface AiCompanionFeedbackDTO {
  hintKey: string
  sampleNo?: string
  itemName?: string
  stdNo?: string
  pageKey?: string
  clicked: boolean
}

/** 流程引导单个步骤（事实字段，模型不得生成/改写） */
export interface FlowGuideStep {
  /** 该步骤所属状态 code */
  status?: number | null
  stageLabel?: string | null
  /** 下一步动作（主语=下一步做什么） */
  nextAction?: string | null
  /** 入口路由（前端 router.push） */
  entryPath?: string | null
  /** 所需权限标识 */
  requiredPermission?: string | null
  /** 当前用户是否具备该权限（false → 灰显、不给跳转） */
  hasPermission: boolean
  /** 字段/按钮级指引 */
  fieldHint?: string | null
  /** 是否当前步 */
  isCurrent: boolean
  /** 负责人角色（R1/R2/R3/R100，供文案「找谁开权限」） */
  actorRole?: string | null
}

/** 业务全流程引导（增量 A16，事实层确定性装配，不调模型） */
export interface FlowGuide {
  sampleNo?: string | null
  currentStatus?: number | null
  currentStatusLabel?: string | null
  /** 当前步 1-based 序号（0=未标当前步/总览） */
  stageIndex: number
  /** 下一步（= 当前状态对应的动作事实）；总览时为 null */
  nextStep?: FlowGuideStep | null
  /** 全部步骤（S10→S90，含权限可见性） */
  steps: FlowGuideStep[]
}

/** 扫描件 OCR 单页失败（逐页列理由，fail-loud） */
export interface ScanOcrFailedPage {
  page: number
  reason?: string | null
}

/** 扫描件 OCR 任务（增量 A17/A18，读侧车位文件，不代跑） */
export interface ScanOcrJob {
  /** 归一化标准号键（如 GB-2763-2021） */
  stdKey: string
  sourceFile?: string | null
  stdNo?: string | null
  /** 模式（ocr） */
  mode?: string | null
  totalPages?: number | null
  /** 已完成页数（= 片段文件数） */
  donePages?: number | null
  /** 失败页清单 */
  failedPages: ScanOcrFailedPage[]
  /** pending | running | done | partial_failed */
  status?: string | null
  /** 完成百分比（保留 1 位） */
  percent?: number | null
  /** 产物文件名 */
  outputTxt?: string | null
  updatedAt?: string | null
}

/** 扫描件 OCR 重试响应（增量 A19）—— **返回待执行命令，系统不代跑** */
export interface ScanOcrRetry {
  stdKey: string
  /** 已复位的待跑页码清单 */
  pendingPages: number[]
  /** 待用户在图终端执行的命令 */
  commandToRun?: string | null
  /** 说明文案 */
  note?: string | null
}

/** 拒答时的替代建议条目 */
export interface AiSuggestion {
  /** 展示文案 */
  text: string
  /** 点击后作为问题重发 */
  query: string
}

/** A2 非流式回答（结构化对象，非 Markdown） */
export interface AiAnswerVO {
  conversationId?: number | null
  messageId?: number | null
  answer: string
  refused: boolean
  domain: AiDomain | string
  confidence?: string | null
  model?: string | null
  elapsedMs?: number | null
  citations: AiCitationVO[]
  suggestions: AiSuggestion[]
  /** 业务域流程引导（增量：NEXT_STEP / GUIDE 时装配） */
  flowGuide?: FlowGuide | null
  /** 数值对齐卡片（增量：标准域装配，数值来自系统标准库） */
  valueAnchors: AiValueAnchor[]
}

/** A2/A3 对话请求（对应后端 AiChatDTO） */
export interface AiChatDTO {
  /** null=新会话 */
  conversationId?: number | null
  question: string
  context?: AiChatContext | null
}

/** A3 SSE `event: refs` 载荷 */
export interface AiStreamRefsPayload {
  citations: AiCitationVO[]
  domain: string
  refused?: boolean
  /** 增量：SSE refs 同步带 flowGuide / valueAnchors */
  flowGuide?: FlowGuide | null
  valueAnchors?: AiValueAnchor[]
}

/** A3 SSE `event: done` 载荷 */
export interface AiStreamDonePayload {
  messageId?: number | null
  conversationId?: number | null
  elapsedMs?: number | null
  confidence?: string | null
  refused?: boolean
  suggestions?: AiSuggestion[]
}

/** A3 SSE `event: error` 载荷 */
export interface AiStreamErrorPayload {
  code: number
  msg: string
}

/** A4 会话（审计列表项） */
export interface AiConversationVO {
  id: number
  title?: string | null
  userNo?: string | null
  model?: string | null
  messageCount?: number | null
  createdAt?: string | null
}

/** A5 消息明细（含引用/拒答留痕） */
export interface AiMessageVO {
  id: number
  conversationId?: number | null
  seq?: number | null
  /** 1=用户 2=助手 */
  role: number
  roleLabel?: string | null
  content: string
  domain?: string | null
  /** 0/1 */
  refused?: number | null
  citations: AiCitationVO[]
  retrievedCount?: number | null
  model?: string | null
  elapsedMs?: number | null
  createdAt?: string | null
}

/** A11 GB 标准文档（已入库列表项） */
export interface GbDocumentVO {
  id: number
  stdNo: string
  stdTitle?: string | null
  sourceFile?: string | null
  /** 1=TXT 2=HTML 3=MD 4=CSV 5=扫描件OCR */
  sourceType?: number | null
  sourceTypeLabel?: string | null
  /** 是否来自扫描件 OCR（增量） */
  ocrDerived?: boolean | null
  /** 来源可信度标签（文本版 / 扫描件OCR）（增量） */
  ocrDerivedLabel?: string | null
  checksum?: string | null
  clauseCount?: number | null
  /** 1=已完成 2=已失效 */
  status?: number | null
  statusLabel?: string | null
  createdAt?: string | null
}

/** A8/A9 GB 导入任务（真实进度，禁假进度） */
export interface GbImportJobVO {
  id: number
  fileName?: string | null
  filePath?: string | null
  /** 0=待处理 1=解析中 2=已完成 3=失败 */
  status: number
  statusLabel?: string | null
  totalFiles?: number | null
  doneFiles?: number | null
  totalClauses?: number | null
  doneClauses?: number | null
  failCount?: number | null
  errorMsg?: string | null
  startedAt?: string | null
  finishedAt?: string | null
}

/** A12 GB 检索命中项 */
export interface GbSearchHitVO {
  stdNo: string
  clauseNo?: string | null
  clauseTitle?: string | null
  snippet: string
  docId?: number | null
  sourceFile?: string | null
  score?: number | null
  /** 增量：来源类型 + 是否扫描件 OCR */
  sourceType?: number | null
  ocrDerived?: boolean | null
}

/** A7 目录扫描请求 */
export interface KbScanDTO {
  dir?: string
  /** 1=TXT 2=HTML 3=MD 4=CSV；留空按扩展名自动判定 */
  sourceType?: number
  /** 增量：是否扫描件 OCR 来源（未传则按文件名后缀 `.ocr.txt` 自动判定） */
  ocrDerived?: boolean
}

/** A12 直接检索请求 */
export interface KbSearchDTO {
  query: string
  topN?: number
  stdNo?: string
}

/** 悬浮窗内一条聊天消息（前端视图模型，非后端 VO） */
export interface AiChatMessage {
  /** 前端唯一 id（用于 :key） */
  id: string
  role: 'user' | 'assistant'
  content: string
  citations: AiCitationVO[]
  refused: boolean
  domain?: string | null
  confidence?: string | null
  model?: string | null
  elapsedMs?: number | null
  suggestions: AiSuggestion[]
  /** 增量：业务域流程引导（事实层确定性装配） */
  flowGuide?: FlowGuide | null
  /** 增量：标准域数值对齐卡片（数值来自系统标准库） */
  valueAnchors: AiValueAnchor[]
  /** 是否正在流式生成 */
  streaming: boolean
  /** 流式异常时的提示文案（非空表示该条失败） */
  error?: string | null
}
