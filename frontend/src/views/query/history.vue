<script setup lang="ts">
/**
 * 历史样品查询页（T-801 A2，GET /api/query/history/page）。
 *
 * <p>列出已签发 / 已出报告（status 80/90）的历史样品，可按整体结论、是否已生成报告筛选，
 * 展示审核 / 签发 / 报告信息。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { SAMPLE_STATUS_OPTIONS } from '@/api/sample'
import { CONCLUSION_OPTIONS } from '@/api/result'
import { pageHistoryQueryApi, type HistoryQueryParams, type HistoryQueryRow } from '@/api/query'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'purple' | 'pending' | 'neutral'

/** 历史样品可选状态（80 已签发 / 90 已出报告） */
const HISTORY_STATUS_OPTIONS = SAMPLE_STATUS_OPTIONS.filter((o) => o.code >= 80)

const query = reactive({
  sampleNo: '',
  sampleName: '',
  clientName: '',
  taskNo: '',
  status: undefined as number | undefined,
  conclusion: undefined as number | undefined,
  reportGenerated: undefined as boolean | undefined,
})
const dateRange = ref<string[]>([])

const loading = ref(false)
const tableData = ref<HistoryQueryRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

function conclusionTone(code?: number | null): Tone {
  if (code === 1) return 'success'
  if (code === 2) return 'danger'
  if (code === 3) return 'pending'
  return 'neutral'
}

function rowItem(row: unknown): HistoryQueryRow {
  return row as HistoryQueryRow
}

function buildParams(): HistoryQueryParams {
  const params: HistoryQueryParams = {
    current: current.value,
    size: size.value,
  }
  if (query.sampleNo) params.sampleNo = query.sampleNo
  if (query.sampleName) params.sampleName = query.sampleName
  if (query.clientName) params.clientName = query.clientName
  if (query.taskNo) params.taskNo = query.taskNo
  if (query.status != null) params.status = query.status
  if (query.conclusion != null) params.conclusion = query.conclusion
  if (query.reportGenerated != null) params.reportGenerated = query.reportGenerated
  if (dateRange.value.length === 2) {
    params.samplingDateFrom = dateRange.value[0]
    params.samplingDateTo = dateRange.value[1]
  }
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageHistoryQueryApi(buildParams())
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  current.value = 1
  void load()
}

function handleReset(): void {
  query.sampleNo = ''
  query.sampleName = ''
  query.clientName = ''
  query.taskNo = ''
  query.status = undefined
  query.conclusion = undefined
  query.reportGenerated = undefined
  dateRange.value = []
  handleSearch()
}

function handlePageChange(p: number): void {
  current.value = p
  void load()
}

function handleSizeChange(s: number): void {
  size.value = s
  current.value = 1
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="历史样品"
      subtitle="已签发 / 已出报告样品的历史归档查询"
      icon="Search"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>查询统计</el-breadcrumb-item>
          <el-breadcrumb-item>历史样品</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </PageHeader>

    <DataFilter>
      <el-form inline>
        <el-form-item label="样品编号">
          <el-input
            v-model="query.sampleNo"
            placeholder="前缀匹配"
            clearable
            style="width: 170px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样品名称">
          <el-input
            v-model="query.sampleName"
            placeholder="模糊查询"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="受检单位">
          <el-input
            v-model="query.clientName"
            placeholder="模糊查询"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="任务编号">
          <el-input
            v-model="query.taskNo"
            placeholder="精确匹配"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option
              v-for="opt in HISTORY_STATUS_OPTIONS"
              :key="opt.code"
              :value="opt.code"
              :label="opt.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="整体结论">
          <el-select
            v-model="query.conclusion"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="opt in CONCLUSION_OPTIONS"
              :key="opt.value"
              :value="opt.value"
              :label="opt.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报告">
          <el-select
            v-model="query.reportGenerated"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              :value="true"
              label="已生成"
            />
            <el-option
              :value="false"
              label="未生成"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="抽样日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
      </el-form>
      <template #actions>
        <el-button
          type="primary"
          :icon="Search"
          @click="handleSearch"
        >
          查询
        </el-button>
        <el-button
          :icon="Refresh"
          @click="handleReset"
        >
          重置
        </el-button>
      </template>
    </DataFilter>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
      >
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          min-width="160"
          show-overflow-tooltip
          fixed="left"
        />
        <el-table-column
          prop="sampleName"
          label="样品名称"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="clientName"
          label="受检单位"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="taskNo"
          label="任务编号"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="samplingDate"
          label="抽样日期"
          width="110"
          align="center"
        />
        <el-table-column
          label="状态"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              tone="success"
              size="sm"
            >
              {{ rowItem(row).statusLabel ?? '—' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="整体结论"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="conclusionTone(rowItem(row).conclusion)"
              size="sm"
            >
              {{ rowItem(row).conclusionLabel ?? '—' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="项数"
          width="70"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).itemTotal }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="待录入"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :class="{ 'abnormal': rowItem(row).blankCount > 0 }">
              {{ rowItem(row).blankCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="待判定"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :class="{ 'abnormal': rowItem(row).pendingCount > 0 }">
              {{ rowItem(row).pendingCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="异常项"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :class="{ 'abnormal': rowItem(row).abnormalCount > 0 }">{{ rowItem(row).abnormalCount }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="审核人"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).auditBy ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="审核时间"
          width="170"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).auditAt ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="签发人"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).signBy ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="报告类型"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).reportTypeLabel ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="报告生成时间"
          width="170"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).reportGeneratedAt ?? '—' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无历史样品"
            hint="当前筛选条件下没有已签发 / 已出报告的样品"
          />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </AppCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}
.abnormal {
  color: var(--lims-danger);
  font-weight: 600;
}
</style>
