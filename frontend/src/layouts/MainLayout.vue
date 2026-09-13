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
import { computed, markRaw, ref, watch, type Component } from 'vue'
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

/** 面包屑：按当前路由在菜单树中的位置生成（分组名取自后端，比本地映射更准）。 */
const breadcrumbItems = computed(() => {
  const title = (route.meta?.title as string | undefined) ?? ''
  const segments = route.path.split('/').filter(Boolean)
  const items: { title: string; to?: string }[] = [{ title: 'LIMS' }]
  // 在菜单树中定位当前 path，取其顶层祖先作为分组名
  for (const group of navMenus.value) {
    const hitSelf = group.path === route.path
    const hitChild = group.children.some((c) => c.path === route.path)
    if (hitSelf || hitChild) {
      items.push({ title: group.title })
      break
    }
  }
  // 当前页
  if (title) items.push({ title })
  // 兜底：避免只有一项
  if (items.length === 1) items.push({ title: segments[0] ?? '首页' })
  // 倒数第二项加 to 指向父级
  if (items.length >= 2) {
    const last = items[items.length - 1]
    const parent = items[items.length - 2]
    items[items.length - 1] = last
    items[items.length - 2] = { title: parent.title, to: '/' }
  }
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

/** 通知（静态示例；真实通知待后端接口实现后接入） */
interface NotificationItem {
  id: number
  type: 'audit' | 'abnormal' | 'task' | 'system'
  title: string
  time: string
  read?: boolean
}
const notifications = ref<NotificationItem[]>([
  { id: 1, type: 'audit', title: '3 份报告待审核（超 24h）', time: '2 分钟前' },
  { id: 2, type: 'abnormal', title: '样品 JK-2026-001 检测出铅超标', time: '18 分钟前' },
  { id: 3, type: 'task', title: '本周监抽任务已完成 78%', time: '今天 09:12' },
  { id: 4, type: 'system', title: '系统将于本周六 02:00 例行维护', time: '昨天 18:30', read: true },
])
const unreadCount = computed(() => notifications.value.filter((n) => !n.read).length)
function markAllRead(): void {
  notifications.value.forEach((n) => (n.read = true))
}

/** 用户菜单 */
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
      ElMessage.info('个人资料页面待接入')
      break
    case 'password':
      ElMessage.info('修改密码页面待接入')
      break
    case 'log':
      ElMessage.info('操作日志页面待接入')
      break
    case 'logout':
      void handleLogout()
      break
  }
}
</script>

<template>
  <div
    class="lims-shell"
    :class="{ 'is-collapsed': collapsed }"
  >
    <!-- ================= 侧栏 ================= -->
    <aside class="shell-aside lims-glass">
      <!-- 品牌区（极光玻璃：第一眼表面） -->
      <div class="brand lims-glass-refract">
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
      <header class="shell-header lims-glass">
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

          <!-- 通知 -->
          <el-popover
            :width="320"
            trigger="click"
            placement="bottom-end"
            popper-class="lims-popover"
          >
            <template #reference>
              <button
                type="button"
                class="icon-btn"
                title="通知"
              >
                <el-icon :size="18">
                  <Bell />
                </el-icon>
                <span
                  v-if="unreadCount > 0"
                  class="icon-btn__badge"
                >{{ unreadCount }}</span>
              </button>
            </template>
            <div class="notif">
              <div class="notif__head">
                <span class="notif__title">通知</span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  @click="markAllRead"
                >
                  全部已读
                </el-button>
              </div>
              <ul class="notif__list">
                <li
                  v-for="n in notifications"
                  :key="n.id"
                  class="notif__item"
                  :class="{ 'is-unread': !n.read }"
                >
                  <span
                    class="notif__dot"
                    :class="`is-${n.type}`"
                    aria-hidden="true"
                  />
                  <div class="notif__body">
                    <p class="notif__msg">
                      {{ n.title }}
                    </p>
                    <span class="notif__time">{{ n.time }}</span>
                  </div>
                </li>
              </ul>
              <div class="notif__foot">
                <el-button
                  link
                  type="primary"
                  size="small"
                >
                  查看全部
                </el-button>
              </div>
            </div>
          </el-popover>

          <!-- 帮助 -->
          <button
            type="button"
            class="icon-btn"
            title="帮助"
            @click="ElMessage.info('帮助中心待接入')"
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
        <router-view v-slot="{ Component }">
          <transition
            name="lims-fade"
            mode="out-in"
          >
            <component :is="Component" />
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
  </div>
</template>

<style scoped>
/* ===========================================================================
 * 布局骨架
 * =========================================================================== */
.lims-shell {
  display: flex;
  gap: var(--lims-sp-3);
  height: 100%;
  padding: var(--lims-sp-3);
}

/* ---------- 侧栏 ---------- */
.shell-aside {
  display: flex;
  flex: 0 0 var(--lims-sidebar-w);
  flex-direction: column;
  width: var(--lims-sidebar-w);
  padding: var(--lims-sp-3);
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
  border-radius: var(--lims-r-md);
  white-space: nowrap;
}

.brand-mark {
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: var(--lims-brand-gradient);
  box-shadow: 0 0 18px rgba(var(--lims-accent-rgb), 0.35);
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
  padding: 10px var(--lims-sp-3);
  border: 1px solid transparent;
  border-radius: var(--lims-r-sm);
  background: transparent;
  color: var(--lims-muted);
  font-family: inherit;
  font-size: var(--lims-fs-base);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color var(--lims-dur-fast) var(--lims-ease-out),
    background var(--lims-dur-fast) var(--lims-ease-out),
    border-color var(--lims-dur-fast) var(--lims-ease-out);
}

.nav-item:hover {
  color: var(--lims-ink);
  background: rgba(255, 255, 255, 0.05);
}

/* 激活态：左侧发光竖条 + 青调底 + 文字提亮 */
.nav-item.is-active {
  border-color: rgba(var(--lims-accent-rgb), 0.24);
  background: linear-gradient(
    90deg,
    rgba(var(--lims-accent-rgb), 0.14),
    rgba(var(--lims-accent-rgb), 0.02)
  );
  color: var(--lims-ink);
  font-weight: 600;
}

.nav-item.is-active::before {
  position: absolute;
  top: 50%;
  left: -1px;
  width: 3px;
  height: 18px;
  border-radius: var(--lims-r-pill);
  background: var(--lims-accent);
  box-shadow: 0 0 12px rgba(var(--lims-accent-rgb), 0.8);
  content: '';
  transform: translateY(-50%);
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
  border-radius: var(--lims-r-sm);
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
  box-shadow: 0 0 8px rgba(var(--lims-accent-rgb), 0.9);
  background: var(--lims-accent);
}

.aside-foot {
  padding-top: var(--lims-sp-2);
  margin-top: var(--lims-sp-2);
  border-top: 1px solid var(--lims-hair);
}

.nav-item--ghost {
  color: var(--lims-faint);
}

/* ---------- 主区 ---------- */
.shell-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--lims-sp-3);
  min-width: 0;
}

.shell-header {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  height: var(--lims-header-h);
  padding: 0 var(--lims-sp-5);
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
  color: #fff;
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
  color: #fff;
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
  border-radius: var(--lims-r-md) !important;
  background: var(--lims-surface-3) !important;
  border: 1px solid var(--lims-glass-border-soft) !important;
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
.notif__item.is-unread .notif__msg {
  color: var(--lims-ink);
  font-weight: 500;
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
.notif__dot.is-audit {
  background: var(--lims-info);
  color: var(--lims-info);
}
.notif__dot.is-abnormal {
  background: var(--lims-danger);
  color: var(--lims-danger);
}
.notif__dot.is-task {
  background: var(--lims-success);
  color: var(--lims-success);
}
.notif__dot.is-system {
  background: var(--lims-muted);
  color: var(--lims-muted);
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
</style>