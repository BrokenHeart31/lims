<script setup lang="ts">
/**
 * 检验员任务查询页（T-603，说明书第七节）
 *
 * <p>说明书原文：「检验员查询到安排给自己的全部检验任务，可下载该任务的 Excel 文档。」</p>
 *
 * <p><b>数据范围</b>：非 R100 角色只能看到指派给自己的任务（后端按登录身份强制收敛，
 * 前端即便改请求参数也无效）。R100 综合管理可查看全部。</p>
 *
 * <p><b>与结果录入的关系</b>：本页是「先看清单再进去录」的前置步骤——
 * 「未录入」筛选开关（onlyUnentered）让检验员一眼看清还有哪些项没录，
 * 已录入/未录入判定复用后端唯一口径，前端不自行计算。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Download, EditPen, Refresh, Search } from '@element-plus/icons-vue'
import { get } from '@/utils/request'
import type { PageResult } from '@/types/api'
import { exportMyTasksApi } from '@/api/exportApi'
import { downloadBlob } from '@/utils/download'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import { ElMessage } from 'element-plus'

/** 检验员任务行（与后端 MyTaskVO 对应） */
interface MyTaskRow {
  itemId: number
  sampleId: number
  sampleNo: string
  sampleName?: string | null
  clientName?: string | null
  taskNo?: string | null
  itemOrder: number
  itemName: string
  methods?: string | null
  basisCode?: string | null
  stdValue?: string | null
  unit?: string | null
  lowerLimit?: string | null
  judgeType: number
  sampleState?: string | null
  sampleStatus: number
  sampleStatusLabel?: string | null
  entered?: boolean | null
  conclusion?: number | null
  conclusionLabel?: string | null
  testValue?: string | null
}

const router = useRouter()

const query = reactive({
  sampleNo: '',
  sampleName: '',
  clientName: '',
  taskNo: '',
  onlyUnentered: false,
})

const loading = ref(false)
const exporting = ref(false)
const tableData = ref<MyTaskRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

/** 当前页未录入项数（给检验员一个即时的工作量提示） */
const pageUnentered = computed(() => tableData.value.filter((r) => !r.entered).length)

function buildParams(): Record<string, unknown> {
  const params: Record<string, unknown> = { current: current.value, size: size.value }
  if (query.sampleNo) params.sampleNo = query.sampleNo
  if (query.sampleName) params.sampleName = query.sampleName
  if (query.clientName) params.clientName = query.clientName
  if (query.taskNo) params.taskNo = query.taskNo
  if (query.onlyUnentered) params.onlyUnentered = true
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await get<PageResult<MyTaskRow>>('/query/my-tasks/page', buildParams())
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
  query.onlyUnentered = false
  handleSearch()
}

/** 跳到结果录入页：带上样品编号定位（录入页按 sampleNo 检索） */
function gotoEntry(row: MyTaskRow): void {
  void router.push({ path: '/result/entry', query: { sampleNo: row.sampleNo } })
}

async function handleExport(): Promise<void> {
  exporting.value = true
  try {
    const result = await exportMyTasksApi()
    downloadBlob(result)
    ElMessage.success('检验任务已导出')
  } catch {
    // 请求层已统一提示
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="我的检验任务"
      subtitle="查看安排给自己的检验任务，可导出 Excel 或直接进入录入"
      icon="EditPen"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>实验室业务</el-breadcrumb-item>
          <el-breadcrumb-item>我的检验任务</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        v-permission="'result:export-excel'"
        type="primary"
        :icon="Download"
        :loading="exporting"
        @click="handleExport"
      >
        下载任务 Excel
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="20"
    >
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
            style="width: 170px"
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
        <el-form-item>
          <el-checkbox v-model="query.onlyUnentered">
            只看未录入
          </el-checkbox>
        </el-form-item>
        <el-form-item>
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
        </el-form-item>
      </el-form>
    </AppCard>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <div class="summary-bar">
        <span class="summary-item">
          共 <b>{{ total }}</b> 条任务
        </span>
        <span
          v-if="pageUnentered > 0"
          class="summary-item warn"
        >
          本页有 <b>{{ pageUnentered }}</b> 项尚未录入
        </span>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
      >
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          width="160"
          show-overflow-tooltip
          fixed="left"
        />
        <el-table-column
          prop="sampleName"
          label="样品名称"
          min-width="130"
          show-overflow-tooltip
        />
        <el-table-column
          prop="clientName"
          label="受检单位"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          prop="itemOrder"
          label="项次"
          width="70"
          align="center"
        />
        <el-table-column
          label="检验项目"
          min-width="170"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span>{{ row.itemName }}</span>
            <StatusBadge
              v-if="row.judgeType === 3"
              tone="warning"
              size="sm"
              class="tag"
            >
              人工判定
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          prop="basisCode"
          label="检测依据"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="标准值"
          width="110"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span>{{ row.stdValue ?? '—' }}</span>
            <span
              v-if="row.unit"
              class="unit"
            >{{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="录入状态"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="row.entered ? 'success' : 'blank'"
              size="sm"
            >
              {{ row.entered ? '已录入' : '未录入' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="结论"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              v-if="row.conclusionLabel"
              :tone="row.conclusion === 2 ? 'danger' : row.conclusion === 1 ? 'success' : 'pending'"
              size="sm"
            >
              {{ row.conclusionLabel }}
            </StatusBadge>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          label="样品状态"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <span class="muted">{{ row.sampleStatusLabel ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="110"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="EditPen"
              @click="gotoEntry(row as MyTaskRow)"
            >
              去录入
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无检验任务"
            hint="任务安排确认后，指派给你的检测单项会出现在这里"
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
        @current-change="load"
        @size-change="handleSearch"
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
.summary-bar {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--lims-text-secondary);
}
.summary-item b {
  color: var(--lims-text-primary);
  font-variant-numeric: tabular-nums;
}
.summary-item.warn b {
  color: var(--lims-warning, #d97706);
}
.tag {
  margin-left: 6px;
}
.unit {
  margin-left: 4px;
  font-size: 12px;
  color: var(--lims-text-secondary);
}
.muted {
  color: var(--lims-text-secondary);
}
</style>
