<script setup lang="ts">
/**
 * 项目标准库维护页（T-106，说明书第二(3)节）
 *
 * <p><b>业务定位</b>：说明书原文——「项目标准库设置的目的是项目检测单项分解时系统根据项目标准库
 * 自动加载相关产品需要检测的检测单项，包括检测依据、检验方法、判定标准。选择『导入新的项目库』
 * 添加新的项目库数据。」</p>
 *
 * <p>因此本页是 T-401 项目分解的**数据源**：分解时按产品编号匹配本库，把本库字段「快照下沉」进
 * sample_item。本页对标准库的修改**不会回溯**已分解的样品——报告必须固化检验当时的判定依据
 * （见 DECISIONS 2026-09-11「快照下沉」）。</p>
 *
 * <p><b>交互设计</b>：主表为产品，展开行/右侧抽屉为检测单项。明细采用「整表覆盖式保存」
 * （PUT /base/lib/{id}/items）：表格是业务方对该产品的完整定义，增量合并会让「表格里删掉的一行」
 * 在系统里残留。导入同上——按产品编号分组后覆盖式写入，同一份 Excel 可反复导入而不产生重复。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  createProductLibApi,
  importProductLibApi,
  listProductLibItemsApi,
  pageProductLibApi,
  removeProductLibApi,
  replaceProductLibItemsApi,
  updateProductLibApi,
  type ProductLibImportResult,
  type ProductLibItemRow,
  type ProductLibItemSaveBody,
  type ProductLibParams,
  type ProductLibRow,
  type ProductLibSaveBody,
} from '@/api/base'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'
import { askConfirm } from '@/utils/confirm'

/** 判定类型选项（与后端 JudgeEngine 闭集一致，顺序即下拉展示顺序） */
const JUDGE_TYPES = [
  { value: 1, label: '限量比较' },
  { value: 2, label: '不得检出/不得使用' },
  { value: 3, label: '文本/感官（人工）' },
] as const

const query = reactive({
  productCode: '',
  productName: '',
  category: '',
})

const loading = ref(false)
const tableData = ref<ProductLibRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(20)

// ---------------- 产品新增/编辑 ----------------
const productDialogVisible = ref(false)
const productSubmitting = ref(false)
const isProductEdit = ref(false)
const productFormRef = ref<FormInstance>()
const productForm = reactive<ProductLibSaveBody>({
  productCode: '',
  productName: '',
  category: '',
})

const productRules: FormRules = {
  productCode: [{ required: true, message: '请填写产品编号', trigger: 'blur' }],
  productName: [{ required: true, message: '请填写产品名称', trigger: 'blur' }],
}

const productDialogTitle = computed(() => (isProductEdit.value ? '编辑产品' : '新增产品'))

// ---------------- 明细抽屉（整表覆盖式保存） ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerSaving = ref(false)
const currentProduct = ref<ProductLibRow | null>(null)
const itemRows = ref<ProductLibItemSaveBody[]>([])
/** 后端已有的明细 id 集合：保存时用于判断「哪些是新增」，仅用于提示，不参与提交语义 */
const existingItemIds = ref<Set<number>>(new Set())

// ---------------- Excel 导入 ----------------
const importInputRef = ref<HTMLInputElement>()
const importResult = ref<ProductLibImportResult | null>(null)
const importDialogVisible = ref(false)
const importing = ref(false)

function buildParams(): ProductLibParams {
  const params: ProductLibParams = { current: current.value, size: size.value }
  if (query.productCode) params.productCode = query.productCode
  if (query.productName) params.productName = query.productName
  if (query.category) params.category = query.category
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageProductLibApi(buildParams())
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
  query.productCode = ''
  query.productName = ''
  query.category = ''
  handleSearch()
}

function openCreateProduct(): void {
  isProductEdit.value = false
  Object.assign(productForm, { id: undefined, productCode: '', productName: '', category: '' })
  productDialogVisible.value = true
}

function openEditProduct(row: ProductLibRow): void {
  isProductEdit.value = true
  Object.assign(productForm, {
    id: row.id,
    productCode: row.productCode ?? '',
    productName: row.productName ?? '',
    category: row.category ?? '',
  })
  productDialogVisible.value = true
}

async function submitProduct(): Promise<void> {
  if (!productFormRef.value) return
  const valid = await productFormRef.value.validate().catch(() => false)
  if (!valid) return
  productSubmitting.value = true
  try {
    if (isProductEdit.value) {
      await updateProductLibApi(productForm)
      ElMessage.success('产品已更新')
    } else {
      await createProductLibApi(productForm)
      ElMessage.success('产品已新增，请继续维护其检测单项')
    }
    productDialogVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    productSubmitting.value = false
  }
}

async function handleRemoveProduct(row: ProductLibRow): Promise<void> {
  if (!(await askConfirm(
    `确认删除产品「${row.productName}」？若其下仍有检测单项，系统会拒绝删除（请先清空明细）。`,
    '删除确认',
    { type: 'warning' },
  ))) return
  try {
    await removeProductLibApi(row.id)
    ElMessage.success('已删除')
    void load()
  } catch {
    // 请求层已统一提示（含「有明细不允许删」的业务错误）
  }
}

// ---------------- 明细抽屉逻辑 ----------------

/** 后端明细行 → 可编辑行（补齐 id，用于覆盖式提交时的提示） */
function toEditableRows(rows: ProductLibItemRow[]): ProductLibItemSaveBody[] {
  return rows.map((r) => ({
    id: r.id,
    productLibId: r.productLibId ?? undefined,
    itemOrder: r.itemOrder,
    itemName: r.itemName,
    unit: r.unit ?? '',
    basisCode: r.basisCode ?? '',
    methods: r.methods ?? '',
    stdValue: r.stdValue ?? '',
    judgeType: r.judgeType,
    isReference: r.isReference,
    lowerLimit: r.lowerLimit ?? '',
    methodNote: r.methodNote ?? '',
  }))
}

async function openItems(row: ProductLibRow): Promise<void> {
  currentProduct.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  itemRows.value = []
  existingItemIds.value = new Set()
  try {
    const rows = await listProductLibItemsApi(row.id)
    itemRows.value = toEditableRows(rows)
    existingItemIds.value = new Set(rows.map((r) => r.id).filter((id): id is number => typeof id === 'number'))
  } catch {
    // 请求层已统一提示
  } finally {
    drawerLoading.value = false
  }
}

/** 追加一行：项次自动取「当前最大项次 + 1」，减少手工排序成本 */
function addItemRow(): void {
  const maxOrder = itemRows.value.reduce((max, r) => Math.max(max, r.itemOrder ?? 0), 0)
  itemRows.value.push({
    productLibId: currentProduct.value?.id,
    itemOrder: maxOrder + 1,
    itemName: '',
    unit: '',
    basisCode: '',
    methods: '',
    stdValue: '',
    judgeType: 1,
    isReference: 0,
    lowerLimit: '',
    methodNote: '',
  })
}

function removeItemRow(index: number): void {
  itemRows.value.splice(index, 1)
}

/**
 * 保存明细（覆盖式）。
 *
 * <p>提交前做**客户端预检**只为给出更快的反馈；真正的一致性校验（判定类型与标准值形态是否匹配）
 * 在后端 `checkJudgeConsistency`，前端不重复实现判定口径——避免两处规则漂移。</p>
 */
async function saveItems(): Promise<void> {
  if (!currentProduct.value) return
  const blank = itemRows.value.findIndex((r) => !r.itemName || !r.itemName.trim())
  if (blank >= 0) {
    ElMessage.warning(`第 ${blank + 1} 行的「检验项目」不能为空`)
    return
  }
  const orders = itemRows.value.map((r) => r.itemOrder)
  if (new Set(orders).size !== orders.length) {
    ElMessage.warning('项次不能重复')
    return
  }
  drawerSaving.value = true
  try {
    const count = await replaceProductLibItemsApi(currentProduct.value.id, itemRows.value)
    ElMessage.success(`已保存，共 ${count} 个检测单项`)
    drawerVisible.value = false
    void load()
  } catch {
    // 请求层已统一提示（含判定类型/标准值不一致的业务错误）
  } finally {
    drawerSaving.value = false
  }
}

// ---------------- Excel 导入 ----------------

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
    const result = await importProductLibApi(file)
    importResult.value = result
    importDialogVisible.value = true
    void load()
  } catch {
    // 请求层已统一提示
  } finally {
    importing.value = false
  }
}

/** 下载导入模板（CSV，13 列与后端 ProductLibImportRow 的 index 绑定一一对应） */
function downloadTemplate(): void {
  const header = [
    '产品编号', '产品名称', '食品大类', '顺序号', '检测项目', '单位', '判定依据标准号',
    '检验方法', '限量值', '判定类型(1限量比较/2不得检出/3文本感官)', '是否参考项(1是/0否)',
    '最低检出限', '方法备注',
  ].join(',')
  const rows = [
    'SC-0001,菠菜,蔬菜,1,铅（以Pb计）,mg/kg,GB 2762,GB 5009.12,0.3,1,0,0.01,',
    'SC-0001,菠菜,蔬菜,2,毒死蜱,mg/kg,GB 2763,GB 23200.113,0.02,1,0,0.005,',
    'SC-0001,菠菜,蔬菜,3,感官,/,GB 2762,感官检验,具有该品种应有的色泽与气味,3,0,,人工判定',
  ].join('\n')
  const blob = new Blob(['\uFEFF' + header + '\n' + rows + '\n'], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '项目标准库导入模板.csv'
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
      title="项目标准库"
      subtitle="维护产品应检项目与判定依据，供项目分解自动加载"
      icon="Files"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>基础数据</el-breadcrumb-item>
          <el-breadcrumb-item>项目标准库</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Download"
        @click="downloadTemplate"
      >
        下载模板
      </el-button>
      <el-button
        v-permission="'base:lib:add'"
        :icon="Upload"
        :loading="importing"
        @click="triggerImport"
      >
        导入新的项目库
      </el-button>
      <el-button
        v-permission="'base:lib:add'"
        type="primary"
        :icon="Plus"
        @click="openCreateProduct"
      >
        新增产品
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
        <el-form-item label="产品编号">
          <el-input
            v-model="query.productCode"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input
            v-model="query.productName"
            placeholder="模糊查询"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="食品大类">
          <el-input
            v-model="query.category"
            placeholder="模糊查询"
            clearable
            style="width: 160px"
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
          prop="productCode"
          label="产品编号"
          width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="productName"
          label="产品名称"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="category"
          label="食品大类"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="itemCount"
          label="检测单项数"
          width="120"
          align="center"
        />
        <el-table-column
          label="操作"
          width="220"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              @click="openItems(row as ProductLibRow)"
            >
              维护单项
            </el-button>
            <el-button
              v-permission="'base:lib:edit'"
              type="primary"
              link
              :icon="Edit"
              @click="openEditProduct(row as ProductLibRow)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'base:lib:remove'"
              type="danger"
              link
              :icon="Delete"
              @click="handleRemoveProduct(row as ProductLibRow)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无项目库数据"
            hint="可通过「导入新的项目库」批量建立，或手动新增产品后维护单项"
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

    <!-- 产品新增/编辑 -->
    <el-dialog
      v-model="productDialogVisible"
      :title="productDialogTitle"
      width="480px"
      :destroy-on-close="true"
    >
      <el-form
        ref="productFormRef"
        :model="productForm"
        :rules="productRules"
        label-width="90px"
      >
        <el-form-item
          label="产品编号"
          prop="productCode"
        >
          <el-input
            v-model="productForm.productCode"
            placeholder="如：SC-0001（项目分解按此匹配）"
          />
        </el-form-item>
        <el-form-item
          label="产品名称"
          prop="productName"
        >
          <el-input
            v-model="productForm.productName"
            placeholder="如：菠菜"
          />
        </el-form-item>
        <el-form-item
          label="食品大类"
          prop="category"
        >
          <el-input
            v-model="productForm.category"
            placeholder="如：蔬菜"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="productSubmitting"
          @click="submitProduct"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 检测单项：整表编辑 + 覆盖式保存 -->
    <el-drawer
      v-model="drawerVisible"
      :title="currentProduct ? `检测单项 — ${currentProduct.productName ?? ''}（${itemRows.length} 项）` : '检测单项'"
      direction="rtl"
      size="82%"
      :destroy-on-close="true"
    >
      <div
        v-loading="drawerLoading"
        class="drawer-body"
      >
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="整表覆盖式保存"
          description="保存时会以当前表格内容整体替换该产品的检测单项：表格里删掉的行会在系统中一并删除。修改标准库不会影响已分解的历史样品。"
          class="mb"
        />
        <div class="drawer-toolbar">
          <el-button
            v-permission="'base:lib:add'"
            type="primary"
            :icon="Plus"
            @click="addItemRow"
          >
            添加一行
          </el-button>
          <span class="drawer-count">共 {{ itemRows.length }} 项</span>
        </div>

        <el-table
          :data="itemRows"
          border
          size="small"
          max-height="520"
        >
          <el-table-column
            label="项次"
            width="80"
          >
            <template #default="{ row }">
              <el-input-number
                v-model="row.itemOrder"
                :min="1"
                :controls="false"
                size="small"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="检验项目"
            min-width="160"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.itemName"
                size="small"
                placeholder="必填"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="判定依据"
            min-width="140"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.basisCode"
                size="small"
                placeholder="如 GB 2762"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="检验方法"
            min-width="170"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.methods"
                size="small"
                placeholder="如 GB 5009.12"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="判定类型"
            width="150"
          >
            <template #default="{ row }">
              <el-select
                v-model="row.judgeType"
                size="small"
                style="width: 100%"
              >
                <el-option
                  v-for="jt in JUDGE_TYPES"
                  :key="jt.value"
                  :label="jt.label"
                  :value="jt.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="标准值"
            min-width="130"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.stdValue"
                size="small"
                :placeholder="row.judgeType === 3 ? '文本描述' : '如 ≤0.3'"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="单位"
            width="90"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.unit"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="最低检出限"
            width="110"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.lowerLimit"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="参考项"
            width="80"
            align="center"
          >
            <template #default="{ row }">
              <el-checkbox
                :model-value="row.isReference === 1"
                @change="(v: boolean | string | number) => (row.isReference = v ? 1 : 0)"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="方法备注"
            min-width="140"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.methodNote"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="70"
            align="center"
            fixed="right"
          >
            <template #default="{ $index }">
              <el-button
                v-permission="'base:lib:remove'"
                type="danger"
                link
                :icon="Delete"
                @click="removeItemRow($index)"
              />
            </template>
          </el-table-column>
          <template #empty>
            <AppEmpty
              title="尚未添加检测单项"
              hint="点击「添加一行」为该产品建立应检项目"
            />
          </template>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="drawerVisible = false">
          取消
        </el-button>
        <el-button
          v-permission="'base:lib:edit'"
          type="primary"
          :loading="drawerSaving"
          @click="saveItems"
        >
          保存（覆盖式）
        </el-button>
      </template>
    </el-drawer>

    <!-- 导入结果 -->
    <el-dialog
      v-model="importDialogVisible"
      title="项目库导入结果"
      width="640px"
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
            <span class="result-label">新建产品</span>
            <span class="result-value ok">{{ importResult.productCount }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">更新产品</span>
            <span class="result-value">{{ importResult.productUpdated }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">写入明细</span>
            <span class="result-value">{{ importResult.itemCount }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">失败行</span>
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
.drawer-body {
  padding: 0 var(--lims-r-xs);
}
.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.drawer-count {
  color: var(--lims-text-secondary);
  font-size: 12px;
}
.mb {
  margin-bottom: 12px;
}
.result-grid {
  display: flex;
  gap: 28px;
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
  max-height: 240px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid var(--lims-border);
  border-radius: var(--lims-r-sm);
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
