<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

onMounted(async () => {
  // 登录后按需拉取 /me（角色 / 权限 / 菜单树）
  if (authStore.isLoggedIn && !authStore.me) {
    try {
      await authStore.fetchMe()
    } catch {
      // 请求层已提示错误；工作台仍可展示骨架内容
    }
  }
})

const welcomeName = computed(() => authStore.userInfo?.nickname ?? authStore.userInfo?.username ?? '')

/**
 * 业务七阶段导航（占位，待动态菜单接入后移除）：
 * 基础数据准备 → 监抽任务管理 → 样品登记（采样单导入）→ 检验项目分解（自动套库）
 * → 检验任务安排 → 检验数据录入（自动判定）→ 报告审核签发 → 报告生成打印 →（查询 / 省平台上报）
 */
const stages = [
  { title: '基础数据准备', desc: '用户权限、项目标准库、判定依据、方法-检验员资质' },
  { title: '监抽任务管理', desc: '监抽任务录入与维护' },
  { title: '样品登记', desc: '采样单 Excel 导入，登记确认（S10 → S20）' },
  { title: '检验项目分解', desc: '自动套用项目标准库分解检测单项（S20 → S30）' },
  { title: '检验任务安排', desc: '按样品编号与检验方法资质自动分配（S30 → S40）' },
  { title: '检验数据录入', desc: '检验员录入数据，系统自动判定单项结论（S50 → S60）' },
  { title: '报告审核签发', desc: '中心领导审核、签发（S60 → S70 → S80）' },
  { title: '报告生成打印', desc: 'CMA / CMA-CATL 检验报告合成（S80 → S90）' },
]
</script>

<template>
  <div class="dashboard">
    <el-card
      shadow="never"
      class="welcome-card"
    >
      <template #header>
        <span>工作台</span>
      </template>
      <p class="welcome-text">
        {{ welcomeName ? `欢迎，${welcomeName}` : '欢迎使用' }}食品质量检验测试中心实验室信息管理系统
      </p>
      <p class="welcome-tip">
        当前为工程骨架阶段：登录 / 布局 / 路由守卫已就绪，业务功能将按七阶段任务（T-2xx ~ T-8xx）逐步交付。
      </p>
    </el-card>

    <el-card
      shadow="never"
      class="stage-card"
    >
      <template #header>
        <span>业务流程（七阶段）</span>
      </template>
      <el-row :gutter="12">
        <el-col
          v-for="(stage, index) in stages"
          :key="stage.title"
          :span="6"
        >
          <div class="stage-item">
            <div class="stage-index">
              {{ index + 1 }}
            </div>
            <div class="stage-title">
              {{ stage.title }}
            </div>
            <div class="stage-desc">
              {{ stage.desc }}
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-text {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.welcome-tip {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.stage-item {
  min-height: 96px;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.stage-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-bottom: 6px;
  color: #ffffff;
  font-size: 12px;
  background: #409eff;
  border-radius: 50%;
}

.stage-title {
  margin-bottom: 4px;
  font-size: 14px;
  font-weight: 600;
}

.stage-desc {
  font-size: 12px;
  color: #909399;
}
</style>
