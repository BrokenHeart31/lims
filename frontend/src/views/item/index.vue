<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { MagicStick, Operation, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  JUDGE_TYPE_OPTIONS,
  confirmItemApi,
  listItemApi,
  matchItemApi,
  pagePendingItemApi,
  saveItemApi,
  type ItemMatchResult,
  type ItemPendingRow,
  type SampleItem,
} from '@/api/item'
import { confirm } from '@/utils/confirm'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'

// ---------------- 待分解样品列表 ----------------
const queryRef = ref<FormInstance>()
const query = reactive({ sampleNo: '', sampleName: '' })
const loading = ref(false)
const tableData = ref<ItemPendingRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

async function loadPending(): Promise<void> {
  loading.value = true
  try {
    const res = await pagePendingItemApi({
      current: current.value,
      size: size.value,
      sampleNo: query.sampleNo || undefined,
      sampleName: query.sampleName || undefined,
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
  current.value = 1
  void loadPending()
}

function handleReset(): void {
  query.sampleNo = ''
  query.sampleName = ''
  handleSearch()
}

function handlePageChange(p: number): void {
  current.value = p
  void loadPending()
}

function handleSizeChange(s: number): void {
  size.value = s
  current.value = 1
  void loadPending()
}

// ---------------- 分解抽屉 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const saving = ref(false)
const confirming = ref(false)
const currentSample = ref<ItemPendingRow | null>(null)
const matchResult = ref<ItemMatchResult | null>(null)
/** 可编辑的明细网格（套库初稿 + 人工调整） */
const editorItems = ref<SampleItem[]>([])
/** 是否有未保存的改动 */
const dirty = ref(false)

const drawerTitle = computed(() =>
  currentSample.value
    ? `项目分解 — ${currentSample.value.sampleNo}（${currentSample.value.sampleName}）`
    : '项目分解',
)

const refCount = computed(() => editorItems.value.filter((i) => i.isReference === 1).length)

async function openDrawer(row: ItemPendingRow): Promise<void> {
  currentSample.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  editorItems.value = []
  matchResult.value = null
  dirty.value = false
  try {
    // 优先取已保存明细；无则自动套库生成初稿
    const saved = await listItemApi(row.id)
    if (saved.items.length > 0) {
      editorItems.value = saved.items.map((i) => ({ ...i }))
    } else {
      await doMatch(row.id)
    }
  } catch {
    // 请求层已统一提示
  } finally {
    drawerLoading.value = false
  }
}

/** 套库：拉取标准库初稿（不落库） */
async function doMatch(sampleId?: number): Promise<void> {
  const id = sampleId ?? currentSample.value?.id
  if (!id) return
  drawerLoading.value = true
  try {
    const res = await matchItemApi(id)
    matchResult.value = res
    if (res.matched) {
      editorItems.value = res.items.map((m) => ({
        sampleId: res.sampleId,
        sampleNo: currentSample.value?.sampleNo,
        itemOrder: m.itemOrder,
        itemName: m.itemName,
        libItemId: m.libItemId,
        unit: m.unit,
        basisCode: m.basisCode,
        methods: m.methods,
        stdValue: m.stdValue,
        judgeType: m.judgeType,
        isReference: m.isReference,
        lowerLimit: m.lowerLimit,
        methodNote: m.methodNote,
        sourceType: 1,
        remark: null,
      }))
      dirty.value = true
      if (res.candidates.length > 1) {
        ElMessage.warning(
          `匹配到 ${res.candidates.length} 个候选产品，已默认选用「${res.matchedProductName}」，请核对第 ${res.matchedLibId} 号产品库`,
        )
      } else {
        ElMessage.success(`已从项目库自动加载 ${res.items.length} 个检测单项`)
      }
    } else {
      editorItems.value = []
      ElMessage.warning('未找到匹配的产品标准库，请人工添加检测单项')
    }
  } catch {
    // 请求层已统一提示
  } finally {
    drawerLoading.value = false
  }
}

/** 人工新增一行 */
function handleAddRow(): void {
  const nextOrder = editorItems.value.length === 0
    ? 1
    : Math.max(...editorItems.value.map((i) => i.itemOrder)) + 1
  editorItems.value.push({
    sampleId: currentSample.value?.id ?? 0,
    sampleNo: currentSample.value?.sampleNo,
    itemOrder: nextOrder,
    itemName: '',
    libItemId: null,
    unit: null,
    basisCode: null,
    methods: null,
    stdValue: null,
    judgeType: 1,
    isReference: 0,
    lowerLimit: null,
    methodNote: null,
    sourceType: 2,
    remark: null,
  })
  dirty.value = true
}

/** 删除一行并重排序号（保证项次连续唯一） */
function handleRemoveRow(index: number): void {
  editorItems.value.splice(index, 1)
  resequence()
  dirty.value = true
}

/** 项次重排（从 1 连续编号，满足后端「项次样品内唯一」约束） */
function resequence(): void {
  editorItems.value.forEach((it, idx) => {
    it.itemOrder = idx + 1
  })
}

/** 保存分解（覆盖式） */
async function handleSave(): Promise<void> {
  const sampleId = currentSample.value?.id
  if (!sampleId) return
  if (editorItems.value.length === 0) {
    ElMessage.warning('请至少保留一个检验项目')
    return
  }
  const blank = editorItems.value.find((i) => !i.itemName || !i.itemName.trim())
  if (blank) {
    ElMessage.warning(`第 ${blank.itemOrder} 项的检验项目名称不能为空`)
    return
  }
  saving.value = true
  try {
    resequence()
    const res = await saveItemApi(sampleId, editorItems.value)
    dirty.value = false
    ElMessage.success(`分解已保存，共 ${res.itemCount} 个检测单项`)
    void loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    saving.value = false
  }
}

/** 确认保存 → S20→S30 */
async function handleConfirm(): Promise<void> {
  const sampleId = currentSample.value?.id
  if (!sampleId) return
  if (dirty.value) {
    ElMessage.warning('有未保存的改动，请先保存再确认')
    return
  }
  if (editorItems.value.length === 0) {
    ElMessage.warning('请先完成项目分解再确认')
    return
  }
  const ok = await confirm({
    title: '分解确认',
    message: '确认保存后样品将进入「任务安排」流程，且不可再修改分解结果。是否继续？',
    tone: 'warning',
    confirmText: '确认保存',
  })
  if (!ok) return
  confirming.value = true
  try {
    const res = await confirmItemApi(sampleId)
    ElMessage.success(`分解确认成功，样品状态：${res.statusLabel}`)
    drawerVisible.value = false
    void loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    confirming.value = false
  }
}

function handleCloseDrawer(): void {
  if (dirty.value) {
    ElMessage.info('存在未保存的改动，已放弃')
  }
  dirty.value = false
}

function handleEditChange(): void {
  dirty.value = true
}

onMounted(() => {
  void loadPending()
})
</script>

<template>
  <div class="item-decompose">
    <PageHeader
      title="项目分解"
      subtitle="从项目标准库自动套用或人工编辑检测单项，确认后样品进入任务安排流程（S20 → S30）"
      :icon="Operation"
    />

    <!-- 查询区 -->
    <AppCard
      variant="panel"
      padding="18px 22px"
    >
      <el-form
        ref="queryRef"
        :model="query"
        inline
      >
        <el-form-item label="样品编号">
          <el-input
            v-model="query.sampleNo"
            placeholder="模糊查询"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样品名称">
          <el-input
            v-model="query.sampleName"
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

    <!-- 待分解清单 -->
    <AppCard
      variant="panel"
      padding="0"
    >
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">待分解样品（登记确认 S20）</span>
          <span class="toolbar-sub">共 {{ total }} 条</span>
        </div>
      </div>
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        height="calc(100vh - 360px)"
      >
        <el-table-column
          type="index"
          label="#"
          width="55"
          align="center"
        />
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="sampleName"
          label="样品名称"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          prop="clientName"
          label="受检单位"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="taskNo"
          label="任务编号"
          width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="inspectType"
          label="检验类别"
          width="110"
        />
        <el-table-column
          prop="samplingDate"
          label="采样日期"
          width="115"
        />
        <el-table-column
          label="分解进度"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              v-if="row.itemCount > 0"
              tone="success"
            >
              {{ row.itemCount }} 项
            </StatusBadge>
            <StatusBadge
              v-else
              tone="blank"
            >
              未分解
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge tone="purple">
              {{ row.statusLabel }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="110"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              @click="openDrawer(row as ItemPendingRow)"
            >
              项目分解
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无待分解样品"
            hint="样品需先完成「登记确认」（S20）才会出现在此列表"
          />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="current"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </AppCard>

    <!-- 分解抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="78%"
      :close-on-click-modal="false"
      @closed="handleCloseDrawer"
    >
      <div
        v-loading="drawerLoading"
        class="drawer-body"
      >
        <!-- 工具栏 -->
        <div class="toolbar">
          <el-button
            type="primary"
            :icon="MagicStick"
            @click="doMatch()"
          >
            从项目库自动套用
          </el-button>
          <el-button
            :icon="Plus"
            @click="handleAddRow"
          >
            新增检测单项
          </el-button>
          <span class="spacer" />
          <StatusBadge tone="info">
            共 {{ editorItems.length }} 项
          </StatusBadge>
          <StatusBadge
            v-if="refCount > 0"
            tone="warning"
          >
            参考项 {{ refCount }} 项
          </StatusBadge>
          <StatusBadge
            v-if="dirty"
            tone="danger"
          >
            有未保存改动
          </StatusBadge>
        </div>

        <el-alert
          v-if="matchResult && !matchResult.matched && editorItems.length === 0"
          type="warning"
          :closable="false"
          show-icon
          title="未找到匹配的产品标准库"
          description="请确认样品名称与项目库产品名一致，或点击「新增检测单项」人工录入。"
          class="match-alert"
        />

        <!-- 明细编辑表 -->
        <el-table
          :data="editorItems"
          border
          stripe
          size="small"
          height="calc(100vh - 300px)"
          class="editor-table"
        >
          <el-table-column
            label="项次"
            width="70"
            align="center"
          >
            <template #default="{ row }">
              <span class="order-cell">{{ row.itemOrder }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="检验项目"
            min-width="200"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.itemName"
                size="small"
                placeholder="必填"
                @change="handleEditChange"
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
                @change="handleEditChange"
              >
                <el-option
                  v-for="o in JUDGE_TYPE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="标准值"
            width="110"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.stdValue"
                size="small"
                placeholder="如 0.5"
                @change="handleEditChange"
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
                @change="handleEditChange"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="检出限"
            width="105"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.lowerLimit"
                size="small"
                @change="handleEditChange"
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
                @change="handleEditChange"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="检验方法"
            min-width="160"
          >
            <template #default="{ row }">
              <el-input
                v-model="row.methods"
                size="small"
                placeholder="多个以 # 分隔"
                @change="handleEditChange"
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
                @change="(v: boolean | string | number) => { row.isReference = v ? 1 : 0; handleEditChange() }"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="来源"
            width="90"
            align="center"
          >
            <template #default="{ row }">
              <StatusBadge :tone="row.sourceType === 1 ? 'info' : 'warning'">
                {{ row.sourceType === 1 ? '标准库' : '人工' }}
              </StatusBadge>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="80"
            fixed="right"
            align="center"
          >
            <template #default="{ $index }">
              <el-button
                type="danger"
                link
                @click="handleRemoveRow($index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <AppEmpty
              title="暂无检测单项"
              hint="请先「从项目库自动套用」或「新增检测单项」"
            />
          </template>
        </el-table>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">
            取消
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            保存分解
          </el-button>
          <el-button
            type="success"
            :loading="confirming"
            @click="handleConfirm"
          >
            确认保存（进入任务安排）
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.item-decompose {
  display: flex;
  flex-direction: column;
  gap: var(--lims-page-gap);
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 22px;
  border-bottom: 1px solid var(--lims-hair);
}
.toolbar-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.toolbar-title {
  color: var(--lims-ink);
  font-size: var(--lims-fs-base);
  font-weight: 600;
}
.toolbar-sub {
  color: var(--lims-faint);
  font-size: var(--lims-fs-xs);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 22px;
  border-top: 1px solid var(--lims-hair);
}
.drawer-body {
  padding: 0 4px;
}
.toolbar .spacer {
  flex: 1;
}
.match-alert {
  margin-bottom: 12px;
}
.editor-table {
  width: 100%;
}
.order-cell {
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
