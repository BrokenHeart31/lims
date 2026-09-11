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

type StageStatus = 'done' | 'active' | 'todo'

/**
 * 业务七阶段（AGENTS 7.1）导航与落地状态。
 * ⚠️ status 为「交付进度快照」，每完成一个阶段任务后同步更新，避免与 STATUS.md 口径漂移。
 */
const stages: { title: string; desc: string; status: StageStatus }[] = [
  { title: '基础数据准备', desc: '用户权限、项目标准库、判定依据、方法-检验员资质', status: 'done' },
  { title: '监抽任务管理', desc: '监抽任务录入与维护', status: 'done' },
  { title: '样品登记', desc: '采样单 Excel 导入，登记确认（S10 → S20）', status: 'done' },
  { title: '检验项目分解', desc: '自动套用项目标准库分解检测单项（S20 → S30）', status: 'done' },
  { title: '检验任务安排', desc: '按样品编号与检验方法资质自动分配（S30 → S40）', status: 'active' },
  { title: '检验数据录入', desc: '检验员录入数据，系统自动判定单项结论（S50 → S60）', status: 'todo' },
  { title: '报告审核签发', desc: '中心领导审核、签发（S60 → S70 → S80）', status: 'todo' },
  { title: '报告生成打印', desc: 'CMA / CMA-CATL 检验报告合成（S80 → S90）', status: 'todo' },
]

const doneCount = computed(() => stages.filter((s) => s.status === 'done').length)

/** 顶部统计卡（数值口径见 STATUS.md「进度评估」） */
const stats = computed(() => [
  { label: '业务阶段', value: `${doneCount.value} / ${stages.length}`, hint: '已落地' },
  { label: '项目进度', value: '41%', hint: '加权口径' },
  { label: '后端单测', value: '23', hint: '全部通过' },
])

const statusText: Record<StageStatus, string> = {
  done: '已交付',
  active: '进行中',
  todo: '待开发',
}
</script>

<template>
  <div class="dashboard">
    <!-- ================= 欢迎区 ================= -->
    <section class="hero lims-glass lims-glass-refract">
      <span
        class="hero-line"
        aria-hidden="true"
      />
      <div class="hero-main">
        <p class="hero-kicker">
          食品质量检验测试中心
        </p>
        <h2 class="hero-title lims-text-gradient">
          {{ welcomeName ? `欢迎，${welcomeName}` : '欢迎使用' }}
        </h2>
        <p class="hero-tip">
          实验室信息管理系统 · 检验数据全程留痕，结论由系统自动判定
        </p>
      </div>
      <div class="hero-stats">
        <div
          v-for="item in stats"
          :key="item.label"
          class="stat"
        >
          <span class="stat-value lims-mono">{{ item.value }}</span>
          <span class="stat-label">{{ item.label }}</span>
          <span class="stat-hint">{{ item.hint }}</span>
        </div>
      </div>
    </section>

    <!-- ================= 七阶段 ================= -->
    <section class="stage-card lims-glass">
      <header class="card-head">
        <h3 class="card-title">
          业务流程（八阶段）
        </h3>
        <span class="card-sub">由样品状态机串联 · S10 → S90</span>
      </header>

      <div class="stage-grid">
        <article
          v-for="(stage, index) in stages"
          :key="stage.title"
          class="stage-item"
          :class="`is-${stage.status}`"
        >
          <div class="stage-top">
            <span class="stage-index lims-mono">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="stage-badge">
              <i
                class="stage-dot"
                aria-hidden="true"
              />
              {{ statusText[stage.status] }}
            </span>
          </div>
          <h4 class="stage-title">
            {{ stage.title }}
          </h4>
          <p class="stage-desc">
            {{ stage.desc }}
          </p>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
}

/* ===========================================================================
 * 欢迎区
 * =========================================================================== */
.hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: var(--lims-sp-6);
  align-items: center;
  justify-content: space-between;
  padding: var(--lims-sp-6);
  border-radius: var(--lims-r-xl);
}

.hero-line {
  position: absolute;
  top: 0;
  left: 8%;
  width: 44%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(var(--lims-accent-rgb), 0.75), transparent);
}

.hero-main {
  min-width: 260px;
}

.hero-kicker {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
  letter-spacing: 2px;
  text-transform: uppercase;
}

.hero-title {
  margin: 6px 0 8px;
  font-size: var(--lims-fs-hero);
  font-weight: 800;
  line-height: 1.2;
}

.hero-tip {
  color: var(--lims-muted);
  font-size: var(--lims-fs-sm);
}

/* ---------- 统计卡 ---------- */
.hero-stats {
  display: flex;
  gap: var(--lims-sp-3);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 96px;
  padding: var(--lims-sp-3) var(--lims-sp-4);
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-md);
  background: rgba(255, 255, 255, 0.035);
  box-shadow: var(--lims-inner-hair);
}

.stat-value {
  color: var(--lims-accent);
  font-size: var(--lims-fs-xl);
  font-weight: 700;
  line-height: 1.1;
  text-shadow: 0 0 18px rgba(var(--lims-accent-rgb), 0.45);
}

.stat-label {
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-xs);
}

.stat-hint {
  color: var(--lims-faint);
  font-size: 11px;
}

/* ===========================================================================
 * 七阶段
 * =========================================================================== */
.stage-card {
  padding: var(--lims-sp-5);
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: var(--lims-sp-3);
  margin-bottom: var(--lims-sp-4);
}

.card-title {
  font-size: var(--lims-fs-lg);
  font-weight: 700;
}

.card-sub {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}

.stage-grid {
  display: grid;
  gap: var(--lims-sp-3);
  grid-template-columns: repeat(auto-fill, minmax(232px, 1fr));
}

.stage-item {
  position: relative;
  padding: var(--lims-sp-4);
  border: 1px solid var(--lims-hair);
  border-radius: var(--lims-r-md);
  background: rgba(255, 255, 255, 0.028);
  transition:
    border-color var(--lims-dur) var(--lims-ease-out),
    transform var(--lims-dur) var(--lims-ease-out),
    background var(--lims-dur) var(--lims-ease-out);
}

.stage-item:hover {
  border-color: rgba(var(--lims-accent-rgb), 0.32);
  background: rgba(255, 255, 255, 0.05);
  transform: translateY(-2px);
}

/* 已交付：青调描边 + 左侧发光条 */
.stage-item.is-done {
  border-color: rgba(var(--lims-success-rgb), 0.24);
}

.stage-item.is-done::before {
  position: absolute;
  top: 12%;
  left: -1px;
  width: 2px;
  height: 76%;
  border-radius: var(--lims-r-pill);
  background: var(--lims-success);
  content: '';
  opacity: 0.85;
}

/* 进行中：品牌渐变描边 + 呼吸光晕（唯一的动效焦点） */
.stage-item.is-active {
  border-color: rgba(var(--lims-accent-rgb), 0.4);
  background: linear-gradient(150deg, rgba(var(--lims-accent-rgb), 0.1), rgba(255, 255, 255, 0.02));
  animation: stage-breathe 3.6s ease-in-out infinite alternate;
}

@keyframes stage-breathe {
  from {
    box-shadow: 0 0 0 rgba(var(--lims-accent-rgb), 0);
  }

  to {
    box-shadow: 0 0 26px rgba(var(--lims-accent-rgb), 0.18);
  }
}

.stage-item.is-todo {
  opacity: 0.72;
}

.stage-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--lims-sp-2);
}

.stage-index {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
  font-weight: 700;
  letter-spacing: 1px;
}

.stage-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--lims-muted);
  font-size: 11px;
}

.stage-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lims-faint);
}

.is-done .stage-dot {
  background: var(--lims-success);
  box-shadow: 0 0 8px rgba(var(--lims-success-rgb), 0.8);
}

.is-active .stage-dot {
  background: var(--lims-accent);
  box-shadow: 0 0 10px rgba(var(--lims-accent-rgb), 0.9);
}

.stage-title {
  margin-bottom: 4px;
  font-size: var(--lims-fs-base);
  font-weight: 600;
}

.stage-desc {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}
</style>
