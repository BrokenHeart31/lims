<script setup lang="ts">
/**
 * 流程回溯查询页（feature B，设计 §5.5）——按样品编号查全链路事件。
 *
 * 三段：① 样品检索（编号 → id）；② 流程回溯时间线（正向 + 逆向，复用 RollbackTimeline）；
 * ③ 回退记录（B5，跨样品分页）+「回退」发起入口。
 *
 * 权限：页面 `rollback:view`；回退入口由 RollbackEntryButton 内部按 `rollback:execute` 显隐。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { pageSampleApi } from '@/api/sample'
import {
  getRollbackTimelineApi,
  pageRollbackHistoryApi,
  recoverRollbackApi,
} from '@/api/rollback'
import type { RollbackHistoryVO, RollbackTimelineVO } from '@/types/rollback'
import { confirm } from '@/utils/confirm'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import RollbackTimeline from '@/components/rollback/RollbackTimeline.vue'
import RollbackEntryButton from '@/components/rollback/RollbackEntryButton.vue'

// ---------------- 样品检索 ----------------
const sampleNoInput = ref('')
const resolving = ref(false)
const sampleId = ref<number | null>(null)
const timeline = ref<RollbackTimelineVO | null>(null)
const timelineLoading = ref(false)

async function resolveAndLoad(): Promise<void> {
  const kw = sampleNoInput.value.trim()
  if (!kw) {
    ElMessage.warning('请输入样品编号')
    return
  }
  resolving.value = true
  try {
    const page = await pageSampleApi({ pageNum: 1, pageSize: 10, sampleNo: kw })
    if (page.records.length === 0) {
      sampleId.value = null
      timeline.value = null
      ElMessage.warning(`未找到样品编号「${kw}」`)
      return
    }
    sampleId.value = page.records[0].id ?? null
    await loadTimeline()
  } catch {
    // 请求层已统一提示
  } finally {
    resolving.value = false
  }
}

async function loadTimeline(): Promise<void> {
  if (sampleId.value == null) return
  timelineLoading.value = true
  try {
    timeline.value = await getRollbackTimelineApi(sampleId.value)
  } catch {
    timeline.value = null
  } finally {
    timelineLoading.value = false
  }
}

/** 回退完成后刷新时间线 + 历史 */
async function onRollbackDone(): Promise<void> {
  await loadTimeline()
  await loadHistory()
}

/** 恢复某次回退 */
async function handleRecover(rollbackId: number): Promise<void> {
  const reason = await confirm({
    title: '恢复回退',
    message: '恢复将把该次回退失效的下游数据复原（仅未被后续操作覆盖时可恢复）。请填写恢复原因：',
    tone: 'warning',
    confirmText: '确认恢复',
    input: true,
    inputPlaceholder: '恢复原因（选填）',
  })
  if (reason === null) return
  try {
    await recoverRollbackApi({ rollbackId, reason: reason === '__ok__' ? undefined : reason })
    ElMessage.success('已恢复')
    await onRollbackDone()
  } catch {
    // 请求层已统一提示（如 4107 不可恢复）
  }
}

// ---------------- 回退记录（B5） ----------------
const history = ref<RollbackHistoryVO[]>([])
const historyLoading = ref(false)
const historyTotal = ref(0)
const historyQuery = ref({ current: 1, size: 10, sampleNo: '' })

async function loadHistory(): Promise<void> {
  historyLoading.value = true
  try {
    const page = await pageRollbackHistoryApi(historyQuery.value.current, historyQuery.value.size, {
      sampleNo: historyQuery.value.sampleNo || undefined,
    })
    history.value = page.records
    historyTotal.value = page.total
  } catch {
    history.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

function handleHistorySearch(): void {
  historyQuery.value.current = 1
  void loadHistory()
}

function handleHistoryPage(p: number): void {
  historyQuery.value.current = p
  void loadHistory()
}

const hasTimeline = computed(() => timeline.value !== null)

onMounted(() => {
  void loadHistory()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="流程回溯"
      subtitle="查看样品全链路状态事件，并对可回退的样品发起逐级回退"
      icon="Histogram"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>流程回溯</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        :loading="timelineLoading"
        @click="loadTimeline"
      >
        刷新
      </el-button>
    </PageHeader>

    <!-- 样品检索 -->
    <DataFilter>
      <el-form-item label="样品编号">
        <el-input
          v-model="sampleNoInput"
          placeholder="完整或部分样品编号"
          clearable
          style="width: 260px"
          @keyup.enter="resolveAndLoad"
        />
      </el-form-item>
      <template #actions>
        <el-button
          type="primary"
          :icon="Search"
          :loading="resolving"
          @click="resolveAndLoad"
        >
          查询
        </el-button>
      </template>
    </DataFilter>

    <!-- 时间线 -->
    <template v-if="hasTimeline">
      <div class="page__toolbar">
        <span class="page__sample">样品：{{ timeline?.sampleNo }}</span>
        <RollbackEntryButton
          :sample-id="sampleId as number"
          :sample-no="timeline?.sampleNo"
          label="发起回退"
          :link="false"
          size="default"
          @done="onRollbackDone"
        />
      </div>
      <div v-loading="timelineLoading">
        <RollbackTimeline
          v-if="timeline"
          :timeline="timeline"
          @recover="handleRecover"
        />
      </div>
    </template>
    <AppCard
      v-else
      variant="panel"
      :padding="16"
    >
      <AppEmpty
        title="请先查询样品"
        hint="输入样品编号并查询，即可查看该样品从登记到当前状态的全部正向/逆向事件"
      />
    </AppCard>

    <!-- 回退记录（跨样品） -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <div class="page__card-head">
        <h3 class="page__section-title">
          回退记录
        </h3>
        <div class="page__card-filter">
          <el-input
            v-model="historyQuery.sampleNo"
            placeholder="按样品编号筛选"
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="handleHistorySearch"
          />
          <el-button
            size="small"
            type="primary"
            :icon="Search"
            @click="handleHistorySearch"
          >
            查询
          </el-button>
        </div>
      </div>
      <el-table
        v-loading="historyLoading"
        :data="history"
        stripe
        border
      >
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          label="回退方向"
          min-width="190"
        >
          <template #default="{ row }">
            {{ row.fromStatusLabel }}
            <span class="page__arrow">→</span>
            {{ row.toStatusLabel }}
          </template>
        </el-table-column>
        <el-table-column
          label="分组"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="row.edgeGroup === 2 ? 'warning' : 'info'"
              size="sm"
            >
              {{ row.edgeGroupLabel ?? '常规' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          prop="reason"
          label="原因"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          label="下游失效"
          width="150"
          align="center"
        >
          <template #default="{ row }">
            单项 {{ row.affectedItemCount ?? 0 }} / 结果 {{ row.affectedResultCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作人 / 时间"
          width="190"
        >
          <template #default="{ row }">
            <div class="page__op">
              <span>{{ row.operatedBy ?? '—' }}</span>
              <span class="page__time">{{ row.operatedAt ?? '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              v-if="row.recovered === 1"
              tone="success"
              size="sm"
            >
              已恢复
            </StatusBadge>
            <el-button
              v-else-if="row.canRecover === 1"
              link
              type="primary"
              size="small"
              @click="handleRecover(row.id)"
            >
              恢复
            </el-button>
            <span
              v-else
              class="page__muted"
            >不可再撤销</span>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty title="暂无回退记录" />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="historyQuery.current"
        v-model:page-size="historyQuery.size"
        :total="historyTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="page__pager"
        @current-change="handleHistoryPage"
        @size-change="handleHistorySearch"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
}

.page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-3);
}

.page__sample {
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-sm);
}

.page__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-3);
  margin-bottom: var(--lims-sp-3);
}

.page__section-title {
  margin: 0;
  color: var(--lims-ink);
  font-size: var(--lims-fs-base);
  font-weight: 600;
}

.page__card-filter {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page__arrow {
  margin: 0 6px;
  color: var(--lims-faint);
}

.page__op {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.page__time {
  color: var(--lims-faint);
  font-size: 11px;
}

.page__muted {
  color: var(--lims-muted);
  font-size: 12px;
}

.page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-sp-3);
}
</style>
