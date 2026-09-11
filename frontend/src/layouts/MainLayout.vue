<script setup lang="ts">
import { computed, markRaw, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Document,
  Expand,
  Fold,
  List,
  Monitor,
  Operation,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/**
 * 侧栏导航配置。
 * 骨架阶段为静态清单；T-801 接入 /me 菜单树后改为按权限动态生成
 * （届时只需替换 menus 的来源，模板无需改动）。
 */
const menus = [
  { path: '/dashboard', title: '工作台', icon: markRaw(Monitor) },
  { path: '/task', title: '监抽任务', icon: markRaw(List) },
  { path: '/sample', title: '样品登记', icon: markRaw(Document) },
  { path: '/item/decompose', title: '项目分解', icon: markRaw(Operation) },
]

/** 侧栏折叠（专注录入时可收起，给数据区让出宽度） */
const collapsed = ref(false)

const displayName = computed(
  () => authStore.userInfo?.nickname ?? authStore.userInfo?.username ?? '未登录',
)

/** 头像用显示名首字，避免引入额外图片资源 */
const avatarText = computed(() => displayName.value.trim().charAt(0).toUpperCase() || 'L')

/**
 * 激活判定：精确匹配优先，其次前缀匹配。
 * 用前缀是为了让 /item/decompose 这类二级路径在子页面下仍高亮。
 */
function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(`${path}/`)
}

async function go(path: string): Promise<void> {
  if (route.path !== path) await router.push(path)
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  authStore.logout()
  await router.push({ name: 'login' })
}

function handleUserCommand(command: string | number | object): void {
  if (command === 'logout') void handleLogout()
}
</script>

<template>
  <div class="lims-shell">
    <!-- ================= 侧栏 ================= -->
    <aside
      class="shell-aside lims-glass"
      :class="{ 'is-collapsed': collapsed }"
    >
      <!-- 品牌区（折射玻璃：全站仅少数「第一眼表面」启用） -->
      <div class="brand lims-glass-refract">
        <span
          class="brand-mark"
          aria-hidden="true"
        />
        <span
          v-show="!collapsed"
          class="brand-text lims-text-gradient"
        >LIMS 管理系统</span>
      </div>

      <nav class="nav">
        <button
          v-for="item in menus"
          :key="item.path"
          type="button"
          class="nav-item"
          :class="{ 'is-active': isActive(item.path) }"
          :title="collapsed ? item.title : undefined"
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
      </nav>

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
          >{{ collapsed ? '展开' : '收起侧栏' }}</span>
        </button>
      </div>
    </aside>

    <!-- ================= 主区 ================= -->
    <div class="shell-body">
      <header class="shell-header lims-glass">
        <div class="header-left">
          <h1 class="page-title">
            {{ route.meta.title ?? '' }}
          </h1>
          <span class="page-sub">食品质量检验测试中心</span>
        </div>

        <el-dropdown
          trigger="click"
          @command="handleUserCommand"
        >
          <button
            type="button"
            class="user-chip"
          >
            <span class="user-avatar">{{ avatarText }}</span>
            <span class="user-name">{{ displayName }}</span>
            <el-icon :size="12">
              <ArrowDown />
            </el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

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
  flex: 0 0 216px;
  flex-direction: column;
  width: 216px;
  padding: var(--lims-sp-3);
  overflow: hidden;
  transition:
    flex-basis var(--lims-dur) var(--lims-ease-out),
    width var(--lims-dur) var(--lims-ease-out);
}

.shell-aside.is-collapsed {
  flex-basis: 68px;
  width: 68px;
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

/* 品牌标记：青→蓝渐变小方块 + 外发光，替代图片 logo */
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

/* ---------- 导航 ---------- */
.nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
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
  height: 60px;
  padding: 0 var(--lims-sp-5);
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: var(--lims-sp-3);
  min-width: 0;
}

.page-title {
  font-size: var(--lims-fs-xl);
  font-weight: 700;
  letter-spacing: 0.2px;
}

.page-sub {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}

/* ---------- 用户区 ---------- */
.user-chip {
  display: flex;
  align-items: center;
  gap: var(--lims-sp-2);
  padding: 6px 12px 6px 6px;
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
}

/* ---------- 内容区 ---------- */
.shell-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
</style>
