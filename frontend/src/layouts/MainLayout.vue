<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Document, Fold, List, Monitor, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

/**
 * 骨架阶段使用静态占位菜单；
 * T-002 /me 契约落地后，将根据 authStore.menus 动态生成路由与菜单。
 */
const activeMenu = computed(() => route.path)

const displayName = computed(() => authStore.userInfo?.nickname ?? authStore.userInfo?.username ?? '未登录')

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
</script>

<template>
  <el-container class="main-layout">
    <el-aside
      width="220px"
      class="layout-aside"
    >
      <div class="aside-brand">
        <span>LIMS 管理系统</span>
      </div>
      <el-menu
        class="aside-menu"
        :default-active="activeMenu"
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <template #title>
            工作台
          </template>
        </el-menu-item>
        <el-menu-item index="/task">
          <el-icon><List /></el-icon>
          <template #title>
            监抽任务
          </template>
        </el-menu-item>
        <el-menu-item index="/sample">
          <el-icon><Document /></el-icon>
          <template #title>
            样品登记
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon :size="18">
            <Fold />
          </el-icon>
          <span class="header-breadcrumb">{{ route.meta.title ?? '' }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleLogout">
            <span class="header-user">
              <el-icon :size="16"><SwitchButton /></el-icon>
              {{ displayName }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-layout {
  height: 100%;
}

.layout-aside {
  background-color: #1f2d3d;
}

.aside-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
  border-bottom: 1px solid rgb(255 255 255 / 10%);
}

.aside-menu {
  border-right: none;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
}

.header-breadcrumb {
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  outline: none;
}

.layout-main {
  background: #f0f2f5;
  padding: 16px;
}
</style>
