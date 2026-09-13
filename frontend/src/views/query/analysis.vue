<script setup lang="ts">
/**
 * 质量分析看板（T-803）
 * ----------------------------------------------------------------------------
 * 需求来源：业务功能说明书 —— 中心领导/质量负责人需要掌握检验业务整体运行情况。
 *
 * ⚠️ 硬约束（DECISIONS 2026-09-13 T-803 裁决）：
 *   ① **禁 mock 假数据** —— 页面所有数字均来自 /stat/* 真实聚合 SQL；
 *      无数据时显示 0 或空态，绝不填演示数据。
 *   ② **后端不加缓存**，本页提供「刷新」按钮手动重取；
 *      切页/切标签不重复请求（后端聚合走索引，开销可控）。
 *   ③ 合格率分母**排除待判定**（后端已实现），故前端直接展示后端值，
 *      不做二次计算——避免前后端两套口径。
 */
import { computed, onMounted, ref } from 'vue'
import {
  DataAnalysis,
  Refresh,
  Timer,
  CircleCheck,
  CircleClose,
  Warning,
  Document,
} from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import LimsChart from '@/components/common/LimsChart.vue'
import {
  getStatOverviewApi,
  getSampleStatusStatApi,
  getInspectTypeStatApi,
  getTopClientsStatApi,
  getCategoryStatApi,
  getTesterWorkloadStatApi,
  getUnqualifiedItemsStatApi,
  getMonthlyTrendStatApi,
  type StatOverview,
  type StatNameValue,
  type StatTrend,
} from '@/api/stat'
import { pieOption, hBarOption, barOption, trendOption } from '@/utils/chartOptions'

const loading = ref(false)
const overview = ref<StatOverview | null>(null)
const statusDist = ref<StatNameValue[]>([])
const inspectType = ref<StatNameValue[]>([])
const topClients = ref<StatNameValue[]>([])
const category = ref<StatNameValue[]>([])
const testerWorkload = ref<StatNameValue[]>([])
const unqualifiedItems = ref<StatNameValue[]>([])
const trend = ref<StatTrend[]>([])

/** 趋势月份数（预留：可做成下拉切换） */
const trendMonths = ref(6)

/**
 * 并行拉取全部统计。
 * 用 allSettled 而非 all：单个接口异常不应导致整页空白，
 * 失败的图表自然落到空态，其余图表照常展示。
 */
async function load(): Promise<void> {
  loading.value = true
  const tasks = [
    getStatOverviewApi().then((d) => (overview.value = d)),
    getSampleStatusStatApi().then((d) => (statusDist.value = d ?? [])),
    getInspectTypeStatApi().then((d) => (inspectType.value = d ?? [])),
    getTopClientsStatApi(8).then((d) => (topClients.value = d ?? [])),
    getCategoryStatApi(8).then((d) => (category.value = d ?? [])),
    getTesterWorkloadStatApi(8).then((d) => (testerWorkload.value = d ?? [])),
    getUnqualifiedItemsStatApi(8).then((d) => (unqualifiedItems.value = d ?? [])),
    getMonthlyTrendStatApi(trendMonths.value).then((d) => (trend.value = d ?? [])),
  ]
  await Promise.allSettled(tasks)
  loading.value = false
}

onMounted(load)

/** 合格率展示：null → 「暂无」（无有效结论，与 0% 语义不同） */
const rateText = computed(() => {
  const r = overview.value?.qualifiedRate
  return r === null || r === undefined ? '暂无' : r.toFixed(1)
})
const rateSuffix = computed(() =>
  overview.value?.qualifiedRate === null || overview.value?.qualifiedRate === undefined ? '' : '%',
)
const judgedTotal = computed(
  () => (overview.value?.qualifiedSamples ?? 0) + (overview.value?.unqualifiedSamples ?? 0),
)

/** KPI 行：主数值全部来自 /stat/overview */
const kpis = computed(() => {
  const o = overview.value
  return [
    {
      label: '样品总数',
      value: o?.totalSamples ?? 0,
      suffix: '份',
      icon: Document,
      iconTone: 'info' as const,
      hint: `累计登记 · 含在检与完成`,
    },
    {
      label: '合格率',
      value: rateText.value,
      suffix: rateSuffix.value,
      icon: CircleCheck,
      iconTone: 'success' as const,
      hint: judgedTotal.value > 0 ? `已判定 ${judgedTotal.value} 份（不含待判定）` : '暂无已判定样品',
    },
    {
      label: '不合格样品',
      value: o?.unqualifiedSamples ?? 0,
      suffix: '份',
      icon: CircleClose,
      iconTone: 'danger' as const,
      hint: '存在至少一项不合格',
    },
    {
      label: '待判定',
      value: o?.pendingSamples ?? 0,
      suffix: '份',
      icon: Warning,
      iconTone: 'warning' as const,
      hint: '数据缺口，需检验员补齐',
    },
    {
      label: '在检中',
      value: o?.testingSamples ?? 0,
      suffix: '份',
      icon: Timer,
      iconTone: 'purple' as const,
      hint: '已安排待录入结果',
    },
    {
      label: '已出报告',
      value: o?.reportCount ?? 0,
      suffix: '份',
      icon: DataAnalysis,
      iconTone: 'accent' as const,
      hint: '报告生成完成',
    },
  ]
})

// ---------- 图表 option（computed，随数据变化自动重绘） ----------
const statusOption = computed(() => pieOption(statusDist.value, '份'))
const inspectTypeOption = computed(() => barOption(inspectType.value, '份'))
const clientOption = computed(() => hBarOption(topClients.value, '份'))
const categoryOption = computed(() => pieOption(category.value, '项'))
const testerOption = computed(() => hBarOption(testerWorkload.value, '项'))
const unqualifiedOption = computed(() => hBarOption(unqualifiedItems.value, '次'))
const trendOptionValue = computed(() => trendOption(trend.value))
</script>

<template>
  <div class="analysis">
    <PageHeader
      title="质量分析"
      subtitle="检验业务全景统计 · 数据实时取自业务表聚合"
      :icon="DataAnalysis"
    >
      <el-button
        :loading="loading"
        :icon="Refresh"
        @click="load"
      >
        刷新数据
      </el-button>
    </PageHeader>

    <!-- ================= KPI 行 ================= -->
    <section class="kpi-row">
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

    <!-- ================= 趋势 + 状态分布 ================= -->
    <div class="grid grid--2-1">
      <AppCard padding="20px">
        <header class="card-head">
          <h3 class="card-title">
            月度检验趋势
          </h3>
          <span class="card-sub">近 {{ trendMonths }} 个月 · 无数据的月份补零</span>
        </header>
        <LimsChart
          :option="trendOptionValue"
          :height="296"
          :loading="loading"
          :empty="trend.length === 0"
          empty-title="暂无趋势数据"
          empty-hint="尚未登记样品，或所选区间内无记录"
        />
      </AppCard>

      <AppCard padding="20px">
        <header class="card-head">
          <h3 class="card-title">
            样品状态分布
          </h3>
          <span class="card-sub">按状态机阶段</span>
        </header>
        <LimsChart
          :option="statusOption"
          :height="296"
          :loading="loading"
          :empty="statusDist.length === 0"
          empty-title="暂无样品"
          empty-hint="登记样品后此处将显示各阶段分布"
        />
      </AppCard>
    </div>

    <!-- ================= 检验类别 + 样品大类 ================= -->
    <div class="grid grid--1-1">
      <AppCard padding="20px">
        <header class="card-head">
          <h3 class="card-title">
            检验类别分布
          </h3>
          <span class="card-sub">监督抽检 / 委托检验等</span>
        </header>
        <LimsChart
          :option="inspectTypeOption"
          :height="272"
          :loading="loading"
          :empty="inspectType.length === 0"
          empty-title="暂无类别数据"
        />
      </AppCard>

      <AppCard padding="20px">
        <header class="card-head">
          <h3 class="card-title">
            样品大类构成
          </h3>
          <span class="card-sub">按检测单项数统计</span>
        </header>
        <LimsChart
          :option="categoryOption"
          :height="272"
          :loading="loading"
          :empty="category.length === 0"
          empty-title="暂无大类数据"
          empty-hint="样品尚未分解检测单项时无数据"
        />
      </AppCard>
    </div>

    <!-- ================= 检验员工作量 + 送检单位 ================= -->
    <div class="grid grid--1-1">
      <AppCard padding="20px">
        <header class="card-head">
          <h3 class="card-title">
            检验员工作量
          </h3>
          <span class="card-sub">按承接检测单项数排名</span>
        </header>
        <LimsChart
          :option="testerOption"
          :height="292"
          :loading="loading"
          :empty="testerWorkload.length === 0"
          empty-title="暂无任务分配"
          empty-hint="完成检验任务安排后此处显示工作量排名"
        />
      </AppCard>

      <AppCard padding="20px">
        <header class="card-head">
          <h3 class="card-title">
            送检单位 Top 8
          </h3>
          <span class="card-sub">按登记样品数排名</span>
        </header>
        <LimsChart
          :option="clientOption"
          :height="292"
          :loading="loading"
          :empty="topClients.length === 0"
          empty-title="暂无送检单位"
        />
      </AppCard>
    </div>

    <!-- ================= 不合格项目 ================= -->
    <AppCard padding="20px">
      <header class="card-head">
        <h3 class="card-title">
          不合格项目 Top 8
        </h3>
        <span class="card-sub">按不合格次数排名 · 用于识别质量风险点</span>
      </header>
      <LimsChart
        :option="unqualifiedOption"
        :height="280"
        :loading="loading"
        :empty="unqualifiedItems.length === 0"
        empty-title="暂无不合格记录"
        empty-hint="当前所有已判定单项均合格，或尚未完成判定"
      />
    </AppCard>

    <p class="foot-note">
      <span class="foot-dot" :class="{ 'is-busy': loading }" aria-hidden="true" />
      统计口径：合格率分母不含「待判定」（待判定属数据缺口，非质量结论）；所有数据来自业务表实时聚合，无演示数据。
    </p>
  </div>
</template>

<style scoped>
.analysis {
  display: flex;
  flex-direction: column;
  gap: var(--lims-page-gap);
}

.kpi-row {
  display: grid;
  gap: var(--lims-sp-3);
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
}

.grid {
  display: grid;
  gap: var(--lims-page-gap);
}

.grid--2-1 {
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
}

.grid--1-1 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 1180px) {
  .grid--2-1,
  .grid--1-1 {
    grid-template-columns: 1fr;
  }
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: var(--lims-sp-3);
  margin-bottom: var(--lims-sp-3);
}

.card-title {
  font-size: var(--lims-fs-lg);
  font-weight: 700;
}

.card-sub {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}

.foot-note {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 var(--lims-sp-2) var(--lims-sp-2);
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
  line-height: 1.6;
}

.foot-dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lims-accent);
  box-shadow: 0 0 8px rgba(var(--lims-accent-rgb), 0.8);
}

.foot-dot.is-busy {
  animation: foot-pulse 0.9s ease-in-out infinite alternate;
}

@keyframes foot-pulse {
  from {
    opacity: 0.3;
  }

  to {
    opacity: 1;
  }
}
</style>
