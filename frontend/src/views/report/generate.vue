<script setup lang="ts">
/**
 * 报告生成列表页（T-702，api-spec 8.2）。
 * ----------------------------------------------------------------------------
 * 收录 status ∈ {S80 已签发, S90 已出报告} 的样品：
 *   - S80（reportGeneratedAt 为空）→ 「生成报告」：选择 CMA / CMA-CATL → 二次确认
 *     → generateReportApi → 成功后跳打印页；
 *   - S90（reportGeneratedAt 非空）→ 「重打印」：仅跳打印页，不再调 generate。
 * 筛选维度含**任务编号**（业务说明书要求可按任务编号筛选）。
 *
 * 打印页组件（print.vue）负责白底 A4 渲染与 window.print()，本页不涉及打印版式。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Printer, Refresh, Search } from '@element-plus/icons-vue'
import {
  REPORT_TYPE_OPTIONS,
  generateReportApi,
  pageReportPendingApi,
  type ReportPendingRow,
  type ReportTypeCode,
} from '@/api/report'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'
import DataTable from '@/components/common/DataTable.vue'
import { askConfirm } from '@/utils/confirm'
import { sampleStatusInfo, type SampleTone } from '@/utils/sampleStatus'

const router = useRouter()

const loading = ref(false)
const tableData = ref<ReportPendingRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const query = reactive({ sampleNo: '', sampleName: '', taskNo: '' })

/** 整体结论 code → 徽章色调 */
function conclusionTone(code?: number | null): 'success' | 'danger' | 'pending' | 'neutral' {
  if (code === 1) return 'success'
  if (code === 2) return 'danger'
  if (code === 3) return 'pending'
  return 'neutral'
}

/** 样品状态 code → 徽章色调（与全局样品状态口径一致） */
function statusTone(row: ReportPendingRow): SampleTone {
  return sampleStatusInfo(row.status).tone
}

/** el-table 作用域插槽 row 为 EP 的宽松类型 → 收窄为强类型（避免 TS2345） */
function rowItem(row: unknown): ReportPendingRow {
  return row as ReportPendingRow
}

const listError = ref('')
let listRequest = 0

async function load(): Promise<void> {
  const request = ++listRequest
  listError.value = ''
  loading.value = true
  try {
    const res = await pageReportPendingApi({
      current: current.value,
      size: size.value,
      sampleNo: query.sampleNo || undefined,
      sampleName: query.sampleName || undefined,
      taskNo: query.taskNo || undefined,
    })
    if (request !== listRequest) return
    tableData.value = res.records
    total.value = res.total
  } catch {
    if (request === listRequest) listError.value = '未能读取列表，请检查网络或权限后重试。'
  } finally {
    if (request === listRequest) loading.value = false
  }
}

function handleSearch(): void {
  current.value = 1
  void load()
}

function handleReset(): void {
  query.sampleNo = ''
  query.sampleName = ''
  query.taskNo = ''
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

// ---------------- 生成报告弹窗 ----------------
const genVisible = ref(false)
const genRow = ref<ReportPendingRow | null>(null)
const genType = ref<ReportTypeCode>(1)
const acting = ref(false)

function typeLabel(code: ReportTypeCode): string {
  return REPORT_TYPE_OPTIONS.find((o) => o.value === code)?.label ?? `类型${code}`
}

function openGenerate(row: ReportPendingRow): void {
  genRow.value = row
  genType.value = 1
  genVisible.value = true
}

async function submitGenerate(): Promise<void> {
  const row = genRow.value
  if (!row || acting.value) return
  const ok = await askConfirm(
    `确认为样品「${row.sampleNo}」生成「${typeLabel(genType.value)}」？生成后样品将流转为「已出报告」。`,
    '生成报告',
    { type: 'primary' },
  )
  if (!ok) return
  acting.value = true
  try {
    await generateReportApi(row.sampleNo, genType.value)
    ElMessage.success('报告生成成功')
    genVisible.value = false
    await load()
    void router.push({
      name: 'report-print',
      query: { sampleNo: row.sampleNo, reportType: genType.value },
    })
  } catch {
    // 请求层已统一提示
  } finally {
    acting.value = false
  }
}

/** 重打印：直接跳打印页，不再次生成（不改状态） */
function handleReprint(row: ReportPendingRow): void {
  const type: ReportTypeCode = row.reportType === 2 ? 2 : 1
  void router.push({ name: 'report-print', query: { sampleNo: row.sampleNo, reportType: type } })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="报告生成"
      subtitle="按资质选择生成 CMA / CMA-CATL 检验报告并打印"
      icon="Printer"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>实验室业务</el-breadcrumb-item>
          <el-breadcrumb-item>报告生成</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-form
        inline
        class="filter-form"
      >
        <DataFilter>
          <el-form-item label="样品编号">
            <el-input
              v-model="query.sampleNo"
              placeholder="支持模糊查询"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="样品名称">
            <el-input
              v-model="query.sampleName"
              placeholder="支持模糊查询"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="任务编号">
            <el-input
              v-model="query.taskNo"
              placeholder="精确匹配"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <template #actions>
            <el-button
              type="primary"
              :icon="Search"
              :disabled="loading"
              @click="handleSearch"
            >
              查询
            </el-button>
            <el-button
              :icon="Refresh"
              :disabled="loading"
              @click="handleReset"
            >
              重置
            </el-button>
          </template>
        </DataFilter>
      </el-form>

      <DataTable
        :rows="tableData"
        :loading="loading"
        :error="listError"
        :current="current"
        :page-size="size"
        :total="total"
        keep-mounted
        @retry="load"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      >
        <el-table
          :data="tableData"
          stripe
          border
        >
          <el-table-column
            prop="sampleNo"
            label="样品编号"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="sampleName"
            label="样品名称"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="clientName"
            label="受检单位"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="taskNo"
            label="任务编号"
            min-width="130"
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
            label="采样日期"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              {{ row.samplingDate ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="检测单项"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              {{ row.itemTotal }} 项
            </template>
          </el-table-column>
          <el-table-column
            label="整体结论"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              <StatusBadge
                :tone="conclusionTone(row.conclusion)"
                size="sm"
              >
                {{ row.conclusionLabel ?? '—' }}
              </StatusBadge>
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              <StatusBadge
                :tone="statusTone(rowItem(row))"
                size="sm"
              >
                {{ row.statusLabel }}
              </StatusBadge>
            </template>
          </el-table-column>
          <el-table-column
            label="报告类型"
            width="130"
            align="center"
          >
            <template #default="{ row }">
              {{ row.reportTypeLabel ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="reportGeneratedAt"
            label="生成时间"
            width="170"
          >
            <template #default="{ row }">
              {{ row.reportGeneratedAt ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="130"
            fixed="right"
            align="center"
          >
            <template #default="{ row }">
              <el-button
                v-if="row.reportGeneratedAt"
                type="primary"
                link
                :icon="Printer"
                @click="handleReprint(rowItem(row))"
              >
                重打印
              </el-button>
              <el-button
                v-else
                type="primary"
                link
                @click="openGenerate(rowItem(row))"
              >
                生成报告
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <AppEmpty
              title="暂无可生成报告的样品"
              hint="需先在「报告审核签发」完成签发（S80），样品才会出现在此列表"
            />
          </template>
        </el-table>
      </DataTable>
    </AppCard>

    <!-- 生成报告：选择报告类型 -->
    <el-dialog
      v-model="genVisible"
      title="生成检验报告"
      width="440px"
    >
      <div
        v-if="genRow"
        class="gen-body"
      >
        <p class="gen-tip">
          样品：{{ genRow.sampleNo }}（{{ genRow.sampleName ?? '—' }}）
        </p>
        <p class="gen-label">
          请选择报告类型：
        </p>
        <el-radio-group v-model="genType">
          <el-radio
            v-for="opt in REPORT_TYPE_OPTIONS"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="genVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="acting"
          @click="submitGenerate"
        >
          生成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
}
.filter-form {
  margin-bottom: var(--lims-sp-4);
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}
.gen-body {
  padding: 0 4px;
}
.gen-tip {
  margin: 0 0 12px;
  color: var(--lims-text-secondary);
  font-size: 13px;
}
.gen-label {
  margin: 0 0 8px;
  font-size: 13px;
}
</style>
