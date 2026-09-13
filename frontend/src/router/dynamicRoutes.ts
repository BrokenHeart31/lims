/**
 * 动态路由构建器 —— 把 `/api/auth/me` 的菜单树转换为 Vue Router 路由
 * =============================================================================
 * 职责边界（重要）：
 *   本模块只负责「菜单树 → 路由/侧栏数据的转换与规范化」，
 *   不负责「何时调用」「如何注册」（那是 router/index.ts 与 stores/auth.ts 的事）。
 *   保持纯函数风格，便于单测与复用（侧栏与路由共用同一份输出，杜绝两套配置漂移）。
 *
 * 设计要点
 * -----------------------------------------------------------------------------
 * 1. **fail-loud，不静默吞**：菜单树里出现注册表不认识的 path 时，
 *    必须在控制台输出明确警告（含 path 与 title），而不是悄悄跳过。
 *    静默跳过会导致「菜单点了没反应」这种极难排查的问题。
 *
 * 2. **权限过滤在这儿做，不在后端**：后端返回的是「我这个角色能看的菜单」，
 *    但同一角色在不同页面可能还要按按钮权限做二次判断。这里的过滤规则是
 *    「路由级 permissions 任一命中即可见」，与 router 守卫使用的判断保持一致。
 *
 * 3. **菜单树与路由树同源**：`buildRoutesFromMenus` 同时产出「路由数组」与
 *    「侧栏菜单树」。若两者分开生成，早晚会出现「有菜单无路由」或反之的漂移。
 *
 * 4. **空目录要剪枝**：目录节点的所有子节点都无页面时，该目录不应出现在侧栏
 *    （否则点开是空分组，体感像 bug）。这是导航可用性的硬要求。
 * =============================================================================
 */
import type { RouteRecordRaw } from 'vue-router'
import type { MenuNode } from '@/api/auth'
import {
  ROUTE_BY_PATH,
  normalizeMenuPath,
  type RouteEntry,
} from './routeRegistry'

/** 侧栏/面包屑共用的菜单节点（已规范化，叶子节点必有可用路由） */
export interface MenuTreeNode {
  /** 后端菜单 id（用于 :key，避免标题重复时 key 冲突） */
  id: number
  title: string
  /** 规范化后的前端路由 path；目录节点为 undefined */
  path?: string
  /** Element Plus 图标组件名（如 'Monitor'）；后端 icon 字段原样透传 */
  icon?: string
  children: MenuTreeNode[]
}

/** 构建结果：路由与菜单树一次产出，保证两者严格同源 */
export interface BuiltNavigation {
  /** 需要注册到 MainLayout children 下的路由 */
  routes: RouteRecordRaw[]
  /** 外壳之外的独立路由（如报告打印页） */
  standaloneRoutes: RouteRecordRaw[]
  /** 侧栏菜单树（已剪枝） */
  menuTree: MenuTreeNode[]
  /** 本次构建中无法识别的菜单 path（供调用方日志/断言） */
  unresolved: { path: string; title: string }[]
}

/** RouteEntry.component 转成 RouteRecordRaw 可直接接受的组件类型 */
type RouteComponent = NonNullable<RouteRecordRaw['component']>

/** 把 RouteEntry 转成 vue-router 的 RouteRecordRaw（去掉前导 /，因为要挂在 layout children 下） */
function toRouteRecord(entry: RouteEntry, underLayout: boolean): RouteRecordRaw {
  const path = underLayout ? entry.path.replace(/^\//, '') : entry.path
  return {
    path,
    name: entry.name,
    component: entry.component as RouteComponent,
    meta: {
      title: entry.title,
      permissions: entry.permissions,
    },
  } as RouteRecordRaw
}

/**
 * 判断当前用户是否可访问某路由条目。
 *
 * 规则与 router 守卫中的 checksAccess 保持一致（单一判断口径）：
 *   · 未声明 permissions → 视为「登录即可访问」
 *   · 声明了 permissions → 任一命中即可
 *
 * 注意：特权角色（R100）由 authStore.permissions 在**后端**扩容为全量权限，
 * 前端这里不需要也不应该硬编码角色判断——否则角色调整要改两处。
 */
function canAccess(entry: RouteEntry, permissions: Set<string>): boolean {
  if (!entry.permissions || entry.permissions.length === 0) return true
  return entry.permissions.some((p) => permissions.has(p))
}

/**
 * 递归构建菜单树，并对无法识别的节点 fail-loud。
 *
 * 语义说明：
 *   · 目录节点（无 path 或 path 指向注册表之外的目录路径）：仅作分组容器，
 *     其可见性完全取决于「剪枝后是否还有子节点」。
 *   · 叶子节点（path 命中注册表）：生成路由 + 菜单项；若无权限则整枝丢弃。
 */
function buildNode(
  node: MenuNode,
  permissions: Set<string>,
  routes: RouteRecordRaw[],
  standaloneRoutes: RouteRecordRaw[],
  unresolved: { path: string; title: string }[],
): MenuTreeNode | null {
  const children: MenuTreeNode[] = []

  for (const child of node.children ?? []) {
    const built = buildNode(child, permissions, routes, standaloneRoutes, unresolved)
    if (built) children.push(built)
  }

  // --- 情况 A：这是一个叶子（或伪叶子）节点，尝试把它当成可导航页面 ---
  const canonical = normalizeMenuPath(node.path)

  if (canonical) {
    const entry = ROUTE_BY_PATH.get(canonical)!

    // 无权限 → 整枝丢弃（连带其子节点；生产上叶子不应有子节点，这里做防御）
    if (!canAccess(entry, permissions)) return null

    // 只登记一次路由，避免同一 path 被多份菜单（如历史重复数据）重复注册，
    // 那会让 vue-router 抛 "duplicate named routes" 警告。
    const already = entry.standalone
      ? standaloneRoutes.some((r) => r.name === entry.name)
      : routes.some((r) => r.name === entry.name)
    if (!already) {
      if (entry.standalone) {
        standaloneRoutes.push(toRouteRecord(entry, false))
      } else if (children.length === 0) {
        // 有子菜单的叶子页仍要注册路由，但侧栏以子节点为主
        routes.push(toRouteRecord(entry, true))
      } else {
        routes.push(toRouteRecord(entry, true))
      }
    }

    // 有子节点时：该节点是「可点击的目录」，自身也保留为菜单项（前端会渲染成可展开项）
    if (children.length > 0) {
      return {
        id: node.id,
        title: node.title,
        path: entry.navVisible === false ? undefined : canonical,
        icon: entry.standalone ? undefined : node.icon,
        children,
      }
    }

    // 纯叶子
    return {
      id: node.id,
      title: node.title,
      path: canonical,
      icon: node.icon,
      children: [],
    }
  }

  // --- 情况 B：path 为空或不在注册表 —— 视为目录容器 ---
  if (children.length > 0) {
    return {
      id: node.id,
      title: node.title,
      // 目录自身不跳转；path 留空，侧栏渲染为不可点击的分组标题或纯展开项
      path: undefined,
      icon: node.icon,
      children,
    }
  }

  // --- 情况 C：既不是有效页面，剪枝后也没有子节点 → 该菜单无对应前端页面 ---
  // 这类节点必须报出来。典型来源：后端提前建好了菜单，但前端页面还没做。
  if (node.path) {
    unresolved.push({ path: node.path, title: node.title })
  }
  return null
}

/**
 * 从 `/me` 返回的菜单树构建路由与侧栏数据。
 *
 * @param menus       后端菜单树（仅含目录/菜单，按钮不在其中）
 * @param permissions 当前用户权限标识全集
 */
export function buildNavigation(menus: MenuNode[], permissions: string[]): BuiltNavigation {
  const permissionSet = new Set(permissions)
  const routes: RouteRecordRaw[] = []
  const standaloneRoutes: RouteRecordRaw[] = []
  const unresolved: { path: string; title: string }[] = []
  const menuTree: MenuTreeNode[] = []

  for (const node of menus) {
    const built = buildNode(node, permissionSet, routes, standaloneRoutes, unresolved)
    if (built) menuTree.push(built)
  }

  // fail-loud：不静默吞掉无法识别的菜单，便于第一时间发现「菜单有、页面无」
  if (unresolved.length > 0) {
    console.warn(
      '[dynamicRoutes] 以下菜单在路由注册表中没有对应页面，已在侧栏忽略：\n' +
        unresolved.map((u) => `  · ${u.title}（${u.path}）`).join('\n') +
        '\n如需展示，请在 src/router/routeRegistry.ts 中登记；' +
        '若页面尚未开发，请把该菜单设为 visible=0。',
    )
  }

  return { routes, standaloneRoutes, menuTree, unresolved }
}
