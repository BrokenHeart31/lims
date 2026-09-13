<script setup lang="ts">
/**
 * 角色管理页（T-107，系统管理四页之一）
 *
 * <p><b>业务定位</b>：角色是权限的载体——`sys_role_menu` 决定该角色能看到哪些菜单、
 * 拥有哪些 {@code resource:action} 权限点。后端 {@code @PreAuthorize} 校验的正是这些标识。</p>
 *
 * <p><b>R100 综合管理是特权角色</b>：它在后端被硬编码为「拥有全部权限与菜单」
 * （不依赖 sys_role_menu）。因此本页对它做三重禁用：编码不可改、权限树不可编辑、不可删除——
 * 用界面约束表达一条后端同样强制的不变量，比事后报错友好。</p>
 *
 * <p><b>覆盖式绑定</b>：保存时提交该角色完整的 menuIds 集合。取消勾选的权限会被真正移除
 * （后端先清后建），这是权限系统里最容易被做错的一点。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createSysRoleApi,
  getSysRoleApi,
  pageSysRoleApi,
  removeSysRoleApi,
  treeSysMenuApi,
  updateSysRoleApi,
  type SysMenuRow,
  type SysRoleParams,
  type SysRoleRow,
  type SysRoleSaveBody,
} from '@/api/system'
import type { ElTree } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'

/** 综合管理特权角色编码（与后端 SysRole.ADMIN_ROLE_CODE 一致） */
const ADMIN_ROLE_CODE = 'R100'

const query = reactive({ roleCode: '', roleName: '' })

const loading = ref(false)
const tableData = ref<SysRoleRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

// ---------------- 编辑弹窗 ----------------
const dialogVisible = ref(false)
const dialogSubmitting = ref(false)
const isEdit = ref(false)
const isAdminRole = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysRoleSaveBody>({
  roleCode: '',
  roleName: '',
  description: '',
  menuIds: [],
})

const rules: FormRules = {
  roleCode: [
    { required: true, message: '请填写角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字、下划线', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请填写角色名称', trigger: 'blur' }],
}

// ---------------- 权限树 ----------------
const menuTree = ref<SysMenuRow[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const treeCheckedKeys = ref<number[]>([])
/** 半选节点：提交时**必须一并带上**，否则父级菜单不会被授权，子权限点即便勾了也无法显示 */
const treeHalfCheckedKeys = ref<number[]>([])

const treeProps = {
  label: 'title',
  children: 'children',
}

/** 按 menu_type 渲染不同色调，让「页面」与「按钮权限」一眼可分 */
function typeLabel(t: number): string {
  if (t === 1) return '目录'
  if (t === 2) return '菜单'
  return '按钮'
}

/** el-tree 勾选回调：同时记录勾选与半选（半选是父级目录，提交时必须带上） */
function handleTreeCheck(_node: Node, info: { checkedKeys: unknown[]; halfCheckedKeys: unknown[] }): void {
  treeCheckedKeys.value = info.checkedKeys.map(Number)
  treeHalfCheckedKeys.value = info.halfCheckedKeys.map(Number)
}

const dialogTitle = computed(() => (isEdit.value ? '编辑角色' : '新增角色'))

function buildParams(): SysRoleParams {
  const params: SysRoleParams = { current: current.value, size: size.value }
  if (query.roleCode) params.roleCode = query.roleCode
  if (query.roleName) params.roleName = query.roleName
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageSysRoleApi(buildParams())
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadMenuTree(): Promise<void> {
  try {
    menuTree.value = await treeSysMenuApi()
  } catch {
    // 请求层已统一提示
  }
}

function handleSearch(): void {
  current.value = 1
  void load()
}

function handleReset(): void {
  query.roleCode = ''
  query.roleName = ''
  handleSearch()
}

function openCreate(): void {
  isEdit.value = false
  isAdminRole.value = false
  Object.assign(form, { id: undefined, roleCode: '', roleName: '', description: '', menuIds: [] })
  treeCheckedKeys.value = []
  if (treeRef.value) {
    treeRef.value.setCheckedKeys([])
  }
  dialogVisible.value = true
}

async function openEdit(row: SysRoleRow): Promise<void> {
  isEdit.value = true
  try {
    const detail = await getSysRoleApi(row.id)
    isAdminRole.value = detail.roleCode === ADMIN_ROLE_CODE
    Object.assign(form, {
      id: detail.id,
      roleCode: detail.roleCode,
      roleName: detail.roleName,
      description: detail.description ?? '',
      menuIds: detail.menuIds ?? [],
    })
    treeCheckedKeys.value = detail.menuIds ?? []
    // 只勾叶子：勾父节点会连带全选子节点，回显会「看起来授了更多权限」
    if (treeRef.value) {
      treeRef.value.setCheckedKeys(detail.menuIds ?? [], false)
    }
    dialogVisible.value = true
  } catch {
    // 请求层已统一提示
  }
}

/** 收集勾选 + 半选的节点 id（半选是父级目录，必须提交否则子权限点无入口） */
function collectCheckedKeys(): number[] {
  if (!treeRef.value) {
    return treeCheckedKeys.value
  }
  const checked = treeRef.value.getCheckedKeys() as Array<string | number>
  const half = treeRef.value.getHalfCheckedKeys() as Array<string | number>
  return Array.from(new Set([...checked, ...half].map(Number)))
}

async function submitForm(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  dialogSubmitting.value = true
  try {
    const payload: SysRoleSaveBody = {
      ...form,
      menuIds: isAdminRole.value ? form.menuIds : collectCheckedKeys(),
    }
    if (isEdit.value) {
      await updateSysRoleApi(payload)
      ElMessage.success('角色已更新')
    } else {
      await createSysRoleApi(payload)
      ElMessage.success('角色已新增')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    dialogSubmitting.value = false
  }
}

async function handleRemove(row: SysRoleRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除角色「${row.roleName}（${row.roleCode}）」？若该角色下仍绑定用户，系统会拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeSysRoleApi(row.id)
    ElMessage.success('已删除')
    void load()
  } catch {
    // 请求层已统一提示
  }
}

onMounted(() => {
  void loadMenuTree()
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="角色管理"
      subtitle="定义角色并分配菜单与操作权限"
      icon="Avatar"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>系统管理</el-breadcrumb-item>
          <el-breadcrumb-item>角色管理</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        v-permission="'sys:role:add'"
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        新增角色
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="20"
    >
      <el-form inline>
        <el-form-item label="角色编码">
          <el-input
            v-model="query.roleCode"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input
            v-model="query.roleName"
            placeholder="模糊查询"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
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
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
      >
        <el-table-column
          prop="roleCode"
          label="角色编码"
          width="130"
        >
          <template #default="{ row }">
            <span class="mono">{{ row.roleCode }}</span>
            <StatusBadge
              v-if="row.roleCode === ADMIN_ROLE_CODE"
              tone="warning"
              size="sm"
              class="tag"
            >
              特权
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          prop="roleName"
          label="角色名称"
          min-width="140"
        />
        <el-table-column
          prop="description"
          label="描述"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          label="绑定用户"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ row.userCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="150"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'sys:role:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="openEdit(row as SysRoleRow)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.roleCode !== ADMIN_ROLE_CODE"
              v-permission="'sys:role:remove'"
              type="danger"
              link
              :icon="Delete"
              @click="handleRemove(row as SysRoleRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无角色"
            hint="新增角色并分配菜单权限后，用户才能访问对应页面"
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="620px"
      :destroy-on-close="true"
    >
      <el-alert
        v-if="isAdminRole"
        type="warning"
        :closable="false"
        show-icon
        title="综合管理（R100）是系统特权角色"
        description="它始终拥有全部权限与菜单，无需也不允许在此编辑权限；角色编码亦不可修改。"
        class="mb"
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-form-item
          label="角色编码"
          prop="roleCode"
        >
          <el-input
            v-model="form.roleCode"
            :disabled="isAdminRole"
            placeholder="如 R4（创建后建议不要修改）"
          />
        </el-form-item>
        <el-form-item
          label="角色名称"
          prop="roleName"
        >
          <el-input
            v-model="form.roleName"
            placeholder="如 质量负责人"
          />
        </el-form-item>
        <el-form-item
          label="描述"
          prop="description"
        >
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="tree-box">
            <el-tree
              ref="treeRef"
              :data="menuTree"
              :props="treeProps"
              node-key="id"
              show-checkbox
              :default-expand-all="false"
              :disabled="isAdminRole"
              :check-strictly="false"
              @check="handleTreeCheck"
            >
              <template #default="{ data }">
                <span class="tree-node">
                  <span class="tree-title">{{ data.title }}</span>
                  <span
                    class="tree-type"
                    :class="`t-${data.menuType}`"
                  >{{ typeLabel(data.menuType) }}</span>
                  <span
                    v-if="data.permission"
                    class="tree-perm mono"
                  >{{ data.permission }}</span>
                </span>
              </template>
            </el-tree>
          </div>
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
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}
.mono {
  font-family: var(--lims-font-mono, ui-monospace, monospace);
  font-size: 12px;
}
.tag {
  margin-left: 6px;
}
.mb {
  margin-bottom: 12px;
}
.tree-box {
  max-height: 320px;
  width: 100%;
  overflow: auto;
  padding: 8px 10px;
  border: 1px solid var(--lims-border);
  border-radius: var(--lims-r-sm);
}
.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.tree-type {
  padding: 0 5px;
  border-radius: 3px;
  font-size: 11px;
  line-height: 16px;
  border: 1px solid var(--lims-border);
  color: var(--lims-text-secondary);
}
.tree-type.t-3 {
  color: var(--lims-warning, #d97706);
  border-color: var(--lims-warning-line, #fcd34d);
}
.tree-perm {
  font-size: 11px;
  color: var(--lims-text-secondary);
}
</style>
