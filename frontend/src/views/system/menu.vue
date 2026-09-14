<script setup lang="ts">
/**
 * 菜单/权限管理页（T-107，系统管理四页之一）
 *
 * <p><b>这是全系统权限标识的唯一权威来源</b>：后端 {@code @PreAuthorize("hasAuthority('...')")}
 * 里的字符串必须能在 sys_menu.permission 找到对应行，否则任何角色都无法被授予该权限。
 * 新增权限点的正确姿势是「先在本页建按钮行，再去角色页勾选」。</p>
 *
 * <p><b>类型语义</b>：目录(1) 是导航分组，不绑权限；菜单(2) 是页面，必须有路由路径；
 * 按钮(3) 是权限点，必须有 resource:action 权限标识。后端会按类型强校验字段形态，
 * 本页也在表单里给出即时提示。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createSysMenuApi,
  getSysMenuApi,
  removeSysMenuApi,
  treeSysMenuApi,
  updateSysMenuApi,
  type SysMenuRow,
  type SysMenuSaveBody,
} from '@/api/system'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'
import { askConfirm } from '@/utils/confirm'

const query = reactive({
  title: '',
  menuType: undefined as number | undefined,
})

const loading = ref(false)
const treeData = ref<SysMenuRow[]>([])

// ---------------- 编辑弹窗 ----------------
const dialogVisible = ref(false)
const dialogSubmitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysMenuSaveBody>({
  parentId: 0,
  title: '',
  path: '',
  icon: '',
  menuType: 2,
  permission: '',
  sortOrder: 1,
  visible: 1,
})

const rules: FormRules = {
  title: [{ required: true, message: '请填写菜单标题', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  permission: [
    {
      pattern: /^$|^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$/,
      message: '需形如 resource:action（小写字母/数字/中划线）',
      trigger: 'blur',
    },
  ],
}

const dialogTitle = computed(() => (isEdit.value ? '编辑菜单/权限' : '新增菜单/权限'))

/** 父节点下拉：把树拍平并加缩进，避免选择器里看不出层级 */
const parentOptions = computed(() => {
  const out: Array<{ id: number; label: string }> = []
  const walk = (nodes: SysMenuRow[], depth: number): void => {
    for (const n of nodes) {
      // 按钮不能作为父节点（权限点没有下级）
      if (n.menuType !== 3) {
        out.push({ id: n.id, label: `${'　'.repeat(depth)}${n.title}` })
        if (n.children?.length) {
          walk(n.children, depth + 1)
        }
      }
    }
  }
  walk(treeData.value, 0)
  return out
})

function typeLabel(t: number): string {
  if (t === 1) return '目录'
  if (t === 2) return '菜单'
  return '按钮'
}

function typeTone(t: number): 'info' | 'success' | 'warning' {
  if (t === 1) return 'info'
  if (t === 2) return 'success'
  return 'warning'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const params: { title?: string; menuType?: number } = {}
    if (query.title) params.title = query.title
    if (query.menuType !== undefined) params.menuType = query.menuType
    treeData.value = await treeSysMenuApi(Object.keys(params).length ? params : undefined)
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  void load()
}

function handleReset(): void {
  query.title = ''
  query.menuType = undefined
  handleSearch()
}

function resetForm(): void {
  Object.assign(form, {
    id: undefined,
    parentId: 0,
    title: '',
    path: '',
    icon: '',
    menuType: 2,
    permission: '',
    sortOrder: 1,
    visible: 1,
  })
}

/** 新增根节点（目录/顶层菜单） */
function openCreateRoot(): void {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

/** 在某节点下新增子节点：目录/菜单则预填其 id 作父级，按钮则预填其父级 */
function openCreateChild(row: SysMenuRow): void {
  isEdit.value = false
  resetForm()
  if (row.menuType === 3) {
    // 在按钮同级新增：挂到同一父节点下，且默认类型仍是按钮
    form.parentId = row.parentId
    form.menuType = 3
  } else {
    form.parentId = row.id
    form.menuType = row.menuType === 1 ? 2 : 3
  }
  dialogVisible.value = true
}

async function openEdit(row: SysMenuRow): Promise<void> {
  isEdit.value = true
  try {
    const detail = await getSysMenuApi(row.id)
    Object.assign(form, {
      id: detail.id,
      parentId: detail.parentId,
      title: detail.title,
      path: detail.path ?? '',
      icon: detail.icon ?? '',
      menuType: detail.menuType,
      permission: detail.permission ?? '',
      sortOrder: detail.sortOrder,
      visible: detail.visible,
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
  // 客户端即时反馈（后端同样强校验，此处只为更快的交互）
  if (form.menuType === 3 && !form.permission?.trim()) {
    ElMessage.warning('按钮类型必须填写权限标识')
    return
  }
  if (form.menuType !== 3 && form.permission?.trim()) {
    ElMessage.warning('目录/菜单类型不应填写权限标识，请改为按钮类型')
    return
  }
  if (form.menuType === 2 && !form.path?.trim()) {
    ElMessage.warning('菜单类型必须填写前端路由路径')
    return
  }
  dialogSubmitting.value = true
  try {
    if (isEdit.value) {
      await updateSysMenuApi(form)
      ElMessage.success('已更新')
    } else {
      await createSysMenuApi(form)
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    dialogSubmitting.value = false
  }
}

async function handleRemove(row: SysMenuRow): Promise<void> {
  if (!(await askConfirm(
    `确认删除「${row.title}」？若存在子节点系统会拒绝删除；删除会同时解除所有角色的该项授权。`,
    '删除确认',
    { type: 'warning' },
  ))) return
  try {
    await removeSysMenuApi(row.id)
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
      title="菜单管理"
      subtitle="维护菜单树与 resource:action 权限标识（后端鉴权的权威来源）"
      icon="Menu"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>系统管理</el-breadcrumb-item>
          <el-breadcrumb-item>菜单管理</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        v-permission="'sys:menu:add'"
        type="primary"
        :icon="Plus"
        @click="openCreateRoot"
      >
        新增根节点
      </el-button>
    </PageHeader>

    <DataFilter>
      <el-form inline>
        <el-form-item label="标题">
          <el-input
            v-model="query.title"
            placeholder="模糊查询"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="query.menuType"
            placeholder="全部"
            clearable
            style="width: 130px"
          >
            <el-option
              label="目录"
              :value="1"
            />
            <el-option
              label="菜单"
              :value="2"
            />
            <el-option
              label="按钮"
              :value="3"
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
      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column
          prop="title"
          label="标题"
          min-width="200"
        >
          <template #default="{ row }">
            <span>{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="typeTone(row.menuType)"
              size="sm"
            >
              {{ typeLabel(row.menuType) }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          prop="path"
          label="路由路径"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          label="权限标识"
          min-width="190"
        >
          <template #default="{ row }">
            <span
              v-if="row.permission"
              class="mono"
            >{{ row.permission }}</span>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="sortOrder"
          label="排序"
          width="70"
          align="center"
        />
        <el-table-column
          label="显示"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :class="row.visible === 1 ? '' : 'muted'">
              {{ row.visible === 1 ? '显示' : '隐藏' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="220"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.menuType !== 3"
              v-permission="'sys:menu:add'"
              type="primary"
              link
              :icon="Plus"
              @click="openCreateChild(row as SysMenuRow)"
            >
              子项
            </el-button>
            <el-button
              v-permission="'sys:menu:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="openEdit(row as SysMenuRow)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'sys:menu:remove'"
              type="danger"
              link
              :icon="Delete"
              @click="handleRemove(row as SysMenuRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无菜单数据"
            hint="菜单树为空时，所有角色都无法访问任何页面"
          />
        </template>
      </el-table>
    </AppCard>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      :destroy-on-close="true"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item
          label="上级节点"
          prop="parentId"
        >
          <el-select
            v-model="form.parentId"
            filterable
            style="width: 100%"
          >
            <el-option
              label="（根节点）"
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
          label="类型"
          prop="menuType"
        >
          <el-radio-group v-model="form.menuType">
            <el-radio :value="1">
              目录
            </el-radio>
            <el-radio :value="2">
              菜单
            </el-radio>
            <el-radio :value="3">
              按钮
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="标题"
          prop="title"
        >
          <el-input
            v-model="form.title"
            placeholder="如：质量负责人审批"
          />
        </el-form-item>
        <el-form-item
          v-if="form.menuType !== 3"
          label="路由路径"
          prop="path"
        >
          <el-input
            v-model="form.path"
            :placeholder="form.menuType === 1 ? '如：/base' : '如：/base/tester-method'"
          />
        </el-form-item>
        <el-form-item
          v-if="form.menuType !== 3"
          label="图标"
          prop="icon"
        >
          <el-input
            v-model="form.icon"
            placeholder="Element Plus 图标名，如 Files"
          />
        </el-form-item>
        <el-form-item
          v-if="form.menuType === 3"
          label="权限标识"
          prop="permission"
        >
          <el-input
            v-model="form.permission"
            placeholder="resource:action，如 base:lib:add"
          />
        </el-form-item>
        <el-form-item
          label="排序"
          prop="sortOrder"
        >
          <el-input-number
            v-model="form.sortOrder"
            :min="0"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item
          label="是否显示"
          prop="visible"
        >
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">
              显示
            </el-radio>
            <el-radio :value="0">
              隐藏
            </el-radio>
          </el-radio-group>
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
