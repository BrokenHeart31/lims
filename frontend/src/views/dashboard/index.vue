<script setup lang="ts">
/**
 * Dashboard — 工作台
 * ----------------------------------------------------------------------------
 * 三大块（提示词 §十）：
 *   ① Hero 欢迎区（保留 Aurora Glass 华丽质感）
 *   ② KPI 行（4 个 StatCard）
 *   ③ 八阶段业务进度网格
 *   ④ 最近任务列表（取真实的「我的检验任务」接口，不使用 mock）
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Document, EditPen, Histogram, Notebook, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getStatOverviewApi, type StatOverview } from '@/api/stat'
import { get } from '@/utils/request'
import type { PageResult } from '@/types/api'
import AppCard from '@/components/common/AppCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppSkeleton from '@/components/common/AppSkeleton.vue'
import AppLoading from '@/components/common/AppLoading.vue'

const authStore = useAuthStore()
const router = useRouter()
const overview = ref<StatOverview | null>(null)
const dashboardLoading = ref(false)
const overviewError = ref(false)
const tasksError = ref(false)

/**
 * 权限感知（2026-09-14 修复）
 * ----------------------------------------------------------------------------
 * 现象：以 R3「检验员」登录时，工作台顶部直接弹出红色「业务概览加载失败」；
 * 点「查看质量分析」跳到 404。根因是工作台**无条件**调用了
 * `/stat/overview`（需 `stat:view`）与 `/query/my-tasks/page`（需 `result:entry`），
 * 而检验员没有 `stat:view`。
 *
 * 判断：**「没有权限」不是「加载失败」**。用错误告警表达权限不足，
 * 会让用户以为系统坏了（本次用户反馈正是如此），也会掩盖真正的故障。
 * 正确做法是——只请求当前账号有权限的数据，无权限的部分呈现**中性空态**并说明原因。
 */
const canViewStat = computed(() => authStore.hasPermission('stat:view'))
/** 我的检验任务与结果录入共用 `result:entry`（见契约第 13 章） */
const canViewMyTasks = computed(() => authStore.hasPermission('result:entry'))
/** 当前账号是否能看到任何一块工作台数据 */
const hasAnyDashboardData = computed(() => canViewStat.value || canViewMyTasks.value)

interface MyTaskRow {
  sampleId: number
  sampleNo: string
  sampleName?: string | null
  itemName: string
  entered?: boolean | null
  sampleStatusLabel?: string | null
  sampleStatus: number
}

const myTasks = ref<MyTaskRow[]>([])
const myTasksTotal = ref<number | null>(null)

async function loadDashboard(): Promise<void> {
  if (dashboardLoading.value) return
  dashboardLoading.value = true
  overviewError.value = false
  tasksError.value = false
  overview.value = null
  myTasks.value = []
  myTasksTotal.value = null

  // 只发起「有权限」的请求；无权限的保持 null 并由模板渲染中性空态，
  // 这样 overviewError / tasksError 的语义被收窄为「有权限但真的失败了」。
  const jobs: Promise<void>[] = []
  if (canViewStat.value) {
    jobs.push(
      getStatOverviewApi()
        .then((data) => { overview.value = data })
        .catch(() => { overviewError.value = true }),
    )
  }
  if (canViewMyTasks.value) {
    jobs.push(
      get<PageResult<MyTaskRow>>('/query/my-tasks/page', { current: 1, size: 5 })
        .then((data) => {
          myTasks.value = data.records
          myTasksTotal.value = data.total
        })
        .catch(() => { tasksError.value = true }),
    )
  }
  await Promise.all(jobs)
  dashboardLoading.value = false
}

onMounted(async () => {
  if (authStore.isLoggedIn && !authStore.me) {
    try {
      await authStore.fetchMe()
    } catch {
      // 请求层已提示错误；统计请求仍会按权限返回真实错误
    }
  }
  await loadDashboard()
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
  { title: '报告生成打印', desc: 'CMA / CMA-CATL 检验报告合成（S80 → S90）', status: 'done' },
]

/**
 * KPI 行：全部来自统计接口或当前用户任务分页，不展示猜测数字。
 *
 * 2026-09-14：改为**按权限过滤**——检验员没有 `stat:view`，
 * 若仍渲染「检测中样品 / 已完成样品 / 合格率」三张卡，会得到一排「—」，
 * 既无信息量又让人以为数据坏了。无权限的卡片直接不出现在列表里。
 */
const kpis = computed(() => {
  const list: {
    label: string
    value: string | number
    suffix: string
    icon: typeof Document
    iconTone: 'info' | 'accent' | 'success' | 'warning'
    hint: string
  }[] = []
  if (canViewMyTasks.value) {
    list.push({
      label: '待处理任务',
      value: myTasksTotal.value ?? '—',
      suffix: '项',
      icon: Document,
      iconTone: 'info',
      hint: '当前权限范围',
    })
  }
  if (canViewStat.value) {
    list.push(
      {
        label: '检测中样品',
        value: overview.value?.testingSamples ?? '—',
        suffix: '份',
        icon: EditPen,
        iconTone: 'accent',
        hint: '真实统计',
      },
      {
        label: '已完成样品',
        value: overview.value?.completedSamples ?? '—',
        suffix: '份',
        icon: Notebook,
        iconTone: 'success',
        hint: '已签发及以上',
      },
      {
        label: '合格率',
        value: overview.value?.qualifiedRate == null ? '—' : overview.value.qualifiedRate.toFixed(1),
        suffix: overview.value?.qualifiedRate == null ? '' : '%',
        icon: Histogram,
        iconTone: 'warning',
        hint: overview.value?.qualifiedRate == null ? '暂无有效结论' : '排除待判定',
      },
    )
  }
  return list
})

const statusText: Record<StageStatus, string> = {
  done: '已交付',
  active: '进行中',
  todo: '待开发',
}

interface RecentTask {
  id: number
  title: string
  status: string
  statusTone: 'success' | 'warning' | 'info' | 'danger' | 'purple'
  meta: string
}

const recentTasks = computed<RecentTask[]>(() => myTasks.value.map((task) => ({
  id: task.sampleId,
  title: `${task.sampleNo} · ${task.itemName}`,
  status: task.entered ? '已录入' : '未录入',
  statusTone: task.entered ? 'success' : 'warning',
  meta: `${task.sampleName ?? '未填写样品名'} · ${task.sampleStatusLabel ?? `S${task.sampleStatus}`}`,
})))
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
        <!-- 跳转按钮按权限显隐：此前硬编码 → 无权限账号点击后命中 catch-all 变 404 -->
        <button
          v-if="canViewMyTasks"
          type="button"
          class="hero-cta"
          @click="router.push('/result/entry')"
        >
          <el-icon :size="14">
            <EditPen />
          </el-icon>
          录入检测数据
        </button>
        <button
          v-if="canViewStat"
          type="button"
          class="hero-cta hero-cta--ghost"
          @click="router.push('/query/analysis')"
        >
          <el-icon :size="14">
            <Histogram />
          </el-icon>
          查看质量分析
        </button>
        <!-- 两个按钮都没权限时，给一句明确的替代指引，而不是一片空白 -->
        <span
          v-if="!canViewMyTasks && !canViewStat"
          class="hero-tip"
        >请从左侧菜单进入你负责的业务功能</span>
      </div>
    </section>

    <!-- 只有「有权限但真的失败」才报警告；权限不足由各区块的中性空态表达 -->
    <el-alert
      v-if="!dashboardLoading && overviewError"
      type="error"
      :closable="false"
      show-icon
      title="业务概览加载失败"
      description="KPI 已显示为不可用，不会用 0 或上次数据代替；当前任务仍可独立查看。"
    >
      <el-button @click="loadDashboard">
        重新加载
      </el-button>
    </el-alert>

    <!-- ================= KPI 行 ================= -->
    <!-- 加载中优先用骨架屏（UI 提示词 §二十七：Loading 优先 Skeleton），
         避免「先闪一排空卡片再填数字」的跳变，也明确区分「正在加载」与「确实为 0」 -->
    <section
      v-if="dashboardLoading"
      class="kpi-row"
    >
      <AppCard
        v-for="n in 4"
        :key="`kpi-skeleton-${n}`"
        padding="18px"
      >
        <AppSkeleton
          :rows="3"
          height="14px"
        />
      </AppCard>
    </section>
    <section
      v-else-if="!hasAnyDashboardData"
      class="kpi-row kpi-row--notice"
    >
      <AppEmpty
        title="当前账号没有工作台统计权限"
        hint="你的业务功能在左侧菜单中（如「结果录入」「我的检验任务」）；如需查看统计概览，请联系管理员分配 stat:view 权限。"
      />
    </section>
    <section
      v-else
      class="kpi-row"
    >
      <StatCard
        v-for="k in kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :suffix="k.suffix"
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
          <div>
            <h3 class="card-title">
              当前任务
            </h3>
            <span class="card-sub">来自真实任务查询接口 · 最多展示 5 项</span>
          </div>
          <button
            class="card-refresh"
            type="button"
            :disabled="dashboardLoading"
            @click="loadDashboard"
          >
            <el-icon :size="14">
              <Refresh />
            </el-icon>
            刷新
          </button>
        </header>

        <AppLoading
          v-if="dashboardLoading"
          :min-height="180"
          text="正在读取任务数据…"
        />
        <AppEmpty
          v-else-if="!canViewMyTasks"
          title="当前账号没有检验任务权限"
          hint="「我的检验任务」需要 result:entry 权限；请联系管理员分配。"
        />
        <AppEmpty
          v-else-if="tasksError"
          title="任务数据暂时不可用"
          hint="请刷新重试；页面不会用假数据替代真实业务数据。"
        >
          <el-button
            type="primary"
            @click="loadDashboard"
          >
            重新加载
          </el-button>
        </AppEmpty>
        <AppEmpty
          v-else-if="recentTasks.length === 0"
          title="当前没有检验任务"
          hint="当任务安排完成后，任务会显示在这里。"
        />
        <ol
          v-else
          class="recent-list"
        >
          <li
            v-for="t in recentTasks"
            :key="`${t.id}-${t.title}`"
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
                <span>{{ t.meta }}</span>
              </div>
            </div>
          </li>
        </ol>

        <footer
          v-if="canViewMyTasks"
          class="recent-foot"
        >
          <button
            class="recent-foot__link"
            type="button"
            @click="router.push('/result/my-tasks')"
          >
            查看全部我的检验任务
          </button>
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
  color: var(--lims-on-accent);
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

/* 无统计权限时：整行只放一条中性说明（不是错误） */
.kpi-row--notice {
  display: block;
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
  display: block;
  margin-top: 4px;
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}

.card-refresh,
.recent-foot__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--lims-muted);
  font: inherit;
  font-size: var(--lims-fs-xs);
  cursor: pointer;
}
.card-refresh:hover,
.recent-foot__link:hover { color: var(--lims-accent); }
.card-refresh:disabled { cursor: wait; opacity: 0.6; }

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

.recent-foot__link {
  color: var(--lims-faint);
  font-size: 11px;
}
</style>