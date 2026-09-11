<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { Download, Upload } from '@element-plus/icons-vue'
import {
  SAMPLE_STATUS_OPTIONS,
  SAMPLE_STATUS_TAG,
  confirmSampleApi,
  importSampleApi,
  pageSampleApi,
  updateSampleApi,
  type Sample,
  type SampleImportResult,
} from '@/api/sample'

/** 采样单导入模板（置于 frontend/public/templates，随构建产物发布） */
const TEMPLATE_URL = '/templates/sample_import_template.xlsx'

// ---------------- 查询区 ----------------
const queryRef = ref<FormInstance>()
const query = reactive({
  sampleNo: '',
  sampleName: '',
  taskNo: '',
  status: undefined as number | undefined,
})

// ---------------- 表格 ----------------
const loading = ref(false)
const tableData = ref<Sample[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const selection = ref<Sample[]>([])

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await pageSampleApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      sampleNo: query.sampleNo || undefined,
      sampleName: query.sampleName || undefined,
      taskNo: query.taskNo || undefined,
      status: query.status,
    })
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  pageNum.value = 1
  void loadList()
}

function handleReset(): void {
  query.sampleNo = ''
  query.sampleName = ''
  query.taskNo = ''
  query.status = undefined
  handleSearch()
}

function handlePageChange(p: number): void {
  pageNum.value = p
  void loadList()
}

function handleSizeChange(s: number): void {
  pageSize.value = s
  pageNum.value = 1
  void loadList()
}

function handleSelectionChange(rows: Sample[]): void {
  selection.value = rows
}

function statusLabelOf(row: Sample): string {
  return row.statusLabel ?? SAMPLE_STATUS_OPTIONS.find((o) => o.code === row.status)?.label ?? String(row.status)
}

function statusTagOf(row: Sample): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  return SAMPLE_STATUS_TAG[row.status] ?? 'info'
}

// ---------------- 导入采样单 ----------------
const importing = ref(false)
const importResult = ref<SampleImportResult | null>(null)
const importDialogVisible = ref(false)

async function handleUpload(options: UploadRequestOptions): Promise<void> {
  importing.value = true
  try {
    const res = await importSampleApi(options.file)
    importResult.value = res
    importDialogVisible.value = true
    await loadList()
  } catch {
    // 请求层已统一提示（含「文件已导入」「格式不支持」等业务错误）
  } finally {
    importing.value = false
  }
}

const importSummary = computed(() => {
  const r = importResult.value
  if (!r) return ''
  return `共解析 ${r.total} 行：成功 ${r.successCount} 行，失败 ${r.failCount} 行`
})

async function copyErrorList(): Promise<void> {
  const rows = importResult.value?.errors ?? []
  const text = rows.map((e) => `第${e.rowNum}行\t${e.sampleNo ?? ''}\t${e.message}`).join('\n')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('失败清单已复制到剪贴板')
  } catch {
    ElMessage.warning('浏览器禁止剪贴板访问，请手动选择复制')
  }
}

// ---------------- 登记确认 (S10→S20) ----------------
const confirming = ref(false)

async function handleConfirm(): Promise<void> {
  const targets = selection.value.filter((row) => row.status === 10 && row.id != null)
  if (targets.length === 0) {
    ElMessage.warning('请先勾选「已登记」状态的样品')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定对选中的 ${targets.length} 条样品进行登记确认？确认后状态由「已登记」转为「登记确认」，将进入项目分解流程。`,
      '登记确认',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  confirming.value = true
  try {
    const ids = targets.map((row) => row.id as number)
    const res = await confirmSampleApi(ids)
    ElMessage.success(`登记确认完成，共 ${res.confirmedCount} 条`)
    await loadList()
  } catch {
    // 请求层已统一提示
  } finally {
    confirming.value = false
  }
}

// ---------------- 登记信息维护 ----------------
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)

function emptyForm(): Sample {
  return {
    sampleNo: '',
    sampleName: '',
    clientName: '',
    samplingAddress: '',
    payee: '',
    sampleQuantity: '',
    projectName: '',
    samplingDate: '',
    remark: '',
    sampler: '',
    manufacturer: '',
    samplingBase: '',
    sampleState: '',
    spec: '',
    brand: '',
    grade: '',
    originalNo: '',
    inspectType: '',
    requireCompleteDate: '',
    taskNo: '',
    taskBatchNo: '',
    status: 10,
  }
}

const form = reactive<Sample>(emptyForm())

const rules: FormRules = {
  sampleNo: [{ required: true, message: '请输入样品编号', trigger: 'blur' }],
  sampleName: [{ required: true, message: '请输入样品名称', trigger: 'blur' }],
}

function canEdit(row: Sample): boolean {
  return row.status === 10
}

function openEdit(row: Sample): void {
  Object.assign(form, emptyForm(), row)
  dialogVisible.value = true
}

async function handleSubmit(): Promise<void> {
  const formInst = formRef.value
  if (!formInst) return
  try {
    await formInst.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await updateSampleApi({ ...form })
    ElMessage.success('登记信息已保存')
    dialogVisible.value = false
    await loadList()
  } catch {
    // 请求层已统一提示
  } finally {
    submitting.value = false
  }
}

// ---------------- 详情 ----------------
const detailVisible = ref(false)
const detailRow = ref<Sample | null>(null)

function openDetail(row: Sample): void {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(() => {
  void loadList()
})
</script>

<template>
  <div class="sample-page">
    <!-- 查询区 -->
    <el-card
      shadow="never"
      class="query-card"
    >
      <el-form
        ref="queryRef"
        :model="query"
        inline
        @submit.prevent
      >
        <el-form-item label="样品编号">
          <el-input
            v-model="query.sampleNo"
            placeholder="支持前缀匹配"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样品名称">
          <el-input
            v-model="query.sampleName"
            placeholder="模糊匹配"
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
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in SAMPLE_STATUS_OPTIONS"
              :key="opt.code"
              :label="opt.label"
              :value="opt.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="handleSearch"
          >
            查询
          </el-button>
          <el-button @click="handleReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 + 表格 -->
    <el-card
      shadow="never"
      class="table-card"
    >
      <div class="toolbar">
        <div class="toolbar-left">
          <el-upload
            :show-file-list="false"
            :auto-upload="true"
            accept=".xls,.xlsx"
            :http-request="handleUpload"
          >
            <el-button
              v-permission="'sample:import'"
              type="primary"
              :icon="Upload"
              :loading="importing"
            >
              导入采样单
            </el-button>
          </el-upload>
          <el-link
            :href="TEMPLATE_URL"
            target="_blank"
            type="primary"
            :underline="false"
            class="template-link"
          >
            <el-icon><Download /></el-icon>
            下载导入模板
          </el-link>
        </div>
        <div class="toolbar-right">
          <el-button
            v-permission="'sample:confirm'"
            type="success"
            :loading="confirming"
            :disabled="selection.length === 0"
            @click="handleConfirm"
          >
            批量登记确认
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          type="selection"
          width="46"
          reserve-selection
        />
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          min-width="160"
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
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="samplingAddress"
          label="抽样地址"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="sampler"
          label="采样者"
          width="100"
        />
        <el-table-column
          prop="samplingDate"
          label="采样日期"
          width="120"
        />
        <el-table-column
          prop="taskNo"
          label="任务编号"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          label="状态"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="statusTagOf(row as Sample)">
              {{ statusLabelOf(row as Sample) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="140"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-if="canEdit(row as Sample)"
              v-permission="'sample:import'"
              link
              type="primary"
              @click="openEdit(row as Sample)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="primary"
              @click="openDetail(row as Sample)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无样品，请先导入采样单 Excel" />
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          :current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 导入结果对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="采样单导入结果"
      width="720px"
    >
      <el-alert
        :title="importSummary"
        :type="(importResult?.failCount ?? 0) > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
      />
      <div
        v-if="(importResult?.errors?.length ?? 0) > 0"
        class="error-block"
      >
        <div class="error-head">
          <span>失败明细（合法行已入库，错误行可修正后重导）</span>
          <el-button
            link
            type="primary"
            @click="copyErrorList"
          >
            复制失败清单
          </el-button>
        </div>
        <el-table
          :data="importResult?.errors ?? []"
          border
          max-height="320"
          size="small"
        >
          <el-table-column
            prop="rowNum"
            label="行号"
            width="80"
            align="center"
          />
          <el-table-column
            prop="sampleNo"
            label="样品编号"
            width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="message"
            label="失败原因"
            min-width="320"
            show-overflow-tooltip
          />
        </el-table>
      </div>
      <template #footer>
        <el-button
          type="primary"
          @click="importDialogVisible = false"
        >
          知道了
        </el-button>
      </template>
    </el-dialog>

    <!-- 登记信息维护对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="登记信息维护"
      width="760px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="样品编号"
              prop="sampleNo"
            >
              <el-input v-model="form.sampleNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="样品名称"
              prop="sampleName"
            >
              <el-input v-model="form.sampleName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="受检单位">
              <el-input v-model="form.clientName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="抽样地址">
              <el-input v-model="form.samplingAddress" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采样者">
              <el-input v-model="form.sampler" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采样日期">
              <el-date-picker
                v-model="form.samplingDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="样品数量">
              <el-input v-model="form.sampleQuantity" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="样品状态">
              <el-input v-model="form.sampleState" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目名称">
              <el-input v-model="form.projectName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验类别">
              <el-input v-model="form.inspectType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产单位">
              <el-input v-model="form.manufacturer" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="抽样基数">
              <el-input v-model="form.samplingBase" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格型号">
              <el-input v-model="form.spec" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商标">
              <el-input v-model="form.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="样品等级">
              <el-input v-model="form.grade" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原编号/生产日期">
              <el-input v-model="form.originalNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务编号">
              <el-input v-model="form.taskNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务批号">
              <el-input v-model="form.taskBatchNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="要求完成日期">
              <el-date-picker
                v-model="form.requireCompleteDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="2"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="样品详情"
      width="720px"
    >
      <el-descriptions
        v-if="detailRow"
        :column="2"
        border
      >
        <el-descriptions-item label="样品编号">
          {{ detailRow.sampleNo }}
        </el-descriptions-item>
        <el-descriptions-item label="样品名称">
          {{ detailRow.sampleName }}
        </el-descriptions-item>
        <el-descriptions-item label="受检单位">
          {{ detailRow.clientName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="抽样地址">
          {{ detailRow.samplingAddress || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="采样者">
          {{ detailRow.sampler || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="采样日期">
          {{ detailRow.samplingDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="样品数量">
          {{ detailRow.sampleQuantity || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="样品状态">
          {{ detailRow.sampleState || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="生产单位">
          {{ detailRow.manufacturer || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="检验类别">
          {{ detailRow.inspectType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="任务编号">
          {{ detailRow.taskNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="任务批号">
          {{ detailRow.taskBatchNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTagOf(detailRow)">
            {{ statusLabelOf(detailRow) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="登记确认">
          {{ detailRow.confirmedAt ? `${detailRow.confirmedBy ?? ''} ${detailRow.confirmedAt}` : '未确认' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="备注"
          :span="2"
        >
          {{ detailRow.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button
          type="primary"
          @click="detailVisible = false"
        >
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sample-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.query-card :deep(.el-card__body) {
  padding-bottom: 2px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.template-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.error-block {
  margin-top: 12px;
}

.error-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--lims-muted);
}
</style>
