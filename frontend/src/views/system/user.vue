<script setup lang="ts">
/**
 * 用户管理页（T-107，系统管理四页之一）
 *
 * <p><b>业务定位</b>：用户是 RBAC 的起点，也是两处业务链路的数据源——
 * username（工号）是 T-105 方法资质的 tester_no、T-501 任务分配的候选人键；
 * nickname + signature_url 是 T-702 报告上「检验员/审核人/签发人」的署名与签名图。</p>
 *
 * <p><b>安全红线</b>：本页永不展示、永不接收密码。新增时设置初始密码；后续改密走独立的
 * 「重置密码」入口（PUT /sys/user/{id}/password），请求体单向进入、后端 VO 无密码字段。</p>
 *
 * <p><b>后端 fail-loud 保护</b>：不允许删除当前登录账号，也不允许删除/停用最后一个
 * R100 综合管理员——这两种操作会让系统永久失去权限维护能力。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Key, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createSysUserApi,
  getSysUserApi,
  listAllDeptApi,
  listAllSysRoleApi,
  pageSysUserApi,
  removeSysUserApi,
  resetSysUserPasswordApi,
  updateSysUserApi,
  type DeptRow,
  type SysRoleRow,
  type SysUserParams,
  type SysUserRow,
  type SysUserSaveBody,
} from '@/api/system'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'

const query = reactive({
  username: '',
  nickname: '',
  deptId: undefined as number | undefined,
  status: undefined as number | undefined,
})

const loading = ref(false)
const tableData = ref<SysUserRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

const deptOptions = ref<DeptRow[]>([])
const roleOptions = ref<SysRoleRow[]>([])

// ---------------- 编辑弹窗 ----------------
const dialogVisible = ref(false)
const dialogSubmitting = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysUserSaveBody>({
  username: '',
  password: '',
  nickname: '',
  deptId: undefined,
  email: '',
  phone: '',
  signatureUrl: '',
  status: 1,
  remark: '',
  roleIds: [],
})

const rules: FormRules = {
  username: [
    { required: true, message: '请填写登录名（工号）', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能包含字母、数字、下划线', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

/** 新增时密码必填（后端也校验）；编辑时留空表示不改密码 */
const dynamicRules = ref<FormRules>({})

// ---------------- 重置密码 ----------------
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdTarget = ref<SysUserRow | null>(null)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ password: '', confirm: '' })
const pwdRules: FormRules = {
  password: [
    { required: true, message: '请填写新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6~32 位之间', trigger: 'blur' },
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function buildParams(): SysUserParams {
  const params: SysUserParams = { current: current.value, size: size.value }
  if (query.username) params.username = query.username
  if (query.nickname) params.nickname = query.nickname
  if (query.deptId !== undefined) params.deptId = query.deptId
  if (query.status !== undefined) params.status = query.status
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageSysUserApi(buildParams())
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

async function loadOptions(): Promise<void> {
  try {
    const [depts, roles] = await Promise.all([listAllDeptApi(), listAllSysRoleApi()])
    deptOptions.value = depts
    roleOptions.value = roles
  } catch {
    // 请求层已统一提示
  }
}

function handleSearch(): void {
  current.value = 1
  void load()
}

function handleReset(): void {
  query.username = ''
  query.nickname = ''
  query.deptId = undefined
  query.status = undefined
  handleSearch()
}

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    username: '',
    password: '',
    nickname: '',
    deptId: undefined,
    email: '',
    phone: '',
    signatureUrl: '',
    status: 1,
    remark: '',
    roleIds: [],
  })
  dynamicRules.value = {
    password: [
      { required: true, message: '请设置初始密码', trigger: 'blur' },
      { min: 6, max: 32, message: '密码长度需在 6~32 位之间', trigger: 'blur' },
    ],
  }
  dialogVisible.value = true
}

async function openEdit(row: SysUserRow): Promise<void> {
  isEdit.value = true
  dynamicRules.value = {}
  try {
    const detail = await getSysUserApi(row.id)
    Object.assign(form, {
      id: detail.id,
      username: detail.username,
      password: '',
      nickname: detail.nickname,
      deptId: detail.deptId ?? undefined,
      email: detail.email ?? '',
      phone: detail.phone ?? '',
      signatureUrl: detail.signatureUrl ?? '',
      status: detail.status,
      remark: detail.remark ?? '',
      roleIds: detail.roleIds ?? [],
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
      // 编辑不提交 username（后端有意忽略）与 password（走独立入口）
      const { password: _ignored, username: _ignoredName, ...rest } = form
      await updateSysUserApi({ ...rest, username: form.username })
      ElMessage.success('用户已更新')
    } else {
      await createSysUserApi(form)
      ElMessage.success('用户已新增')
    }
    dialogVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    dialogSubmitting.value = false
  }
}

function openResetPwd(row: SysUserRow): void {
  pwdTarget.value = row
  pwdForm.password = ''
  pwdForm.confirm = ''
  pwdDialogVisible.value = true
}

async function submitResetPwd(): Promise<void> {
  if (!pwdFormRef.value || !pwdTarget.value) return
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  pwdSubmitting.value = true
  try {
    await resetSysUserPasswordApi(pwdTarget.value.id, pwdForm.password)
    ElMessage.success(`已重置「${pwdTarget.value.nickname}」的密码`)
    pwdDialogVisible.value = false
  } catch {
    // 请求层已统一提示
  } finally {
    pwdSubmitting.value = false
  }
}

async function handleRemove(row: SysUserRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除用户「${row.nickname}（${row.username}）」？删除后该账号将无法登录，其历史操作记录仍保留。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeSysUserApi(row.id)
    ElMessage.success('已删除')
    void load()
  } catch {
    // 请求层已统一提示（含「不能删除当前登录账号 / 最后一个管理员」）
  }
}

onMounted(() => {
  void loadOptions()
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="用户管理"
      subtitle="维护系统账号、部门归属与角色绑定"
      icon="User"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>系统管理</el-breadcrumb-item>
          <el-breadcrumb-item>用户管理</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
      <el-button
        v-permission="'sys:user:add'"
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        新增用户
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="20"
    >
      <el-form inline>
        <el-form-item label="登录名">
          <el-input
            v-model="query.username"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model="query.nickname"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-select
            v-model="query.deptId"
            placeholder="全部"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option
              v-for="d in deptOptions"
              :key="d.id"
              :label="d.deptName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option
              label="启用"
              :value="1"
            />
            <el-option
              label="停用"
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
          prop="username"
          label="登录名"
          width="130"
        />
        <el-table-column
          prop="nickname"
          label="姓名"
          min-width="130"
        />
        <el-table-column
          prop="deptName"
          label="部门"
          min-width="130"
          show-overflow-tooltip
        />
        <el-table-column
          label="角色"
          min-width="170"
        >
          <template #default="{ row }">
            <template v-if="row.roleNames && row.roleNames.length">
              <StatusBadge
                v-for="name in row.roleNames"
                :key="name"
                tone="purple"
                size="sm"
                class="tag"
              >
                {{ name }}
              </StatusBadge>
            </template>
            <span
              v-else
              class="muted"
            >未分配</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="phone"
          label="手机"
          width="130"
        />
        <el-table-column
          label="状态"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="row.status === 1 ? 'success' : 'neutral'"
              size="sm"
            >
              {{ row.status === 1 ? '启用' : '停用' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="230"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'sys:user:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="openEdit(row as SysUserRow)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'sys:user:edit'"
              type="primary"
              link
              :icon="Key"
              @click="openResetPwd(row as SysUserRow)"
            >
              重置密码
            </el-button>
            <el-button
              v-permission="'sys:user:remove'"
              type="danger"
              link
              :icon="Delete"
              @click="handleRemove(row as SysUserRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无用户"
            hint="新增用户后需为其分配角色，否则无任何权限"
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

    <!-- 新增/编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="600px"
      :destroy-on-close="true"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="{ ...rules, ...dynamicRules }"
        label-width="100px"
      >
        <el-form-item
          label="登录名"
          prop="username"
        >
          <el-input
            v-model="form.username"
            :disabled="isEdit"
            placeholder="工号，如 nj004（创建后不可修改）"
          />
        </el-form-item>
        <el-form-item
          v-if="!isEdit"
          label="初始密码"
          prop="password"
        >
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="6~32 位"
          />
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="nickname"
        >
          <el-input
            v-model="form.nickname"
            placeholder="真实姓名（报告署名用）"
          />
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="deptId"
        >
          <el-select
            v-model="form.deptId"
            placeholder="请选择"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in deptOptions"
              :key="d.id"
              :label="d.deptName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="角色"
          prop="roleIds"
        >
          <el-select
            v-model="form.roleIds"
            multiple
            placeholder="请选择（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="`${r.roleName}（${r.roleCode}）`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="邮箱"
          prop="email"
        >
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item
          label="手机"
          prop="phone"
        >
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item
          label="签名图片"
          prop="signatureUrl"
        >
          <el-input
            v-model="form.signatureUrl"
            placeholder="图片地址；留空时报告渲染虚线占位框"
          />
        </el-form-item>
        <el-form-item
          label="状态"
          prop="status"
        >
          <el-radio-group v-model="form.status">
            <el-radio :value="1">
              启用
            </el-radio>
            <el-radio :value="0">
              停用
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

    <!-- 重置密码 -->
    <el-dialog
      v-model="pwdDialogVisible"
      title="重置密码"
      width="420px"
      :destroy-on-close="true"
    >
      <el-alert
        v-if="pwdTarget"
        type="warning"
        :closable="false"
        show-icon
        :title="`即将重置「${pwdTarget.nickname}（${pwdTarget.username}）」的登录密码`"
        class="mb"
      />
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="80px"
      >
        <el-form-item
          label="新密码"
          prop="password"
        >
          <el-input
            v-model="pwdForm.password"
            type="password"
            show-password
            placeholder="6~32 位"
          />
        </el-form-item>
        <el-form-item
          label="确认密码"
          prop="confirm"
        >
          <el-input
            v-model="pwdForm.confirm"
            type="password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="pwdSubmitting"
          @click="submitResetPwd"
        >
          确认重置
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
.tag {
  margin-right: 4px;
}
.mb {
  margin-bottom: 12px;
}
</style>
