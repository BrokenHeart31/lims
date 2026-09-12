<script setup lang="ts">
/**
 * Dashboard — 工作台
 * ----------------------------------------------------------------------------
 * 三大块（提示词 §十）：
 *   ① Hero 欢迎区（保留 Aurora Glass 华丽质感）
 *   ② KPI 行（4 个 StatCard）
 *   ③ 八阶段业务进度网格
 *   ④ 最近任务时间轴（mock；真实数据需 T-801 任务查询接口落地）
 */
import { computed, onMounted } from 'vue'
import { Bell, Document, EditPen, Histogram, Notebook } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import AppCard from '@/components/common/AppCard.vue'
import StatCard from '@/components/common/StatCard.vue'

const authStore = useAuthStore()

onMounted(async () => {
  if (authStore.isLoggedIn && !authStore.me) {
    try {
      await authStore.fetchMe()
    } catch {
      // 请求层已提示错误；工作台仍可展示骨架内容
    }
  }
})

const welcomeName = computed(() => authStore.userInfo?.nickname ?? authStore.userInfo?.username ?? '')
const today = computed(() => {
  const d = new Date()
  const w = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 星期${w}`
})

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
  { title: '检验任务安排', desc: '按样品编号与检验方法资质自动分配（S30 → S40）', status: 'done' },
  { title: '检验数据录入', desc: '检验员录入数据，系统自动判定单项结论（S50 → S60）', status: 'done' },
  { title: '报告审核签发', desc: '中心领导审核、签发（S60 → S70 → S80）', status: 'done' },
  { title: '报告生成打印', desc: 'CMA / CMA-CATL 检验报告合成（S80 → S90）', status: 'todo' },
]

/** KPI 行（按提示词 §十：待处理任务 / 检测中样品 / 待审核报告 / 异常样品） */
const kpis = [
  { label: '待处理任务', value: 12, suffix: '项', trend: '+3', trendTone: 'up' as const, icon: Document, iconTone: 'info' as const, hint: '较昨日' },
  { label: '检测中样品', value: 38, suffix: '份', trend: '+8', trendTone: 'up' as const, icon: EditPen, iconTone: 'accent' as const, hint: '在检中' },
  { label: '待审核报告', value: 7, suffix: '份', trend: '-2', trendTone: 'down' as const, icon: Notebook, iconTone: 'warning' as const, hint: '建议 24h 内审' },
  { label: '异常样品', value: 2, suffix: '份', trend: '+1', trendTone: 'up' as const, icon: Bell, iconTone: 'danger' as const, hint: '需复核' },
]

const statusText: Record<StageStatus, string> = {
  done: '已交付',
  active: '进行中',
  todo: '待开发',
}

/** 最近任务（mock：真实接口由 T-801 / T-701 提供） */
interface RecentTask {
  id: number
  title: string
  type: 'audit' | 'result' | 'sample' | 'sign'
  status: string
  statusTone: 'success' | 'warning' | 'info' | 'danger' | 'purple'
  time: string
  operator: string
}
const recentTasks: RecentTask[] = [
  { id: 1, title: '样品 JK-2026-013 检测结果录入', type: 'result', status: '检验中', statusTone: 'warning', time: '今天 14:32', operator: 'nj001 系统管理员' },
  { id: 2, title: '样品 JK-2026-010 报告审核', type: 'audit', status: '已通过', statusTone: 'success', time: '今天 11:18', operator: '审核员' },
  { id: 3, title: '样品 JK-2026-008 报告签发', type: 'sign', status: '已签发', statusTone: 'success', time: '今天 10:05', operator: '签发员' },
  { id: 4, title: '采样单 2026-W38 批次导入', type: 'sample', status: '已登记', statusTone: 'info', time: '昨天 16:42', operator: '采样员' },
  { id: 5, title: '样品 JK-2026-005 检测出铅超标', type: 'result', status: '待判定', statusTone: 'purple', time: '昨天 09:21', operator: 'nj002 检验员' },
]

const taskTypeLabel: Record<RecentTask['type'], string> = {
  audit: '审核',
  result: '录入',
  sample: '登记',
  sign: '签发',
}
</script>

<template>
  <div class="dashboard">
    <!-- ================= Hero 欢迎区 ================= -->
    <section class="hero lims-glass lims-glass-refract">
      <span
        class="hero-line"
        aria-hidden="true"
      />
      <div class="hero-main">
        <p class="hero-kicker">
          食品质量检验测试中心 · {{ today }}
        </p>
        <h2 class="hero-title lims-text-gradient">
          {{ welcomeName ? `下午好，${welcomeName}` : '欢迎使用 LIMS' }}
        </h2>
        <p class="hero-tip">
          实验室当前运行状态良好 · 检验数据全程留痕，结论由系统自动判定
        </p>
      </div>
      <div class="hero-side">
        <button
          type="button"
          class="hero-cta"
        >
          <el-icon :size="14">
            <EditPen />
          </el-icon>
          录入检测数据
        </button>
        <button
          type="button"
          class="hero-cta hero-cta--ghost"
        >
          <el-icon :size="14">
            <Histogram />
          </el-icon>
          查看质量分析
        </button>
      </div>
    </section>

    <!-- ================= KPI 行 ================= -->
    <section class="kpi-row">
      <StatCard
        v-for="k in kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :suffix="k.suffix"
        :trend="k.trend"
        :trend-tone="k.trendTone"
        :icon="k.icon"
        :icon-tone="k.iconTone"
        :hint="k.hint"
      />
    </section>

    <!-- ================= 八阶段 + 最近任务 ================= -->
    <div class="dashboard-grid">
      <AppCard padding="22px">
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
      </AppCard>

      <AppCard padding="22px">
        <header class="card-head">
          <h3 class="card-title">
            最近任务
          </h3>
          <span class="card-sub">实时反映样品生命周期动态</span>
        </header>

        <ol class="recent-list">
          <li
            v-for="t in recentTasks"
            :key="t.id"
            class="recent-item"
          >
            <span
              class="recent-item__dot"
              :class="`is-${t.statusTone}`"
              aria-hidden="true"
            />
            <div class="recent-item__body">
              <div class="recent-item__title">
                <span class="recent-item__name">{{ t.title }}</span>
                <span
                  class="recent-item__tag"
                  :class="`is-${t.statusTone}`"
                >{{ t.status }}</span>
              </div>
              <div class="recent-item__meta">
                <span>{{ t.time }}</span>
                <span class="recent-item__sep">·</span>
                <span>{{ t.operator }}</span>
                <span class="recent-item__sep">·</span>
                <span class="recent-item__type">{{ taskTypeLabel[t.type] }}</span>
              </div>
            </div>
          </li>
        </ol>

        <footer class="recent-foot">
          <span class="recent-foot__hint">更多任务待 T-801 查询接口接入后展示</span>
        </footer>
      </AppCard>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--lims-page-gap);
}

/* ===========================================================================
 * Hero 欢迎区
 * =========================================================================== */
.hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: var(--lims-sp-6);
  align-items: center;
  justify-content: space-between;
  padding: var(--lims-sp-6) var(--lims-sp-7);
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
  min-width: 280px;
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

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 200px;
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  padding: 0 16px;
  border: none;
  border-radius: var(--lims-r-sm);
  background: var(--lims-brand-gradient);
  color: #fff;
  font-family: inherit;
  font-size: var(--lims-fs-sm);
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(27, 92, 240, 0.24);
  transition: transform var(--lims-dur-fast) var(--lims-ease-out), box-shadow var(--lims-dur-fast) var(--lims-ease-out);
}

.hero-cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 26px rgba(27, 92, 240, 0.34);
}

.hero-cta--ghost {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--lims-hair-2);
  color: var(--lims-ink);
  box-shadow: none;
}

.hero-cta--ghost:hover {
  border-color: rgba(var(--lims-accent-rgb), 0.4);
  color: var(--lims-accent);
  background: rgba(var(--lims-accent-rgb), 0.06);
}

/* ===========================================================================
 * KPI 行
 * =========================================================================== */
.kpi-row {
  display: grid;
  gap: var(--lims-sp-3);
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

/* ===========================================================================
 * 八阶段 + 最近任务（双列）
 * =========================================================================== */
.dashboard-grid {
  display: grid;
  gap: var(--lims-page-gap);
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
}

@media (max-width: 1180px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
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

/* ---------- 八阶段网格 ---------- */
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

/* ---------- 最近任务时间轴 ---------- */
.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.recent-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--lims-hair);
}

.recent-item:last-child {
  border-bottom: none;
}

.recent-item__dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  flex: none;
  border-radius: 50%;
  background: var(--lims-accent);
  box-shadow: 0 0 8px currentColor;
}

.recent-item__dot.is-success {
  background: var(--lims-success);
  color: var(--lims-success);
}
.recent-item__dot.is-warning {
  background: var(--lims-warning);
  color: var(--lims-warning);
}
.recent-item__dot.is-info {
  background: var(--lims-info);
  color: var(--lims-info);
}
.recent-item__dot.is-danger {
  background: var(--lims-danger);
  color: var(--lims-danger);
}
.recent-item__dot.is-purple {
  background: var(--lims-purple);
  color: var(--lims-purple);
}

.recent-item__body {
  flex: 1;
  min-width: 0;
}

.recent-item__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.recent-item__name {
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  font-weight: 500;
}

.recent-item__tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: var(--lims-r-pill);
  font-size: 11px;
  font-weight: 500;
}

.recent-item__tag.is-success {
  color: var(--lims-success);
  background: var(--lims-success-soft);
}
.recent-item__tag.is-warning {
  color: var(--lims-warning);
  background: var(--lims-warning-soft);
}
.recent-item__tag.is-info {
  color: var(--lims-info);
  background: var(--lims-info-soft);
}
.recent-item__tag.is-danger {
  color: var(--lims-danger);
  background: var(--lims-danger-soft);
}
.recent-item__tag.is-purple {
  color: var(--lims-purple);
  background: var(--lims-purple-soft);
}

.recent-item__meta {
  margin-top: 4px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  color: var(--lims-faint);
  font-size: 11px;
}

.recent-item__sep {
  color: var(--lims-faint);
}

.recent-item__type {
  color: var(--lims-muted);
  font-weight: 500;
}

.recent-foot {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--lims-hair);
  text-align: center;
}

.recent-foot__hint {
  color: var(--lims-faint);
  font-size: 11px;
}
</style>