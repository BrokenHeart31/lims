<script setup lang="ts">
/**
 * 在检样品查询页（T-801 A1，GET /api/query/testing/page）。
 *
 * <p>列出未出报告（status 10..70）的在检样品，带出检测进度、整体结论、当前处理人与
 * 当前阶段停留时长；只读列表，无写入操作。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { SAMPLE_STATUS_OPTIONS } from '@/api/sample'
import { pageTestingQueryApi, type TestingQueryParams, type TestingQueryRow } from '@/api/query'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'purple' | 'pending' | 'neutral'

/** 在检样品可选状态（10..70，排除已签发/已出报告） */
const INSPECTING_STATUS_OPTIONS = SAMPLE_STATUS_OPTIONS.filter((o) => o.code <= 70)

const query = reactive({
  sampleNo: '',
  sampleName: '',
  clientName: '',
  taskNo: '',
  status: undefined as number | undefined,
})
const dateRange = ref<string[]>([])

const loading = ref(false)
const tableData = ref<TestingQueryRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

function statusTone(code: number): Tone {
  if (code <= 30) return 'info'
  if (code === 40 || code === 50) return 'pending'
  if (code === 60) return 'success'
  if (code === 70) return 'purple'
  return 'success'
}

function conclusionTone(code?: number | null): Tone {
  if (code === 1) return 'success'
  if (code === 2) return 'danger'
  if (code === 3) return 'pending'
  return 'neutral'
}

/** el-table 插槽 row 为宽松类型，传入强类型函数前统一收窄 */
function rowItem(row: unknown): TestingQueryRow {
  return row as TestingQueryRow
}

function buildParams(): TestingQueryParams {
  const params: TestingQueryParams = {
    current: current.value,
    size: size.value,
  }
  if (query.sampleNo) params.sampleNo = query.sampleNo
  if (query.sampleName) params.sampleName = query.sampleName
  if (query.clientName) params.clientName = query.clientName
  if (query.taskNo) params.taskNo = query.taskNo
  if (query.status != null) params.status = query.status
  if (dateRange.value.length === 2) {
    params.samplingDateFrom = dateRange.value[0]
    params.samplingDateTo = dateRange.value[1]
  }
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageTestingQueryApi(buildParams())
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
      title="在检样品"
      subtitle="未出报告样品（已登记 → 已审核）的检测进度、处理人与停留时长"
      icon="Search"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>查询统计</el-breadcrumb-item>
          <el-breadcrumb-item>在检样品</el-breadcrumb-item>
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
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样品名称">
          <el-input
            v-model="query.sampleName"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="受检单位">
          <el-input
            v-model="query.clientName"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="任务编号">
          <el-input
            v-model="query.taskNo"
            placeholder="精确匹配"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in INSPECTING_STATUS_OPTIONS"
              :key="opt.code"
              :value="opt.code"
              :label="opt.label"
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
          prop="inspectType"
          label="检验类别"
          width="110"
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
              :tone="statusTone(rowItem(row).status)"
              size="sm"
            >
              {{ rowItem(row).statusLabel ?? '—' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="检测进度"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).enteredCount }} / {{ rowItem(row).itemTotal }}</span>
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
            <span :class="{ 'abnormal': rowItem(row).abnormalCount > 0 }">
              {{ rowItem(row).abnormalCount }}
            </span>
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
          label="当前处理人"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span>{{ rowItem(row).currentHandler ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="停留时长"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <span v-if="rowItem(row).stageStayHours != null">{{ rowItem(row).stageStayHours }} 小时</span>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="updatedAt"
          label="更新时间"
          width="170"
          align="center"
          show-overflow-tooltip
        />
        <template #empty>
          <AppEmpty
            title="暂无在检样品"
            hint="当前筛选条件下没有未出报告的样品"
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
.muted {
  color: var(--lims-text-secondary);
}
</style>
