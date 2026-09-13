<script setup lang="ts">
/**
 * 部门管理页（T-107，系统管理四页之一）
 *
 * <p><b>业务定位</b>：部门树是数据权限的骨架（AGENTS 8.3「本部门及下属部门」）。
 * 任务分配按部门收敛候选人范围、查询范围按部门子树裁剪——因此 parent_id 的完整性
 * 直接决定「能看到什么、能被分到什么活」。</p>
 *
 * <p><b>fail-loud 保护（后端）</b>：有子部门或有用户的部门不允许删除；parent_id 不允许成环。
 * 孤儿部门会让权限范围计算静默错位——查不到数据或查到不该看的，都比报错更难排查。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import {
  createDeptApi,
  getDeptApi,
  removeDeptApi,
  treeDeptApi,
  updateDeptApi,
  type DeptRow,
  type DeptSaveBody,
} from '@/api/system'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'

const loading = ref(false)
const treeData = ref<DeptRow[]>([])

// ---------------- 编辑弹窗 ----------------
const dialogVisible = ref(false)
const dialogSubmitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<DeptSaveBody>({
  parentId: 0,
  deptCode: '',
  deptName: '',
  leader: '',
  remark: '',
})

const rules: FormRules = {
  deptCode: [
    { required: true, message: '请填写部门编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字、下划线', trigger: 'blur' },
  ],
  deptName: [{ required: true, message: '请填写部门名称', trigger: 'blur' }],
}

const dialogTitle = computed(() => (isEdit.value ? '编辑部门' : '新增部门'))

/** 父部门下拉：拍平树并加缩进 */
const parentOptions = computed(() => {
  const out: Array<{ id: number; label: string }> = []
  const walk = (nodes: DeptRow[], depth: number): void => {
    for (const n of nodes) {
      out.push({ id: n.id, label: `${'　'.repeat(depth)}${n.deptName}` })
      if (n.children?.length) {
        walk(n.children, depth + 1)
      }
    }
  }
  walk(treeData.value, 0)
  return out
})

async function load(): Promise<void> {
  loading.value = true
  try {
    treeData.value = await treeDeptApi()
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  Object.assign(form, {
    id: undefined,
    parentId: 0,
    deptCode: '',
    deptName: '',
    leader: '',
    remark: '',
  })
}

function openCreateRoot(): void {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function openCreateChild(row: DeptRow): void {
  isEdit.value = false
  resetForm()
  form.parentId = row.id
  dialogVisible.value = true
}

async function openEdit(row: DeptRow): Promise<void> {
  isEdit.value = true
  try {
    const detail = await getDeptApi(row.id)
    Object.assign(form, {
      id: detail.id,
      parentId: detail.parentId,
      deptCode: detail.deptCode,
      deptName: detail.deptName,
      leader: detail.leader ?? '',
      remark: detail.remark ?? '',
    })
    dialogVisible.value = true
  } catch {
    // 请求层已统一提示
  }
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  dialogSubmitting.value = true
  try {
    if (isEdit.value) {
      await updateDeptApi(form)
      ElMessage.success('部门已更新')
    } else {
      await createDeptApi(form)
      ElMessage.success('部门已新增')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    dialogSubmitting.value = false
  }
}

async function handleRemove(row: DeptRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除部门「${row.deptName}」？若其下仍有子部门或用户，系统会拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeDeptApi(row.id)
    ElMessage.success('已删除')
    void load()
  } catch {
    // 请求层已统一提示
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="部门管理"
      subtitle="维护组织架构，支撑数据权限「本部门及下属部门」"
      icon="OfficeBuilding"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>系统管理</el-breadcrumb-item>
          <el-breadcrumb-item>部门管理</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        v-permission="'sys:dept:add'"
        type="primary"
        :icon="Plus"
        @click="openCreateRoot"
      >
        新增顶级部门
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column
          prop="deptName"
          label="部门名称"
          min-width="220"
        />
        <el-table-column
          prop="deptCode"
          label="部门编码"
          width="130"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.deptCode }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="人数"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.userCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          prop="leader"
          label="负责人"
          width="130"
        >
          <template #default="{ row }">
            <span v-if="row.leader">{{ row.leader }}</span>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="remark"
          label="备注"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          label="操作"
          width="200"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'sys:dept:add'"
              type="primary"
              link
              :icon="Plus"
              @click="openCreateChild(row as DeptRow)"
            >
              子部门
            </el-button>
            <el-button
              v-permission="'sys:dept:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="openEdit(row as DeptRow)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'sys:dept:remove'"
              type="danger"
              link
              :icon="Delete"
              @click="handleRemove(row as DeptRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无部门数据"
            hint="部门树为空时，用户无法设置归属部门，数据权限将失效"
          />
        </template>
      </el-table>
    </AppCard>

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
        label-width="100px"
      >
        <el-form-item
          label="上级部门"
          prop="parentId"
        >
          <el-select
            v-model="form.parentId"
            filterable
            style="width: 100%"
          >
            <el-option
              label="（顶级部门）"
              :value="0"
            />
            <el-option
              v-for="p in parentOptions"
              :key="p.id"
              :label="p.label"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="部门编码"
          prop="deptCode"
        >
          <el-input
            v-model="form.deptCode"
            placeholder="如 NA（农残检验室）"
          />
        </el-form-item>
        <el-form-item
          label="部门名称"
          prop="deptName"
        >
          <el-input
            v-model="form.deptName"
            placeholder="如 农残检验室"
          />
        </el-form-item>
        <el-form-item
          label="负责人"
          prop="leader"
        >
          <el-input
            v-model="form.leader"
            placeholder="选填"
          />
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
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
}
.mono {
  font-family: var(--lims-font-mono, ui-monospace, monospace);
  font-size: 12px;
}
.muted {
  color: var(--lims-text-secondary);
}
</style>
