<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createTaskApi,
  deleteTaskApi,
  pageTaskApi,
  updateTaskApi,
  TASK_NATURE_OPTIONS,
  TASK_REGION_OPTIONS,
  TASK_STATUS_OPTIONS,
  SAMPLING_STAGE_OPTIONS,
  type SuperviseTask,
} from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import { askConfirm } from '@/utils/confirm'

const authStore = useAuthStore()

// ---------------- 查询区 ----------------
const queryRef = ref<FormInstance>()
const query = reactive({
  taskNo: '',
  taskName: '',
  status: '',
})

// ---------------- 表格 ----------------
const loading = ref(false)
const tableData = ref<SuperviseTask[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

async function loadList(): Promise<void> {
  loading.value = true
  try {
    const res = await pageTaskApi({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      taskNo: query.taskNo || undefined,
      taskName: query.taskName || undefined,
      status: query.status || undefined,
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
  query.taskNo = ''
  query.taskName = ''
  query.status = ''
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

// task 状态 → tone（简单映射）
function statusTone(s: string | undefined): 'success' | 'warning' | 'info' | 'pending' | 'neutral' | 'purple' {
  if (!s) return 'neutral'
  if (s.includes('完成') || s.includes('已签发')) return 'success'
  if (s.includes('进行') || s.includes('中')) return 'pending'
  if (s.includes('草稿')) return 'neutral'
  if (s.includes('退回')) return 'warning'
  return 'info'
}

// ---------------- 新建/编辑弹窗 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)

const emptyForm = (): SuperviseTask => ({
  taskNo: '',
  taskName: '',
  taskNature: '监督抽检',
  taskSource: '',
  regionLevel: '',
  leader: '',
  batchNo: '',
  receiveDate: '',
  issueDate: '',
  completeDate: '',
  priority: '',
  positiveRateRequirement: '',
  samplingStage: '',
  testScope: '',
  status: '草稿',
  remark: '',
})

const form = reactive<SuperviseTask>(emptyForm())

const rules: FormRules = {
  taskNo: [{ required: true, message: '请输入任务编号', trigger: 'blur' }],
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskNature: [{ required: true, message: '请选择任务性质', trigger: 'change' }],
}

function openCreate(): void {
  dialogTitle.value = '新建监抽任务'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: SuperviseTask): void {
  dialogTitle.value = '编辑监抽任务'
  Object.assign(form, row)
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
    if (form.id) {
      await updateTaskApi({ ...form })
      ElMessage.success('更新成功')
    } else {
      await createTaskApi({ ...form })
      ElMessage.success('新建成功')
    }
    dialogVisible.value = false
    void loadList()
  } catch {
    // 请求层已统一提示
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SuperviseTask): Promise<void> {
  if (!(await askConfirm(`确定删除任务「${row.taskName}（${row.taskNo}）」吗？`, '删除确认', { type: 'warning' }))) return
  try {
    await deleteTaskApi(row.id!)
    ElMessage.success('删除成功')
    void loadList()
  } catch {
    // 请求层已统一提示
  }
}

onMounted(() => {
  void loadList()
})
</script>

<template>
  <div class="task-page">
    <PageHeader
      title="监抽任务"
      subtitle="下达 / 维护食品质量监督抽检任务，作为后续采样的来源依据"
      icon="Notebook"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>业务管理</el-breadcrumb-item>
          <el-breadcrumb-item>监抽任务</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        v-if="authStore.hasPermission('task:add')"
        type="primary"
        @click="openCreate"
      >
        新建任务
      </el-button>
    </PageHeader>

    <!-- 查询区 -->
    <AppCard
      variant="panel"
      :padding="20"
    >
      <el-form
        ref="queryRef"
        :model="query"
        inline
        @submit.prevent
      >
        <el-form-item label="任务编号">
          <el-input
            v-model="query.taskNo"
            placeholder="任务编号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input
            v-model="query.taskName"
            placeholder="任务名称"
            clearable
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
              v-for="s in TASK_STATUS_OPTIONS"
              :key="s"
              :label="s"
              :value="s"
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
    </AppCard>

    <!-- 列表区 -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
      >
        <el-table-column
          prop="taskNo"
          label="任务编号"
          width="160"
        />
        <el-table-column
          prop="taskName"
          label="任务名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="taskNature"
          label="任务性质"
          width="100"
        />
        <el-table-column
          prop="taskSource"
          label="任务来源"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="regionLevel"
          label="级别"
          width="80"
        />
        <el-table-column
          prop="leader"
          label="负责人"
          width="90"
        />
        <el-table-column
          prop="receiveDate"
          label="接受日期"
          width="110"
        />
        <el-table-column
          prop="status"
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="statusTone((row as SuperviseTask).status)"
              size="sm"
            >
              {{ (row as SuperviseTask).status }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="140"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="authStore.hasPermission('task:edit')"
              link
              type="primary"
              @click="openEdit(row as SuperviseTask)"
            >
              编辑
            </el-button>
            <el-button
              v-if="authStore.hasPermission('task:remove')"
              link
              type="danger"
              @click="handleDelete(row as SuperviseTask)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty description="暂无监抽任务，可点击右上「新建任务」下达" />
        </template>
      </el-table>

      <el-pagination
        class="pager"
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </AppCard>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              label="任务编号"
              prop="taskNo"
            >
              <el-input
                v-model="form.taskNo"
                placeholder="如 RW-SA-20230101"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="任务名称"
              prop="taskName"
            >
              <el-input v-model="form.taskName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              label="任务性质"
              prop="taskNature"
            >
              <el-select
                v-model="form.taskNature"
                style="width: 100%"
              >
                <el-option
                  v-for="n in TASK_NATURE_OPTIONS"
                  :key="n"
                  :label="n"
                  :value="n"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务来源">
              <el-input
                v-model="form.taskSource"
                placeholder="下达单位"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="区域级别">
              <el-select
                v-model="form.regionLevel"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="r in TASK_REGION_OPTIONS"
                  :key="r"
                  :label="r"
                  :value="r"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-input v-model="form.leader" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次">
              <el-input v-model="form.batchNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务等级">
              <el-input v-model="form.priority" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接受日期">
              <el-date-picker
                v-model="form.receiveDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下达日期">
              <el-date-picker
                v-model="form.issueDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="完成日期">
              <el-date-picker
                v-model="form.completeDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="抽样环节">
              <el-select
                v-model="form.samplingStage"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="s in SAMPLING_STAGE_OPTIONS"
                  :key="s"
                  :label="s"
                  :value="s"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="阳性率要求">
              <el-input v-model="form.positiveRateRequirement" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测范围">
              <el-input v-model="form.testScope" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select
                v-model="form.status"
                style="width: 100%"
              >
                <el-option
                  v-for="s in TASK_STATUS_OPTIONS"
                  :key="s"
                  :label="s"
                  :value="s"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="任务说明">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="3"
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
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.task-page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
}
.pager {
  margin-top: var(--lims-r-sm);
  justify-content: flex-end;
}
</style>
