/**
 * 路由注册表（Route Registry）—— 动态路由的「组件来源唯一真相」
 * =============================================================================
 * 设计背景（GLM 自裁，2026-09-13）
 * -----------------------------------------------------------------------------
 * 后端 `/api/auth/me` 的菜单树只提供 **结构信息**（title / path / icon / 层级 / 排序），
 * 不提供也不应提供「Vue 组件路径」——因为：
 *   ① 组件路径属于前端构建期概念，写进数据库意味着前端目录重构要改 DB；
 *   ② 数据库存 `/src/views/xxx.vue` 这类字符串会让前端无法享受 Vite 的静态分析
 *      （`import.meta.glob` 之外的手写路径无法被 tree-shaking / 预加载优化）；
 *   ③ 安全上，让 DB 决定加载哪个组件等于把「代码执行入口」交给数据，是危险的设计。
 *
 * 因此采用 **路径注册表 + 中间件转换**：
 *   前端维护 `path → 组件加载函数` 的注册表（本文件），DB 只决定「显示什么、顺序如何、谁能看」。
 *
 * 为什么不用 `import.meta.glob` 自动匹配 views 下的全部组件？
 * -----------------------------------------------------------------------------
 * 直觉方案是 glob 扫描所有 views 然后按路径匹配。但本项目的实际路径关系不是
 * 「1 个文件 = 1 个路由」的简单映射：
 *   · `/sample` 这个 URL 对应的是 `views/sample/index.vue`（多了个 index）；
 *   · `/assign/index` 保留 index 段，而 `/sample` 不保留——历史原因造成的不一致；
 *   · `/report/print` 必须放在布局**之外**（打印页要干净纸张），glob 无法表达这种元信息；
 *   · 每个路由还需要 `title` / `permissions` / 是否需要登录等 meta，glob 拿不到。
 * 与其用一堆正则去猜路径，不如**显式登记**——多写几行，但每一行都明确可验，
 * 且改错时能立即在构建期被发现（TS 类型约束）。
 *
 * 与后端数据的对接约定
 * -----------------------------------------------------------------------------
 * DB 菜单的 `path` 是「菜单路径」，与「前端路由路径」允许存在历史差异。
 * `ALIAS` 表负责把 DB 里可能出现的旧路径/别名，统一规范化到注册表的规范路径。
 * 这样：
 *   · 后端不需要为了前端改数据（用户明确要求「不为动态路由大规模改数据库」）；
 *   · 前端也不需要为兼容旧数据而把别名散落在路由生成逻辑里。
 * =============================================================================
 */
import type { RouteRecordRaw } from 'vue-router'

/** 路由节点的静态定义（组件 + 元信息），是注册表的「值」 */
export interface RouteEntry {
  /** 规范化后的路由 path（以 / 开头），也是侧栏点击后跳转的地址 */
  path: string
  /** 路由 name（用于编程式导航） */
  name: string
  /** 组件的动态导入函数 */
  component: RouteRecordRaw['component']
  /** 页面标题（document.title 与面包屑兜底） */
  title: string
  /**
   * 访问所需权限标识（任一命中即可）。
   * 省略 = 只要登录即可访问（如工作台）。
   */
  permissions?: string[]
  /**
   * 是否渲染在 MainLayout 外壳之外。
   * 目前仅报告打印页需要（白底 A4 纸张，不应带侧栏/顶栏）。
   */
  standalone?: boolean
  /**
   * 是否在侧栏展示。
   * 默认 true；设为 false 的页面只能通过代码跳转到达（如报告审核详情）。
   */
  navVisible?: boolean
}

/**
 * 路由注册表。
 *
 * ⚠️ 维护约定：新增页面时**必须**在此登记，否则动态路由无法生成该页面，
 * 且会在控制台输出 fail-loud 警告（见 modules/dynamicRoutes.ts）。
 * 这是刻意设计的「显式优于隐式」——漏登记的页面会立刻暴露，而不是静默 404。
 */
export const ROUTE_REGISTRY: RouteEntry[] = [
  // ---------------- 工作台 ----------------
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    title: '工作台',
  },

  // ---------------- 业务管理（七阶段主线 1~4） ----------------
  {
    path: '/task',
    name: 'supervise-task',
    component: () => import('@/views/task/index.vue'),
    title: '监抽任务',
  },
  {
    path: '/sample',
    name: 'sample-register',
    component: () => import('@/views/sample/index.vue'),
    title: '样品登记',
  },
  {
    path: '/item/decompose',
    name: 'item-decompose',
    component: () => import('@/views/item/index.vue'),
    title: '项目分解',
    permissions: ['item:decompose'],
  },
  {
    path: '/assign/index',
    name: 'assign-index',
    component: () => import('@/views/assign/index.vue'),
    title: '任务安排',
    permissions: ['assign:confirm'],
  },

  // ---------------- 实验室业务（七阶段主线 5~7） ----------------
  {
    path: '/result/entry',
    name: 'result-entry',
    component: () => import('@/views/result/index.vue'),
    title: '结果录入',
    permissions: ['result:entry'],
  },
  {
    path: '/result/my-tasks',
    name: 'result-my-tasks',
    component: () => import('@/views/result/my-tasks.vue'),
    title: '我的检验任务',
    permissions: ['result:entry'],
  },
  {
    path: '/report/audit',
    name: 'report-audit',
    component: () => import('@/views/report/audit.vue'),
    title: '报告审核',
    permissions: ['report:audit', 'report:sign'],
  },
  {
    path: '/report/generate',
    name: 'report-generate',
    component: () => import('@/views/report/generate.vue'),
    title: '报告生成',
    permissions: ['report:generate'],
  },

  // ---------------- 数据中心 ----------------
  {
    path: '/query/testing',
    name: 'query-testing',
    component: () => import('@/views/query/testing.vue'),
    title: '在检样品',
    permissions: ['query:testing'],
  },
  {
    path: '/query/history',
    name: 'query-history',
    component: () => import('@/views/query/history.vue'),
    title: '历史样品',
    permissions: ['query:history'],
  },
  {
    path: '/query/lib',
    name: 'query-library',
    component: () => import('@/views/query/library.vue'),
    title: '项目库查询',
    permissions: ['base:lib:list'],
  },
  {
    path: '/query/analysis',
    name: 'query-analysis',
    component: () => import('@/views/query/analysis.vue'),
    title: '质量分析',
    permissions: ['stat:view'],
  },

  // ---------------- 基础数据 ----------------
  {
    path: '/base/product-lib',
    name: 'base-product-lib',
    component: () => import('@/views/base/product-lib.vue'),
    title: '项目标准库',
    permissions: ['base:lib:list'],
  },
  {
    path: '/base/tester-method',
    name: 'base-tester-method',
    component: () => import('@/views/base/tester-method.vue'),
    title: '方法资质',
    permissions: ['base:tester-method:list'],
  },

  // ---------------- 数据导出 ----------------
  {
    path: '/export/province',
    name: 'export-province',
    component: () => import('@/views/export/province.vue'),
    title: '省平台上报',
    permissions: ['export:province'],
  },

  // ---------------- 系统管理 ----------------
  {
    path: '/sys/user',
    name: 'sys-user',
    component: () => import('@/views/system/user.vue'),
    title: '用户管理',
    permissions: ['sys:user:list'],
  },
  {
    path: '/sys/role',
    name: 'sys-role',
    component: () => import('@/views/system/role.vue'),
    title: '角色管理',
    permissions: ['sys:role:list'],
  },
  {
    path: '/sys/menu',
    name: 'sys-menu',
    component: () => import('@/views/system/menu.vue'),
    title: '菜单管理',
    permissions: ['sys:menu:list'],
  },
  {
    path: '/sys/dept',
    name: 'sys-dept',
    component: () => import('@/views/system/dept.vue'),
    title: '部门管理',
    permissions: ['sys:dept:list'],
  },

  // ---------------- 布局之外的独立页面 ----------------
  {
    path: '/report/print',
    name: 'report-print',
    component: () => import('@/views/report/print.vue'),
    title: '报告打印',
    permissions: ['report:generate', 'report:print'],
    // 报告是白底 A4 纸质文档：独立于外壳，让「打印」直接得到干净纸张，
    // 否则要在打印 CSS 里反向隐藏侧栏/顶栏，脆弱且易漏（T-702 决策）。
    standalone: true,
    navVisible: false,
  },
]

/** path → RouteEntry 的索引，供 O(1) 查询 */
export const ROUTE_BY_PATH: ReadonlyMap<string, RouteEntry> = new Map(
  ROUTE_REGISTRY.map((e) => [e.path, e]),
)

/**
 * 后端菜单 path → 前端规范 path 的别名表。
 *
 * 起因（2026-09-13 实测）：`sys_menu` 中的 path 是权限种子时期写入的，
 * 与前端实际路由存在历史差异。用户明确要求「不为动态路由大规模修改数据库」，
 * 故在前端做一次规范化转换，而不是去改数据。
 *
 * 已发现的差异（实测 SQL 结果，非推测）：
 *   /sample/register  →  /sample          前端路由不含 register 段
 *   /assign           →  /assign/index    前端保留了 index 段（历史命名）
 *   /base/tester-method 与 /base/tester-method 一致（无需转换）
 *
 * ⚠️ 维护约定：若后端为某路径补了真实页面，**优先改 DB 的 path 使其规范化**，
 * 而不是往这里继续加别名——别名表是兼容层，不是常态。
 */
export const PATH_ALIAS: Readonly<Record<string, string>> = {
  '/sample/register': '/sample',
  '/assign': '/assign/index',
}

/**
 * 把后端菜单 path 规范化为前端路由 path。
 *
 * @param rawPath 后端菜单树中的 path（可能为空——目录节点通常无 path）
 * @returns 规范化后的路径；无法识别时返回 `null`（调用方须 fail-loud，不得静默跳过）
 */
export function normalizeMenuPath(rawPath: string | undefined | null): string | null {
  if (!rawPath) return null
  // 去掉可能的查询串/尾部斜杠，避免 /sample/ 与 /sample 被当成两个路由
  const cleaned = rawPath.split('?')[0].replace(/\/+$/, '') || '/'
  const canonical = PATH_ALIAS[cleaned] ?? cleaned
  return ROUTE_BY_PATH.has(canonical) ? canonical : null
}
