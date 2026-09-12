<script setup lang="ts">
/**
 * MainLayout — 应用外壳（UI 升级 2026-09-12 GLM）
 * ----------------------------------------------------------------------------
 * 结构：
 *   Sidebar (224px / collapse 68px)  +  Main(Header 60px + Breadcrumb + <router-view>)
 * 设计：
 *   - 侧栏分组：工作台 / 业务管理 / 实验室业务（提示词 §七）
 *   - 顶部 Header：面包屑移动到此处的搜索栏之前 → 提示词 §八
 *   - 全局搜索 ⌘K、通知、帮助、用户菜单（个人资料/修改密码/操作日志/退出）
 *   - 极光玻璃品牌区（保留 Aurora Glass 华丽质感）
 */
import { computed, markRaw, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Bell,
  Document,
  EditPen,
  Expand,
  Fold,
  Help,
  Histogram,
  List,
  Lock,
  Monitor,
  Notebook,
  Operation,
  Search,
  SwitchButton,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import { confirm } from '@/utils/confirm'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

interface MenuGroup {
  title: string
  items: { path: string; title: string; icon: ReturnType<typeof markRaw> }[]
}

/** 侧栏分组（提示词 §七：业务管理 / 实验室业务 / 数据中心 / 系统管理）。
 *  本轮只渲染已落地的 7 个业务页；数据中心/系统管理未实现，预留分组名（隐藏分组本身）。 */
const menuGroups: MenuGroup[] = [
  {
    title: '工作台',
    items: [{ path: '/dashboard', title: '概览', icon: markRaw(Monitor) }],
  },
  {
    title: '业务管理',
    items: [
      { path: '/task', title: '监抽任务', icon: markRaw(List) },
      { path: '/sample', title: '样品登记', icon: markRaw(Document) },
      { path: '/item/decompose', title: '项目分解', icon: markRaw(Operation) },
      { path: '/assign/index', title: '任务安排', icon: markRaw(UserFilled) },
    ],
  },
  {
    title: '实验室业务',
    items: [
      { path: '/result/entry', title: '结果录入', icon: markRaw(EditPen) },
      { path: '/report/audit', title: '报告审核', icon: markRaw(Notebook) },
    ],
  },
]

/** 侧栏折叠 */
const collapsed = ref(false)

const displayName = computed(
  () => authStore.userInfo?.nickname ?? authStore.userInfo?.username ?? '未登录',
)

const avatarText = computed(() => displayName.value.trim().charAt(0).toUpperCase() || 'L')

function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(`${path}/`)
}

async function go(path: string): Promise<void> {
  if (route.path !== path) await router.push(path)
}

/** 面包屑：从路由 meta.breadcrumb 或自动按分组路径生成。 */
const breadcrumbItems = computed(() => {
  const title = (route.meta?.title as string | undefined) ?? ''
  const segments = route.path.split('/').filter(Boolean)
  const items: { title: string; to?: string }[] = [{ title: 'LIMS' }]
  // 找到当前路径所属分组
  for (const g of menuGroups) {
    if (g.items.some((i) => isActive(i.path))) {
      items.push({ title: g.title })
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

/** 顶部全局搜索（仅展示 UI，未实现搜索逻辑） */
const searchKeyword = ref('')
const searchDialogOpen = ref(false)
function handleSearchKey(e: KeyboardEvent): void {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchDialogOpen.value = true
  }
}
if (typeof window !== 'undefined') window.addEventListener('keydown', handleSearchKey)

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

      <!-- 分组导航 -->
      <nav class="nav">
        <div
          v-for="group in menuGroups"
          :key="group.title"
          class="nav-group"
        >
          <div
            v-if="!collapsed"
            class="nav-group__title"
          >
            {{ group.title }}
          </div>
          <button
            v-for="item in group.items"
            :key="item.path"
            type="button"
            class="nav-item"
            :class="{ 'is-active': isActive(item.path) }"
            :title="collapsed ? `${group.title} · ${item.title}` : undefined"
            @click="go(item.path)"
          >
            <el-icon
              class="nav-icon"
              :size="18"
            >
              <component :is="item.icon" />
            </el-icon>
            <span
              v-show="!collapsed"
              class="nav-text"
            >{{ item.title }}</span>
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

    <!-- 全局搜索弹窗（占位 UI，待接真实搜索） -->
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
          placeholder="搜索样品 / 任务 / 报告 / 用户…"
          size="large"
          :prefix-icon="Search"
          autofocus
        />
        <ul class="search-dialog__hint">
          <li><kbd>↑</kbd><kbd>↓</kbd> 切换</li>
          <li><kbd>↵</kbd> 打开</li>
          <li><kbd>Esc</kbd> 关闭</li>
          <li class="search-dialog__hint--right">
            ⌘K 全局唤起
          </li>
        </ul>
        <p class="search-dialog__placeholder">
          搜索功能为前端占位，对接真实接口后可按业务对象跳转（样品 / 任务 / 报告 / 用户）。
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
</style>