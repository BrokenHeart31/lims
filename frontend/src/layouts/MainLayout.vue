<script setup lang="ts">
/**
 * MainLayout — 应用外壳（动态路由版，2026-09-13 GLM）
 * ----------------------------------------------------------------------------
 * 结构：
 *   Sidebar (224px / collapse 68px)  +  Main(Header 60px + Breadcrumb + <router-view>)
 * 设计：
 *   - 侧栏菜单由 `/api/auth/me` 菜单树驱动（authStore.navMenus），与动态路由同源；
 *     不再使用本地静态分组——静态菜单与动态路由并存必然漂移。
 *   - 顶部 Header：面包屑 + 全局搜索 ⌘K + 通知 + 帮助 + 用户菜单 → 提示词 §八
 *   - 极光玻璃品牌区（保留 Aurora Glass 华丽质感）
 */
import { computed, markRaw, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  Bell,
  Coin,
  DataAnalysis,
  Document,
  Download,
  EditPen,
  Expand,
  Files,
  Fold,
  Help,
  Histogram,
  List,
  Lock,
  Medal,
  Menu,
  Monitor,
  Notebook,
  OfficeBuilding,
  Operation,
  Search,
  SwitchButton,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { changePasswordApi } from '@/api/auth'
import { pagePendingAuditApi, pagePendingSignApi, pageReportPendingApi } from '@/api/report'
import { pagePendingResultApi } from '@/api/result'
import { pagePendingItemApi } from '@/api/item'
import { pagePendingAssignApi } from '@/api/assign'
import { pageOperationLogApi, type OperationLogRow } from '@/api/system'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import { confirm } from '@/utils/confirm'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/**
 * 后端图标名 → Element Plus 组件。
 *
 * 为什么用「白名单映射」而不是 `(ElIcons as any)[name]` 动态取？
 *   · 动态取会把整个图标库（400+）拉进产物，且 Vite 无法 tree-shake；
 *   · 后端 icon 是自由文本，拼错的名字动态取会渲染空白且无任何提示。
 * 白名单 + 兜底图标让「不认识的图标」退化为通用图标，界面不会出现空洞。
 * 本项目 sys_menu 实际用到的图标名已全部覆盖（见 db/seed 的 icon 列）。
 */
const ICON_MAP: Record<string, Component> = {
  Monitor: markRaw(Monitor),
  List: markRaw(List),
  Document: markRaw(Document),
  Files: markRaw(Files),
  User: markRaw(User),
  EditPen: markRaw(EditPen),
  Notebook: markRaw(Notebook),
  Search: markRaw(Search),
  Coin: markRaw(Coin),
  Setting: markRaw(Operation),
  Upload: markRaw(Download),
  Download: markRaw(Download),
  Tickets: markRaw(Tickets),
  Histogram: markRaw(Histogram),
  TrendCharts: markRaw(TrendCharts),
  DataAnalysis: markRaw(DataAnalysis),
  Medal: markRaw(Medal),
  OfficeBuilding: markRaw(OfficeBuilding),
  Operation: markRaw(Operation),
  UserFilled: markRaw(UserFilled),
  Menu: markRaw(Menu),
}
/** 兜底图标：后端新增了未登记图标时用它，避免渲染空洞 */
const FALLBACK_ICON = markRaw(Files)

function resolveIcon(name?: string): Component {
  if (name && ICON_MAP[name]) return ICON_MAP[name]
  return FALLBACK_ICON
}

/** 侧栏折叠 */
const collapsed = ref(false)

/** 侧栏菜单树：来自 /me，与动态路由同源（由 router 注册时回填，杜绝两套配置漂移） */
const navMenus = computed(() => authStore.navMenus)

/** 展开的目录 id 集合（LIMS 菜单仅两级，默认全部展开比反复点开更省事） */
const expandedIds = ref<Set<number>>(new Set())
watch(
  navMenus,
  (tree) => {
    const next = new Set<number>()
    for (const node of tree) {
      if (node.children.length > 0) next.add(node.id)
    }
    expandedIds.value = next
  },
  { immediate: true },
)

function toggleExpand(id: number): void {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

const displayName = computed(
  () => authStore.userInfo?.nickname ?? authStore.userInfo?.username ?? '未登录',
)

const avatarText = computed(() => displayName.value.trim().charAt(0).toUpperCase() || 'L')

function isActive(path?: string): boolean {
  if (!path) return false
  return route.path === path || route.path.startsWith(`${path}/`)
}

async function go(path?: string): Promise<void> {
  if (!path) return
  if (route.path !== path) await router.push(path)
}

/**
 * 面包屑：按当前路由在菜单树中的位置生成。
 *
 * 规则（修 2026-09-13 豆包巡检 B6）：
 *   · 首项「LIMS」→ 可点击回工作台；
 *   · 在菜单树中找当前页的顶层祖先（分组）；若当前页本身就是顶层叶子
 *     （如工作台），不再重复加入分组名（避免「工作台/工作台」重复）；
 *   · 分组项若自身有 path 则可点击回该页，否则纯展示；
 *   · 末项（当前页）不可点击。
 */
const breadcrumbItems = computed(() => {
  const title = (route.meta?.title as string | undefined) ?? ''
  const items: { title: string; to?: string }[] = [{ title: 'LIMS', to: '/dashboard' }]
  for (const group of navMenus.value) {
    const isSelf = group.path === route.path
    const isChild = group.children.some((c) => c.path === route.path)
    if (isSelf) {
      // 当前页本身就是顶层叶子：只加一次，不重复分组名
      break
    }
    if (isChild) {
      items.push({ title: group.title })
      break
    }
  }
  if (title) items.push({ title })
  if (items.length === 1) items.push({ title: '首页', to: '/dashboard' })
  return items
})

/** 顶部全局搜索：仅在已登记的导航页中检索（不编造业务数据） */
const searchKeyword = ref('')
const searchDialogOpen = ref(false)

interface SearchHit {
  title: string
  path: string
  group: string
}

/** 把菜单树摊平成可搜索列表 */
const searchablePages = computed<SearchHit[]>(() => {
  const out: SearchHit[] = []
  for (const g of navMenus.value) {
    if (g.path) out.push({ title: g.title, path: g.path, group: g.title })
    for (const c of g.children) {
      if (c.path) out.push({ title: c.title, path: c.path, group: g.title })
    }
  }
  return out
})

const searchHits = computed<SearchHit[]>(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return searchablePages.value
  return searchablePages.value.filter(
    (p) => p.title.toLowerCase().includes(kw) || p.path.toLowerCase().includes(kw),
  )
})

function handleSearchKey(e: KeyboardEvent): void {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchDialogOpen.value = true
  }
}
if (typeof window !== 'undefined') window.addEventListener('keydown', handleSearchKey)

async function gotoSearchHit(hit: SearchHit): Promise<void> {
  searchDialogOpen.value = false
  searchKeyword.value = ''
  await go(hit.path)
}

/**
 * 待办提醒（2026-09-14 由「静态示例通知」改写为真实数据）
 * ----------------------------------------------------------------------------
 * 改写原因：原实现是 4 条写死的假通知（「3 份报告待审核」「样品 JK-2026-001 铅超标」
 * 「本周任务完成 78%」），属于 DECISIONS「禁 mock 假数据」原则的明确违反——
 * 假数据比没有数据更危险：用户会照着它去点、去查，然后发现系统里根本不存在这件事。
 *
 * 现改为**汇总各业务域真实的待办数量**：数据全部来自既有分页接口的 `total`，
 * 不新增任何后端字段、不编造任何数值。
 *
 * 三点设计约束：
 *   ① **权限容错**：每个来源用各自的 Promise.allSettled 项包裹，403/网络异常直接跳过，
 *      不能让「某个模块没权限」把整个待办面板打空；
 *   ② **零值不展示**：count=0 的条目直接不出现（而不是显示「0 份待审核」）；
 *   ③ **不做「已读」**：待办是派生数据（样品状态变了它就该变），
 *      存「已读」只会让用户看到与事实不符的角标。
 */
interface TodoItem {
  key: string
  /** 待办文案（不含数量，数量单独渲染为角标样式的数字） */
  label: string
  count: number
  /** 点击跳转的业务页面 */
  path: string
  tone: 'audit' | 'sign' | 'result' | 'item' | 'assign' | 'report'
}

interface TodoSource {
  key: string
  label: string
  path: string
  tone: TodoItem['tone']
  /**
   * 取该项待办所需权限。
   *
   * ⚠️ 必须在**发请求前**判断：待办面板在挂载时就会拉数据，
   * 若不做权限过滤，无权限的账号会在控制台留下成串的 `403 Forbidden`,
   * 用户看到的就是「没权限还报错」（2026-09-14 实测：R3 登录后 15 条 403）。
   * 权限是前端已知信息（`/me` 已返回），没有任何理由先请求再吃 403。
   */
  permission: string
  /** 取该项待办数量（复用既有分页接口，size=1 只取 total） */
  load: () => Promise<number>
}

const todoSources: TodoSource[] = [
  {
    key: 'audit',
    label: '份报告待审核',
    path: '/report/audit',
    tone: 'audit',
    permission: 'report:audit',
    load: async () => (await pagePendingAuditApi({ current: 1, size: 1 })).total,
  },
  {
    key: 'sign',
    label: '份报告待签发',
    path: '/report/audit',
    tone: 'sign',
    permission: 'report:sign',
    load: async () => (await pagePendingSignApi({ current: 1, size: 1 })).total,
  },
  {
    key: 'result',
    label: '个样品待录入结果',
    path: '/result/entry',
    tone: 'result',
    permission: 'result:entry',
    load: async () => (await pagePendingResultApi({ current: 1, size: 1 })).total,
  },
  {
    key: 'item',
    label: '个样品待分解项目',
    path: '/item/decompose',
    tone: 'item',
    permission: 'item:decompose',
    load: async () => (await pagePendingItemApi({ current: 1, size: 1 })).total,
  },
  {
    key: 'assign',
    label: '个样品待安排检验员',
    path: '/assign/index',
    tone: 'assign',
    permission: 'assign:confirm',
    load: async () => (await pagePendingAssignApi({ current: 1, size: 1 })).total,
  },
  {
    key: 'report',
    label: '个样品可生成报告',
    path: '/report/generate',
    tone: 'report',
    permission: 'report:generate',
    load: async () => (await pageReportPendingApi({ current: 1, size: 1 })).total,
  },
]

const todos = ref<TodoItem[]>([])
const todoLoading = ref(false)
/** 是否已完成首次加载（用于区分「加载中」与「确实没有待办」） */
const todoLoaded = ref(false)

async function loadTodos(): Promise<void> {
  // 只对**有权限**的待办源发请求，从源头消除无意义的 403
  const allowed = todoSources.filter((s) => authStore.hasPermission(s.permission))
  if (allowed.length === 0) {
    todos.value = []
    todoLoaded.value = true
    return
  }
  todoLoading.value = true
  try {
    const results = await Promise.allSettled(allowed.map((s) => s.load()))
    todos.value = allowed
      .map((source, index) => {
        const settled = results[index]
        const count = settled.status === 'fulfilled' ? Number(settled.value) || 0 : 0
        return { source, count }
      })
      .filter((item) => item.count > 0)
      .map((item) => ({
        key: item.source.key,
        label: item.source.label,
        count: item.count,
        path: item.source.path,
        tone: item.source.tone,
      }))
    todoLoaded.value = true
  } finally {
    todoLoading.value = false
  }
}

/** 角标数字：全部待办条目计数之和（>99 显示 99+） */
const todoBadge = computed(() => todos.value.reduce((sum, item) => sum + item.count, 0))

async function gotoTodo(item: TodoItem): Promise<void> {
  await router.push(item.path)
}

onMounted(() => {
  void loadTodos()
})

/** 用户菜单：个人资料 / 修改密码 / 操作日志 / 退出 */
const profileOpen = ref(false)
const passwordOpen = ref(false)
const logOpen = ref(false)
const helpOpen = ref(false)

/**
 * 操作日志（2026-09-14 接入后端）
 * ----------------------------------------------------------------------------
 * 原实现是「诚实的空壳」——对话框里写「待后端接入」。本轮把后端补齐
 * （`sys_operation_log` + `OperationLogInterceptor` + `GET /api/sys/log/page`），
 * 前端改为真实分页表格。
 *
 * 数据范围由后端决定：有 `log:view` 权限可看全部，否则只能看到自己的记录。
 */
const logRows = ref<OperationLogRow[]>([])
const logLoading = ref(false)
const logTotal = ref(0)
const logQuery = ref({ current: 1, size: 10 })

async function loadOperationLog(): Promise<void> {
  logLoading.value = true
  try {
    const page = await pageOperationLogApi({
      current: logQuery.value.current,
      size: logQuery.value.size,
    })
    logRows.value = page.records ?? []
    logTotal.value = page.total ?? 0
  } catch {
    // request 拦截器已提示；保持表格空态，不伪造数据
    logRows.value = []
    logTotal.value = 0
  } finally {
    logLoading.value = false
  }
}

function handleLogPageChange(page: number): void {
  logQuery.value.current = page
  void loadOperationLog()
}

function openOperationLog(): void {
  logQuery.value.current = 1
  logOpen.value = true
  void loadOperationLog()
}

async function handleLogout(): Promise<void> {
  const ok = await confirm({
    title: '退出登录',
    message: '确定要退出当前账号吗？',
    tone: 'warning',
    confirmText: '退出',
  })
  if (!ok) return
  authStore.logout()
  await router.push({ name: 'login' })
}

function handleUserCommand(command: string): void {
  switch (command) {
    case 'profile':
      profileOpen.value = true
      break
    case 'password':
      passwordForm.value.oldPassword = ''
      passwordForm.value.newPassword = ''
      passwordForm.value.confirmPassword = ''
      passwordOpen.value = true
      break
    case 'log':
      openOperationLog()
      break
    case 'logout':
      void handleLogout()
      break
  }
}

/** 修改密码表单 */
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordLoading = ref(false)
async function submitPassword(): Promise<void> {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordLoading.value = true
  try {
    await changePasswordApi({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordOpen.value = false
    setTimeout(() => void handleLogout(), 800)
  } catch {
    /* request 拦截器已提示 */
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <div
    class="lims-shell"
    :class="{ 'is-collapsed': collapsed }"
  >
    <!-- ================= 侧栏（STEP 3：实色 Sidebar 层，不再玻璃化） ================= -->
    <aside class="shell-aside">
      <!-- 品牌区 -->
      <div class="brand">
        <span
          class="brand-mark"
          aria-hidden="true"
        />
        <span
          v-show="!collapsed"
          class="brand-text lims-text-gradient"
        >LIMS 实验室</span>
      </div>

      <!-- 分组导航：由 /me 菜单树驱动（与动态路由同源） -->
      <nav class="nav">
        <div
          v-for="group in navMenus"
          :key="group.id"
          class="nav-group"
        >
          <!-- 目录节点：有子菜单时渲染为可展开的分组标题；无子菜单时作为普通项 -->
          <div
            v-if="group.children.length > 0"
            class="nav-sub"
          >
            <button
              type="button"
              class="nav-group__title nav-group__title--btn"
              :class="{ 'is-expanded': expandedIds.has(group.id) }"
              :title="collapsed ? group.title : undefined"
              @click="toggleExpand(group.id)"
            >
              <el-icon
                v-if="collapsed"
                class="nav-icon"
                :size="18"
              >
                <component :is="resolveIcon(group.icon)" />
              </el-icon>
              <span v-show="!collapsed">{{ group.title }}</span>
              <el-icon
                v-show="!collapsed"
                class="nav-caret"
                :size="12"
              >
                <component :is="expandedIds.has(group.id) ? ArrowDown : ArrowRight" />
              </el-icon>
            </button>
            <div
              v-show="!collapsed && expandedIds.has(group.id)"
              class="nav-children"
            >
              <button
                v-for="child in group.children"
                :key="child.id"
                type="button"
                class="nav-item nav-item--child"
                :class="{ 'is-active': isActive(child.path) }"
                @click="go(child.path)"
              >
                <span class="nav-dot" />
                <span class="nav-text">{{ child.title }}</span>
              </button>
            </div>
          </div>

          <!-- 叶子节点（顶层直接是页面） -->
          <button
            v-else
            type="button"
            class="nav-item"
            :class="{ 'is-active': isActive(group.path) }"
            :title="collapsed ? group.title : undefined"
            @click="go(group.path)"
          >
            <el-icon
              class="nav-icon"
              :size="18"
            >
              <component :is="resolveIcon(group.icon)" />
            </el-icon>
            <span
              v-show="!collapsed"
              class="nav-text"
            >{{ group.title }}</span>
          </button>
        </div>
      </nav>

      <!-- 折叠按钮 -->
      <div class="aside-foot">
        <button
          type="button"
          class="nav-item nav-item--ghost"
          :title="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="collapsed = !collapsed"
        >
          <el-icon
            class="nav-icon"
            :size="18"
          >
            <component :is="collapsed ? Expand : Fold" />
          </el-icon>
          <span
            v-show="!collapsed"
            class="nav-text"
          >收起侧栏</span>
        </button>
      </div>
    </aside>

    <!-- ================= 主区 ================= -->
    <div class="shell-body">
      <!-- 顶部 Header：搜索 / 通知 / 帮助 / 用户菜单 -->
      <header class="shell-header">
        <div class="header-left">
          <AppBreadcrumb :items="breadcrumbItems" />
        </div>

        <div class="header-right">
          <!-- 全局搜索（点击/⌘K 打开） -->
          <button
            type="button"
            class="search-trigger"
            :title="`全局搜索（⌘K）`"
            @click="searchDialogOpen = true"
          >
            <el-icon :size="14">
              <Search />
            </el-icon>
            <span class="search-trigger__placeholder">搜索样品 / 任务 / 报告…</span>
            <span class="search-trigger__kbd">⌘K</span>
          </button>

          <!-- 待办提醒：数量全部来自真实业务接口（不再有静态假通知） -->
          <el-popover
            :width="330"
            trigger="click"
            placement="bottom-end"
            popper-class="lims-popover"
            @show="loadTodos"
          >
            <template #reference>
              <button
                type="button"
                class="icon-btn"
                title="待办提醒"
              >
                <el-icon :size="18">
                  <Bell />
                </el-icon>
                <span
                  v-if="todoBadge > 0"
                  class="icon-btn__badge"
                >{{ todoBadge > 99 ? '99+' : todoBadge }}</span>
              </button>
            </template>
            <div
              v-loading="todoLoading"
              class="notif"
            >
              <div class="notif__head">
                <span class="notif__title">待办提醒</span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="loadTodos"
                >
                  刷新
                </el-button>
              </div>
              <ul
                v-if="todos.length > 0"
                class="notif__list"
              >
                <li
                  v-for="todo in todos"
                  :key="todo.key"
                  class="notif__item"
                  @click="gotoTodo(todo)"
                >
                  <span
                    class="notif__dot"
                    :class="`is-${todo.tone}`"
                    aria-hidden="true"
                  />
                  <div class="notif__body">
                    <p class="notif__msg">
                      <b class="notif__count">{{ todo.count }}</b> {{ todo.label }}
                    </p>
                    <span class="notif__time">点击前往处理</span>
                  </div>
                </li>
              </ul>
              <el-empty
                v-else-if="todoLoaded && !todoLoading"
                :image-size="56"
                description="暂无待办事项"
              />
            </div>
          </el-popover>

          <!-- 帮助 -->
          <button
            type="button"
            class="icon-btn"
            title="帮助"
            @click="helpOpen = true"
          >
            <el-icon :size="18">
              <Help />
            </el-icon>
          </button>

          <!-- 用户菜单 -->
          <el-dropdown
            trigger="click"
            @command="handleUserCommand"
          >
            <button
              type="button"
              class="user-chip"
            >
              <span class="user-avatar">{{ avatarText }}</span>
              <span class="user-meta">
                <span class="user-name">{{ displayName }}</span>
                <span class="user-role">{{ authStore.userInfo?.deptName ?? '实验室' }}</span>
              </span>
              <el-icon :size="12">
                <ArrowDown />
              </el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人资料
                </el-dropdown-item>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item command="log">
                  <el-icon><Histogram /></el-icon>
                  操作日志
                </el-dropdown-item>
                <el-dropdown-item
                  command="logout"
                  divided
                >
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 主区：面包屑已上移至 Header；这里直接渲染路由视图 -->
      <main class="shell-main">
        <router-view v-slot="{ Component: RouteComponent }">
          <transition
            name="lims-fade"
            mode="out-in"
          >
            <component :is="RouteComponent" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 全局搜索弹窗：在已登记导航页中检索并跳转（不做假数据业务搜索） -->
    <el-dialog
      v-model="searchDialogOpen"
      width="640px"
      :show-close="false"
      :modal-class="'lims-search-modal'"
      align-center
      custom-class="lims-search-dialog"
    >
      <div class="search-dialog">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索页面（如：报告审核、质量分析）…"
          size="large"
          :prefix-icon="Search"
          autofocus
        />
        <ul class="search-dialog__hint">
          <li><kbd>Esc</kbd> 关闭</li>
          <li class="search-dialog__hint--right">
            ⌘K 全局唤起
          </li>
        </ul>
        <ul
          v-if="searchHits.length > 0"
          class="search-dialog__list"
        >
          <li
            v-for="hit in searchHits"
            :key="hit.path"
            class="search-dialog__item"
            @click="gotoSearchHit(hit)"
          >
            <span class="search-dialog__item-title">{{ hit.title }}</span>
            <span class="search-dialog__item-path">{{ hit.path }}</span>
          </li>
        </ul>
        <p
          v-else
          class="search-dialog__placeholder"
        >
          没有匹配的页面。跨业务对象（样品 / 任务 / 报告 / 用户）的全文检索需后端提供接口后接入。
        </p>
      </div>
    </el-dialog>

    <!-- 个人资料 -->
    <el-dialog
      v-model="profileOpen"
      width="480px"
      title="个人资料"
      align-center
    >
      <div class="profile-grid">
        <div class="profile-row">
          <span class="profile-label">用户名</span><span>{{ authStore.userInfo?.username }}</span>
        </div>
        <div class="profile-row">
          <span class="profile-label">姓名</span><span>{{ authStore.userInfo?.nickname }}</span>
        </div>
        <div class="profile-row">
          <span class="profile-label">部门</span><span>{{ authStore.userInfo?.deptName ?? '—' }}</span>
        </div>
        <div class="profile-row">
          <span class="profile-label">角色</span><span>{{ (authStore.userInfo?.roles ?? []).join('、') || '—' }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="profileOpen = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <!-- 修改密码 -->
    <el-dialog
      v-model="passwordOpen"
      width="440px"
      title="修改密码"
      align-center
    >
      <el-form
        label-width="80px"
        @submit.prevent
      >
        <el-form-item label="旧密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            placeholder="请输入旧密码"
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            placeholder="6~32 位"
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
            @keyup.enter="submitPassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordOpen = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="passwordLoading"
          @click="submitPassword"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 操作日志（2026-09-14 接入后端：sys_operation_log + 拦截器自动写入） -->
    <el-dialog
      v-model="logOpen"
      width="860px"
      title="操作日志"
      align-center
    >
      <el-table
        v-loading="logLoading"
        :data="logRows"
        size="small"
        max-height="420"
        empty-text="暂无操作记录"
      >
        <el-table-column
          prop="createdAt"
          label="时间"
          width="160"
        />
        <el-table-column
          label="操作人"
          width="110"
        >
          <template #default="{ row }">
            {{ row.operatorName || row.operator }}
          </template>
        </el-table-column>
        <el-table-column
          prop="summary"
          label="操作"
          min-width="260"
          show-overflow-tooltip
        />
        <el-table-column
          label="结果"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.result === 1 ? 'success' : 'danger'"
              size="small"
            >
              {{ row.resultLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="耗时"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ row.durationMs }} ms
          </template>
        </el-table-column>
      </el-table>
      <div class="log-foot">
        <span class="log-foot__hint">仅记录写操作（新增 / 修改 / 删除 / 导入 / 确认 / 审核 / 签发等）</span>
        <el-pagination
          v-if="logTotal > 0"
          layout="prev, pager, next, total"
          :total="logTotal"
          :current-page="logQuery.current"
          :page-size="logQuery.size"
          @current-change="handleLogPageChange"
        />
      </div>
      <template #footer>
        <el-button @click="logOpen = false">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <!-- 帮助中心 -->
    <el-dialog
      v-model="helpOpen"
      width="600px"
      title="帮助中心"
      align-center
    >
      <div class="help-body">
        <h4>快捷操作</h4>
        <ul>
          <li>按 <kbd>Ctrl/⌘ + K</kbd> 打开全局页面搜索</li>
          <li>左侧栏可折叠，节省录入空间</li>
          <li>表格列支持点击表头排序；长文本悬浮可见全文</li>
        </ul>
        <h4>业务流程</h4>
        <ol>
          <li>样品登记：下载模板 → 导入采样单 → 确认登记</li>
          <li>项目分解：自动套库 → 预览 → 确认保存</li>
          <li>任务安排：自动分配检验员 → 确认</li>
          <li>结果录入：录入检验值 → 自动判定 → 提交审核</li>
          <li>报告审核签发：审核 → 退回/通过 → 签发 → 生成打印</li>
        </ol>
        <h4>常见问题</h4>
        <ul>
          <li>找不到某菜单？确认当前账号是否有对应权限</li>
          <li>数据看不到？联系管理员确认部门数据范围</li>
          <li>报告打印请用「报告生成」页的打印按钮进入独立打印视图</li>
        </ul>
      </div>
      <template #footer>
        <el-button @click="helpOpen = false">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ===========================================================================
 * 布局骨架（STEP 3 重构，T-917-3）
 * ---------------------------------------------------------------------------
 * 规范 §三十八 要求的结构是**三区贴合**：
 *
 *   ┌──────────────────────────────┐
 *   │ Header                       │
 *   ├───────────┬──────────────────┤
 *   │           │ Page Header      │
 *   │ Sidebar   │  KPI / Content   │
 *   └───────────┴──────────────────┘
 *
 * ⚠️ 原实现是「浮动玻璃壳」：.lims-shell 有 12px padding + gap，侧栏与顶栏
 *    各是一块带圆角的玻璃板，浮在页面底上。这与规范冲突（§六 要求 Sidebar
 *    224 / Header 64 的标准后台框架；§三十八 的示意图是贴边分区）。
 *    现改为：**贴边 + 实色层级**（Sidebar #0D1218 / Header #10161D /
 *    Content 底 #080C10），用「底色差异 + hair 分隔线」表达分区，
 *    不再用 padding + 圆角 + 阴影。
 * =========================================================================== */
.lims-shell {
  display: flex;
  height: 100%;
  overflow: hidden;
  /* 页面底：内容区透出，作为最底层 */
  background: var(--lims-layer-page);
}

/* ---------- 侧栏：贴边，实色 Sidebar 层 ---------- */
.shell-aside {
  display: flex;
  flex: 0 0 var(--lims-sidebar-w);
  flex-direction: column;
  width: var(--lims-sidebar-w);
  padding: var(--lims-sp-3);
  background: var(--lims-layer-sidebar);
  border-right: 1px solid var(--lims-hair);
  overflow: hidden;
  transition:
    flex-basis var(--lims-dur) var(--lims-ease-out),
    width var(--lims-dur) var(--lims-ease-out);
}

.lims-shell.is-collapsed .shell-aside {
  flex-basis: var(--lims-sidebar-w-collapsed);
  width: var(--lims-sidebar-w-collapsed);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--lims-sp-2);
  height: 44px;
  padding: 0 var(--lims-sp-2);
  margin-bottom: var(--lims-sp-4);
  border-radius: var(--lims-r-ctrl);
  white-space: nowrap;
}

.brand-mark {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--lims-brand-gradient);
  /* STEP 3：光晕由 18px/0.35 降至 12px/0.22（规范 §三十五 禁「大量发光」） */
  box-shadow: 0 0 12px rgba(var(--lims-accent-rgb), 0.22);
}

.brand-text {
  font-size: var(--lims-fs-lg);
  font-weight: 700;
  letter-spacing: 0.4px;
}

/* ---------- 分组导航 ---------- */
.nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--lims-sp-3);
  overflow-y: auto;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-group__title {
  padding: 0 var(--lims-sp-3) 6px;
  color: var(--lims-faint);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.2px;
  text-transform: uppercase;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--lims-sp-3);
  width: 100%;
  padding: 9px var(--lims-sp-3);
  border: none;
  border-radius: var(--lims-r-ctrl);
  background: transparent;
  color: var(--lims-muted);
  font-family: inherit;
  font-size: var(--lims-fs-base);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color var(--lims-dur-fast) var(--lims-ease-out),
    background var(--lims-dur-fast) var(--lims-ease-out);
}

.nav-item:hover {
  color: var(--lims-ink);
  background: rgba(255, 255, 255, 0.04);
}

/* 激活态（规范 §七 明确推荐的组合）：
 *   左侧 3px 品牌色竖条 + 淡淡的品牌色背景 + 品牌色 Icon
 * ⚠️ 规范原话：「当前菜单不要使用粗暴的大面积绿色边框」——
 *    故去掉了原实现的 border + 90° 渐变底色（那正是「大面积品牌色」）。
 *    改为等宽纯色微底（--lims-accent-soft），视觉重量显著下降。 */
.nav-item.is-active {
  background: var(--lims-accent-soft);
  color: var(--lims-ink);
  font-weight: 600;
}

.nav-item.is-active::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--lims-accent);
  content: '';
  transform: translateY(-50%);
}

/* 激活项图标取品牌色（规范 §七「品牌色 Icon」） */
.nav-item.is-active .nav-icon {
  color: var(--lims-accent);
}

.nav-icon {
  flex: none;
}

.nav-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 二级菜单（目录节点） ---------- */
.nav-sub {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* 目录标题在展开态下作为可点击按钮出现，需清除 button 默认样式 */
.nav-group__title--btn {
  display: flex;
  align-items: center;
  gap: var(--lims-sp-3);
  width: 100%;
  padding: 8px var(--lims-sp-3);
  border: none;
  border-radius: var(--lims-r-ctrl);
  background: transparent;
  color: var(--lims-faint);
  font-family: inherit;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.2px;
  text-align: left;
  text-transform: uppercase;
  white-space: nowrap;
  cursor: pointer;
  transition: color var(--lims-dur-fast) var(--lims-ease-out);
}

.nav-group__title--btn:hover {
  color: var(--lims-muted);
}

.nav-caret {
  margin-left: auto;
  transition: transform var(--lims-dur-fast) var(--lims-ease-out);
}

.nav-children {
  display: flex;
  flex-direction: column;
  gap: 2px;
  /* 子项缩进靠左内边距，而非嵌套 margin，保证折叠态下对齐不错位 */
  padding-left: var(--lims-sp-6);
}

/* 子项比父项矮一档，形成清晰层级但不过度留白 */
.nav-item--child {
  padding: 8px var(--lims-sp-3);
  font-size: var(--lims-fs-sm, 13px);
}

.nav-dot {
  flex: none;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
}

.nav-item--child.is-active .nav-dot {
  opacity: 1;
  background: var(--lims-accent);
}

/* ---------- 侧栏底部（帮助 / 关于） ---------- */
.aside-foot {
  padding-top: var(--lims-sp-2);
  margin-top: var(--lims-sp-2);
  border-top: 1px solid var(--lims-hair);
}

.nav-item--ghost {
  color: var(--lims-faint);
}

/* ---------- 主区：与侧栏贴合，顶栏实色 Header 层 ---------- */
.shell-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.shell-header {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  height: var(--lims-header-h);
  padding: 0 var(--lims-content-px);
  background: var(--lims-layer-header);
  border-bottom: 1px solid var(--lims-hair);
}

.header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--lims-sp-2);
}

/* ---------- 全局搜索触发 ---------- */
.search-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 260px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--lims-hair);
  border-radius: var(--lims-r-ctrl);
  background: rgba(255, 255, 255, 0.04);
  color: var(--lims-muted);
  font-family: inherit;
  font-size: var(--lims-fs-sm);
  cursor: pointer;
  transition:
    border-color var(--lims-dur-fast) var(--lims-ease-out),
    background var(--lims-dur-fast) var(--lims-ease-out),
    color var(--lims-dur-fast) var(--lims-ease-out);
}

.search-trigger:hover {
  border-color: rgba(var(--lims-accent-rgb), 0.42);
  color: var(--lims-ink);
  background: rgba(var(--lims-accent-rgb), 0.06);
}

.search-trigger__placeholder {
  flex: 1;
  text-align: left;
}

.search-trigger__kbd {
  padding: 1px 6px;
  border: 1px solid var(--lims-hair-2);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--lims-faint);
  font-family: var(--lims-font-mono);
  font-size: 10px;
}

/* ---------- 图标按钮 ---------- */
.icon-btn {
  position: relative;
  display: grid;
  width: 34px;
  height: 34px;
  border: 1px solid transparent;
  border-radius: var(--lims-r-ctrl);
  background: transparent;
  color: var(--lims-muted);
  cursor: pointer;
  place-items: center;
  transition:
    color var(--lims-dur-fast) var(--lims-ease-out),
    background var(--lims-dur-fast) var(--lims-ease-out),
    border-color var(--lims-dur-fast) var(--lims-ease-out);
}

.icon-btn:hover {
  color: var(--lims-ink);
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--lims-hair-2);
}

.icon-btn__badge {
  position: absolute;
  top: 4px;
  right: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--lims-danger);
  color: var(--lims-on-accent);
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  box-shadow: 0 0 0 2px var(--lims-bg);
}

/* ---------- 用户菜单 ---------- */
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px 0 4px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  background: rgba(255, 255, 255, 0.04);
  color: var(--lims-ink-2);
  font-family: inherit;
  font-size: var(--lims-fs-sm);
  cursor: pointer;
  outline: none;
  transition:
    border-color var(--lims-dur-fast) var(--lims-ease-out),
    background var(--lims-dur-fast) var(--lims-ease-out);
}

.user-chip:hover {
  border-color: rgba(var(--lims-accent-rgb), 0.45);
  background: rgba(var(--lims-accent-rgb), 0.08);
}

.user-avatar {
  display: grid;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--lims-brand-gradient);
  color: var(--lims-on-accent);
  font-size: var(--lims-fs-xs);
  font-weight: 700;
  place-items: center;
  box-shadow: 0 0 12px rgba(var(--lims-accent-rgb), 0.3);
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.1;
}

.user-name {
  font-size: var(--lims-fs-sm);
  font-weight: 600;
  color: var(--lims-ink);
}

.user-role {
  font-size: 11px;
  color: var(--lims-faint);
}

/* ---------- 主内容 ---------- */
.shell-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--lims-content-py) var(--lims-content-px);
}
</style>

<style>
/* 全局样式（非 scoped）：搜索弹窗、通知面板、确认弹窗 */
/* 通知面板 */
.lims-popover {
  padding: 0 !important;
  border-radius: var(--lims-r-ctrl) !important;
  background: var(--lims-layer-elevated) !important;
  border: 1px solid var(--lims-hair-2) !important;
  box-shadow: var(--lims-shadow-pop) !important;
}
.notif {
  width: 320px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
}
.notif__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 8px;
  border-bottom: 1px solid var(--lims-hair);
}
.notif__title {
  color: var(--lims-ink);
  font-weight: 600;
  font-size: var(--lims-fs-base);
}
.notif__list {
  list-style: none;
  margin: 0;
  padding: 6px 0;
  overflow-y: auto;
  flex: 1;
}
.notif__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background var(--lims-dur-fast) var(--lims-ease-out);
}
.notif__item:hover {
  background: rgba(255, 255, 255, 0.04);
}
.notif__count {
  color: var(--lims-ink);
  font-weight: 700;
  font-size: var(--lims-fs-base);
  margin-right: 2px;
}
.notif__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex: none;
  background: var(--lims-accent);
  box-shadow: 0 0 6px currentColor;
}
.notif__dot.is-audit,
.notif__dot.is-assign {
  background: var(--lims-info);
  color: var(--lims-info);
}
.notif__dot.is-sign,
.notif__dot.is-report {
  background: var(--lims-accent);
  color: var(--lims-accent);
}
.notif__dot.is-result {
  background: var(--lims-warning);
  color: var(--lims-warning);
}
.notif__dot.is-item {
  background: var(--lims-success);
  color: var(--lims-success);
}
.notif__body {
  flex: 1;
  min-width: 0;
}
.notif__msg {
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-sm);
  line-height: 1.45;
  margin: 0;
}
.notif__time {
  display: block;
  margin-top: 2px;
  color: var(--lims-faint);
  font-size: 11px;
}
.notif__foot {
  padding: 8px 14px 10px;
  text-align: right;
  border-top: 1px solid var(--lims-hair);
}

/* 搜索弹窗 */
.lims-search-dialog {
  border-radius: var(--lims-r-lg) !important;
  background: linear-gradient(150deg, rgba(22, 25, 30, 0.96), rgba(10, 11, 15, 0.96)) !important;
}
.search-dialog {
  padding: 4px 4px 0;
}
.search-dialog__hint {
  display: flex;
  gap: 14px;
  margin: 12px 0 8px;
  padding: 0;
  list-style: none;
  color: var(--lims-faint);
  font-size: 11px;
}
.search-dialog__hint li {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.search-dialog__hint--right {
  margin-left: auto;
}
.search-dialog__hint kbd {
  padding: 1px 5px;
  border: 1px solid var(--lims-hair-2);
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.05);
  font-family: var(--lims-font-mono);
  font-size: 10px;
}
.search-dialog__placeholder {
  padding: 8px 4px 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-sm);
  text-align: center;
  border-top: 1px solid var(--lims-hair);
}

/* ---------- 搜索命中列表 ---------- */
.search-dialog__list {
  display: flex;
  flex-direction: column;
  max-height: 320px;
  margin: 8px 0 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
  border-top: 1px solid var(--lims-hair);
}

.search-dialog__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-4);
  padding: 10px 12px;
  border-radius: var(--lims-r-xs);
  cursor: pointer;
  transition: background var(--lims-dur-fast) var(--lims-ease-out);
}

.search-dialog__item:hover {
  background: rgba(var(--lims-accent-rgb), 0.08);
}

.search-dialog__item-title {
  color: var(--lims-ink);
  font-size: var(--lims-fs-base);
}

.search-dialog__item-path {
  flex: none;
  color: var(--lims-faint);
  font-family: var(--lims-font-mono);
  font-size: 11px;
}

/* 个人资料 */
.profile-grid { display: flex; flex-direction: column; gap: 12px; }
.profile-row { display: flex; gap: 12px; font-size: var(--lims-fs-sm); }
.profile-label { width: 72px; color: var(--lims-faint); flex: none; }

/* 帮助中心 */
.help-body { font-size: var(--lims-fs-sm); line-height: 1.7; color: var(--lims-ink-2); }
.help-body h4 { margin: 12px 0 6px; color: var(--lims-ink); font-size: var(--lims-fs-base); }
.help-body ul, .help-body ol { margin: 0; padding-left: 20px; }
.help-body li { margin: 3px 0; }
.help-body kbd { padding: 1px 5px; border: 1px solid var(--lims-hair-2); border-radius: 3px; background: rgba(255,255,255,0.05); font-family: var(--lims-font-mono); font-size: 10px; }

/* 操作日志对话框：表格 + 底部「说明 + 分页」一行 */
.log-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-4);
  flex-wrap: wrap;
  margin-top: 10px;
}
.log-foot__hint {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}
</style>