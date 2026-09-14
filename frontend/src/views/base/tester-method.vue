<script setup lang="ts">
/**
 * 方法-检验员资质维护页（T-105，说明书第二(2)节）
 *
 * <p>业务定位：说明书写明「选择检验方法，添加检验员，设置该检验员对该方法的资质」。
 * 本页是该配置的维护界面——它是 T-501 任务自动分配的**第三级规则**（方法资质规则）的数据源：
 * 前两级（部门匹配 + 项目负载均衡）不需要这张表，但第三级要求「候选人必须对该项检验方法有有效资质」。
 * 该表此前为 0 行，导致第三级规则永远落空，本页 + Excel 导入即为补齐该数据缺口。</p>
 *
 * <p><b>Excel 导入的行内校验</b>：工号必须存在于 sys_user（后端校验），否则该行报错跳过。
 * 原因：tester_method.tester_no 是「给谁分配任务」的检索键，写进不存在的工号会让
 * T-501 匹配出幽灵候选人，比直接报错更难排查。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  createTesterMethodApi,
  importTesterMethodApi,
  pageTesterMethodApi,
  removeTesterMethodApi,
  updateTesterMethodApi,
  type TesterMethodImportResult,
  type TesterMethodParams,
  type TesterMethodRow,
  type TesterMethodSaveBody,
} from '@/api/base'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'
import DataTable from '@/components/common/DataTable.vue'
import { askConfirm } from '@/utils/confirm'

const query = reactive({
  methodName: '',
  methodNo: '',
  testerNo: '',
  qualStatus: undefined as number | undefined,
})

const loading = ref(false)
const tableData = ref<TesterMethodRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

// ---------------- 编辑弹窗 ----------------
const dialogVisible = ref(false)
const dialogSubmitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<TesterMethodSaveBody>({
  methodName: '',
  methodNo: '',
  testerNo: '',
  qualStatus: 1,
  remark: '',
})

const rules: FormRules = {
  methodName: [{ required: true, message: '请填写检验方法名称', trigger: 'blur' }],
  testerNo: [{ required: true, message: '请填写检验员工号', trigger: 'blur' }],
  qualStatus: [{ required: true, message: '请选择资质状态', trigger: 'change' }],
}

const dialogTitle = computed(() => (isEdit.value ? '编辑资质' : '新增资质'))

// ---------------- Excel 导入 ----------------
const importInputRef = ref<HTMLInputElement>()
const importResult = ref<TesterMethodImportResult | null>(null)
const importDialogVisible = ref(false)
const importing = ref(false)

function buildParams(): TesterMethodParams {
  const params: TesterMethodParams = { current: current.value, size: size.value }
  if (query.methodName) params.methodName = query.methodName
  if (query.methodNo) params.methodNo = query.methodNo
  if (query.testerNo) params.testerNo = query.testerNo
  if (query.qualStatus !== undefined) params.qualStatus = query.qualStatus
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageTesterMethodApi(buildParams())
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
  query.methodName = ''
  query.methodNo = ''
  query.testerNo = ''
  query.qualStatus = undefined
  handleSearch()
}

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    methodName: '',
    methodNo: '',
    testerNo: '',
    qualStatus: 1,
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: TesterMethodRow): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    methodName: row.methodName,
    methodNo: row.methodNo ?? '',
    testerNo: row.testerNo,
    qualStatus: row.qualStatus,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  dialogSubmitting.value = true
  try {
    if (isEdit.value) {
      await updateTesterMethodApi(form)
      ElMessage.success('资质已更新')
    } else {
      await createTesterMethodApi(form)
      ElMessage.success('资质已新增')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    dialogSubmitting.value = false
  }
}

async function handleRemove(row: TesterMethodRow): Promise<void> {
  if (!(await askConfirm(
    `确认删除「${row.methodName}」— 工号 ${row.testerNo} 的资质记录？删除后 T-501 自动分配将不再考虑该组合。`,
    '删除确认',
    { type: 'warning' },
  ))) return
  try {
    await removeTesterMethodApi(row.id)
    ElMessage.success('已删除')
    void load()
  } catch {
    // 请求层已统一提示
  }
}

function triggerImport(): void {
  importInputRef.value?.click()
}

async function onFileChange(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    const result = await importTesterMethodApi(file)
    importResult.value = result
    importDialogVisible.value = true
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    importing.value = false
  }
}

/** 下载导入模板（前端生成，避免为模板单独开一个后端接口） */
function downloadTemplate(): void {
  const header = '检验方法名称,方法编号,检验员工号,资质状态(1有效/0失效),备注\n'
  const sample = '蔬菜中有机磷类农药残留量的测定,GB 23200.121,njna000,1,农残室具备该资质\n'
  const blob = new Blob(['\uFEFF' + header + sample], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '方法-检验员资质导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="方法-检验员资质"
      subtitle="配置检验员对检验方法持有资质，供任务安排第三级规则匹配"
      icon="Medal"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>基础数据</el-breadcrumb-item>
          <el-breadcrumb-item>方法资质</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Download"
        @click="downloadTemplate"
      >
        下载模板
      </el-button>
      <el-button
        :icon="Upload"
        :loading="importing"
        @click="triggerImport"
      >
        Excel 导入
      </el-button>
      <el-button
        v-permission="'base:tester-method:add'"
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        新增资质
      </el-button>
      <input
        ref="importInputRef"
        type="file"
        accept=".xlsx,.xls"
        style="display: none"
        @change="onFileChange"
      >
    </PageHeader>

    <DataFilter>
      <el-form inline>
        <el-form-item label="检验方法">
          <el-input
            v-model="query.methodName"
            placeholder="模糊查询"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="方法编号">
          <el-input
            v-model="query.methodNo"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="检验员工号">
          <el-input
            v-model="query.testerNo"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="资质状态">
          <el-select
            v-model="query.qualStatus"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              label="有效"
              :value="1"
            />
            <el-option
              label="失效"
              :value="0"
            />
          </el-select>
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
    </DataFilter>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <DataTable
        :rows="tableData"
        :loading="loading"
        :pagination="false"
        empty-title="暂无资质数据"
      >
        <el-table-column
          prop="methodName"
          label="检验方法"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column
          prop="methodNo"
          label="方法编号"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="检验员"
          min-width="150"
        >
          <template #default="{ row }">
            <span>{{ row.testerName ?? '—' }}</span>
            <span class="muted mono">（{{ row.testerNo }}）</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="deptName"
          label="所属部门"
          min-width="130"
          show-overflow-tooltip
        />
        <el-table-column
          label="资质状态"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="row.qualStatus === 1 ? 'success' : 'danger'"
              size="sm"
            >
              {{ row.qualStatusLabel ?? (row.qualStatus === 1 ? '有效' : '失效') }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          prop="remark"
          label="备注"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          label="操作"
          width="150"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'base:tester-method:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="openEdit(row as TesterMethodRow)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'base:tester-method:remove'"
              type="danger"
              link
              :icon="Delete"
              @click="handleRemove(row as TesterMethodRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无资质数据"
            hint="该表为空时，任务安排的第三级「方法资质规则」会永久落空，请先新增或导入"
          />
        </template>
      </DataTable>

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

    <!-- 新增/编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      :destroy-on-close="true"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
      >
        <el-form-item
          label="检验方法"
          prop="methodName"
        >
          <el-input
            v-model="form.methodName"
            placeholder="如：蔬菜中有机磷类农药残留量的测定"
          />
        </el-form-item>
        <el-form-item
          label="方法编号"
          prop="methodNo"
        >
          <el-input
            v-model="form.methodNo"
            placeholder="如：GB 23200.121"
          />
        </el-form-item>
        <el-form-item
          label="检验员工号"
          prop="testerNo"
        >
          <el-input
            v-model="form.testerNo"
            placeholder="必须是已存在的系统账号工号，如 njna000"
          />
        </el-form-item>
        <el-form-item
          label="资质状态"
          prop="qualStatus"
        >
          <el-radio-group v-model="form.qualStatus">
            <el-radio :value="1">
              有效
            </el-radio>
            <el-radio :value="0">
              失效
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="dialogSubmitting"
          @click="submitForm"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入结果"
      width="600px"
    >
      <template v-if="importResult">
        <el-alert
          v-if="importResult.failCount === 0"
          type="success"
          :closable="false"
          show-icon
          title="全部导入成功"
          class="mb"
        />
        <el-alert
          v-else
          type="warning"
          :closable="false"
          show-icon
          title="部分行导入失败（合法行已入库，不回滚）"
          class="mb"
        />
        <div class="result-grid">
          <div class="result-item">
            <span class="result-label">新增</span>
            <span class="result-value ok">{{ importResult.successCount }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">更新</span>
            <span class="result-value">{{ importResult.updateCount }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">失败</span>
            <span
              class="result-value"
              :class="{ err: importResult.failCount > 0 }"
            >{{ importResult.failCount }}</span>
          </div>
        </div>
        <div
          v-if="importResult.errors.length"
          class="error-list"
        >
          <div class="error-title">
            错误明细（最多展示 200 条）
          </div>
          <ul>
            <li
              v-for="(err, idx) in importResult.errors"
              :key="idx"
            >
              {{ err }}
            </li>
          </ul>
        </div>
      </template>
      <template #footer>
        <el-button
          type="primary"
          @click="importDialogVisible = false"
        >
          关闭
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
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}
.muted {
  color: var(--lims-text-secondary);
}
.mono {
  font-family: var(--lims-font-mono, ui-monospace, monospace);
  font-size: 12px;
}
.mb {
  margin-bottom: 12px;
}
.result-grid {
  display: flex;
  gap: 24px;
  padding: 4px 2px 12px;
}
.result-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.result-label {
  color: var(--lims-text-secondary);
  font-size: 12px;
}
.result-value {
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.result-value.ok {
  color: var(--lims-success);
}
.result-value.err {
  color: var(--lims-danger);
}
.error-list {
  max-height: 260px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid var(--lims-border);
  border-radius: var(--lims-r-sm);
  background: var(--lims-bg-subtle, transparent);
}
.error-title {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--lims-text-secondary);
}
.error-list ul {
  margin: 0;
  padding-left: 18px;
}
.error-list li {
  line-height: 1.9;
  font-size: 12px;
}
</style>
