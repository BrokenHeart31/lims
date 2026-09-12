<script setup lang="ts">
/**
 * 检验数据录入页（T-601，api-spec 第 6 章）。
 *
 * <p>列表只显示已安排 / 检验中（S40/S50）的样品；点行进入抽屉：
 * <ol>
 *   <li>按检测单项逐条录入「检验结果」；jt1/jt2 由**判定引擎自动判定**，jt3 感官项由检验员选合格/不合格；</li>
 *   <li>输入时调 <code>/result/judge</code> 实时预览结论与判定依据（不落库）；</li>
 *   <li>「保存录入」可分次保存（首次保存流转 S40→S50）；</li>
 *   <li>全部录齐后「提交」流转 S50→S60（检验完成）。</li>
 * </ol>
 * </p>
 *
 * <p><b>设计要点（AGENTS 0.2 / 7.3）</b>：单项结论**一律由后端引擎产出**，前端只展示、绝不自算；
 * 「待判定」以醒目样式提示，需人工关注（数据缺口 / 闭集外输入）。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Promotion, Refresh, Search } from '@element-plus/icons-vue'
import { JUDGE_TYPE_OPTIONS } from '@/api/item'
import {
  getResultDetailApi,
  judgeResultApi,
  pagePendingResultApi,
  saveResultApi,
  submitResultApi,
  type ResultDetail,
  type ResultDetailItem,
  type ResultJudgeResult,
  type ResultPendingRow,
  type ResultSaveItemPayload,
} from '@/api/result'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import { askConfirm } from '@/utils/confirm'

function statusTone(label?: string): 'success' | 'warning' | 'info' | 'neutral' | 'pending' | 'purple' {
  if (!label) return 'neutral'
  if (label.includes('已完成') || label.includes('检验完成') || label.includes('S60')) return 'success'
  if (label.includes('录入中') || label.includes('S40') || label.includes('S50')) return 'pending'
  if (label.includes('待判定')) return 'warning'
  if (label.includes('签发')) return 'purple'
  return 'info'
}

/** 单项录入草稿 */
interface DraftItem {
  testValue: string
  manualConclusion: number | null
}

// ---------------- 待录入列表 ----------------
const query = reactive({ sampleNo: '', sampleName: '' })
const loading = ref(false)
const tableData = ref<ResultPendingRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

async function loadPending(): Promise<void> {
  loading.value = true
  try {
    const res = await pagePendingResultApi({
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

// ---------------- 抽屉：结果录入 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const currentSample = ref<ResultPendingRow | null>(null)
const detail = ref<ResultDetail | null>(null)

/** 录入草稿：itemId → { testValue, manualConclusion } */
const draft = ref<Record<number, DraftItem>>({})
/** 实时判定结果：itemId → 判定输出（未预览时回落到已落库的结论） */
const judged = ref<Record<number, ResultJudgeResult>>({})
/** 正在判定中的项（避免并发预览） */
const judging = ref<Record<number, boolean>>({})

const drawerTitle = computed(() =>
  currentSample.value
    ? `结果录入 — ${currentSample.value.sampleNo}（${currentSample.value.sampleName ?? ''}）`
    : '结果录入',
)

/** 待保存项：草稿中有值的项 */
const dirtyItems = computed<ResultSaveItemPayload[]>(() => {
  if (!detail.value) return []
  const out: ResultSaveItemPayload[] = []
  for (const item of detail.value.items) {
    const d = draft.value[item.id]
    if (!d) continue
    const text = d.testValue.trim()
    if (item.judgeType === 3) {
      // 感官项：选择人工结论即视为已录入（检验值为描述性文本，可空）
      if (d.manualConclusion == null && text === '') continue
      out.push({ itemId: item.id, testValue: text || null, manualConclusion: d.manualConclusion })
    } else if (text !== '') {
      out.push({ itemId: item.id, testValue: text, manualConclusion: null })
    }
  }
  return out
})

/** 全部单项是否已录入（用于「提交」按钮可用性） */
const allEntered = computed(() => {
  if (!detail.value || detail.value.items.length === 0) return false
  return detail.value.items.every((item) => conclusionOf(item) != null)
})

async function openDrawer(row: ResultPendingRow): Promise<void> {
  currentSample.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  await loadDetail(row.id)
  drawerLoading.value = false
}

async function loadDetail(sampleId: number): Promise<void> {
  try {
    const res = await getResultDetailApi(sampleId)
    detail.value = res
    initDraft(res)
  } catch {
    // 请求层已统一提示
  }
}

/** 用已落库结果初始化草稿，便于检验员在既有值上修正 */
function initDraft(res: ResultDetail): void {
  const drafts: Record<number, DraftItem> = {}
  const cache: Record<number, ResultJudgeResult> = {}
  for (const item of res.items) {
    drafts[item.id] = {
      testValue: item.testValue ?? '',
      manualConclusion: item.judgeType === 3 ? (item.conclusion ?? null) : null,
    }
    if (item.entered && item.conclusion != null) {
      cache[item.id] = {
        itemId: item.id,
        itemName: item.itemName,
        unit: item.unit,
        stdValue: item.stdValue,
        lowerLimit: item.lowerLimit,
        judgeType: item.judgeType,
        testValue: item.testValue,
        conclusion: item.conclusion,
        conclusionLabel: item.conclusionLabel ?? '',
        conclusionSource: item.conclusionSource ?? 1,
        conclusionSourceLabel: item.conclusionSourceLabel ?? '',
        judgeBasis: item.judgeBasis ?? '',
      }
    }
  }
  draft.value = drafts
  judged.value = cache
  judging.value = {}
}

/** 单项当前结论（优先实时预览，其次已落库） */
function conclusionOf(item: ResultDetailItem): number | null {
  const live = judged.value[item.id]
  if (live) return live.conclusion
  return item.entered && item.conclusion != null ? item.conclusion : null
}

/**
 * el-table 作用域插槽的 row 为宽松类型（Element Plus `DefaultRow`），
 * 模板内统一经此收窄为明细项类型，避免把 DefaultRow 直接传入强类型函数。
 */
function rowItem(row: unknown): ResultDetailItem {
  return row as ResultDetailItem
}

function basisOf(item: ResultDetailItem): string {
  const live = judged.value[item.id]
  if (live) return live.judgeBasis
  return item.judgeBasis ?? ''
}

function sourceLabelOf(item: ResultDetailItem): string {
  const live = judged.value[item.id]
  if (live) return live.conclusionSourceLabel
  return item.conclusionSourceLabel ?? ''
}

function conclusionLabel(code: number | null): string {
  if (code === 1) return '合格'
  if (code === 2) return '不合格'
  if (code === 3) return '待判定'
  return '未录入'
}

function conclusionTone(code: number | null): 'success' | 'danger' | 'warning' | 'pending' | 'neutral' {
  // 1=合格 2=不合格 3=待判定 其他=未录入
  if (code === 1) return 'success'
  if (code === 2) return 'danger'
  if (code === 3) return 'pending'
  return 'neutral'
}

function judgeTypeLabel(item: ResultDetailItem): string {
  return item.judgeTypeLabel ?? JUDGE_TYPE_OPTIONS.find((o) => o.value === item.judgeType)?.label ?? '—'
}

/** 实时判定预览（失焦 / 选择变化时触发；不落库） */
async function handleJudge(item: ResultDetailItem): Promise<void> {
  const d = draft.value[item.id]
  if (!d || judging.value[item.id]) return
  const text = d.testValue.trim()
  if (item.judgeType === 3) {
    if (d.manualConclusion == null) {
      const next = { ...judged.value }
      delete next[item.id]
      judged.value = next
      return
    }
  } else if (text === '') {
    const next = { ...judged.value }
    delete next[item.id]
    judged.value = next
    return
  }

  judging.value = { ...judging.value, [item.id]: true }
  try {
    const res = await judgeResultApi(item.id, text || null, item.judgeType === 3 ? d.manualConclusion : null)
    judged.value = { ...judged.value, [item.id]: res }
  } catch {
    // 请求层已统一提示；保留旧预览
  } finally {
    const next = { ...judging.value }
    delete next[item.id]
    judging.value = next
  }
}

async function handleSave(): Promise<void> {
  if (!detail.value) return
  const items = dirtyItems.value
  if (items.length === 0) {
    ElMessage.warning('请先录入至少一个检测单项的检验结果')
    return
  }
  saving.value = true
  try {
    const res = await saveResultApi(detail.value.sampleId, items)
    ElMessage.success(`已保存 ${items.length} 项结果，当前整体结论：${res.conclusionLabel}`)
    await loadDetail(res.sampleId)
    await loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    saving.value = false
  }
}

async function handleSubmit(): Promise<void> {
  if (!detail.value) return
  const sampleId = detail.value.sampleId
  if (!(await askConfirm('提交后样品将流转为「检验完成」，结果不能再修改。确定提交吗？', '提交检验结果', { type: 'warning' }))) return
  submitting.value = true
  try {
    const res = await submitResultApi(sampleId)
    ElMessage.success(`已提交，整体结论：${res.conclusionLabel}（${res.statusLabel}）`)
    drawerVisible.value = false
    await loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadPending()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="结果录入"
      subtitle="按检测单项录入检验结果，单项结论由判定引擎自动生成（感官项目由检验员判定）"
      icon="Promotion"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>实验室业务</el-breadcrumb-item>
          <el-breadcrumb-item>结果录入</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="loadPending"
      >
        刷新
      </el-button>
    </PageHeader>

    <!-- 查询条件 -->
    <AppCard
      variant="panel"
      :padding="20"
    >
      <el-form inline>
        <el-form-item label="样品编号">
          <el-input
            v-model="query.sampleNo"
            placeholder="支持模糊查询"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样品名称">
          <el-input
            v-model="query.sampleName"
            placeholder="支持模糊查询"
            clearable
            style="width: 200px"
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

    <!-- 待录入样品列表 -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        @row-dblclick="openDrawer"
      >
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          min-width="160"
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
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="录入进度"
          width="200"
          align="center"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="row.itemTotal === 0 ? 0 : Math.round((row.enteredCount / row.itemTotal) * 100)"
              :status="row.enteredCount === row.itemTotal && row.itemTotal > 0 ? 'success' : ''"
              :stroke-width="14"
              :text-inside="true"
            />
            <span class="progress-text">{{ row.enteredCount }} / {{ row.itemTotal }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="整体结论"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="conclusionTone(row.conclusion ?? null)"
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
            <el-tag
              type="primary"
              effect="plain"
            >
              {{ row.statusLabel }}
            </el-tag>
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
              @click="openDrawer(row as ResultPendingRow)"
            >
              结果录入
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty description="暂无待录入样品（需先在「任务安排」完成安排确认）" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </AppCard>

    <!-- 录入抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="86%"
      :destroy-on-close="true"
    >
      <div
        v-loading="drawerLoading"
        class="drawer-body"
      >
        <template v-if="detail">
          <!-- 头部：样品摘要 + 进度 + 操作 -->
          <AppCard variant="glass">
            <div class="assign-head">
              <div class="head-cell">
                <span class="cell-label">样品状态</span>
                <StatusBadge
                  :tone="statusTone(detail.statusLabel)"
                  size="md"
                >
                  {{ detail.statusLabel }}
                </StatusBadge>
              </div>
              <div class="head-cell">
                <span class="cell-label">录入进度</span>
                <div class="cell-progress">
                  <el-progress
                    :percentage="detail.itemTotal === 0 ? 0 : Math.round((detail.enteredCount / detail.itemTotal) * 100)"
                    :stroke-width="14"
                    :text-inside="true"
                  />
                  <span class="progress-text">{{ detail.enteredCount }} / {{ detail.itemTotal }}</span>
                </div>
              </div>
              <div class="head-cell">
                <span class="cell-label">整体结论</span>
                <div class="cell-conclusion">
                  <StatusBadge
                    :tone="conclusionTone(detail.conclusion ?? null)"
                    size="md"
                  >
                    {{ detail.conclusionLabel ?? '—' }}
                  </StatusBadge>
                  <span class="conclusion-hint">参考项不计入整体结论</span>
                </div>
              </div>
              <div class="head-cell">
                <span class="cell-label">可用操作</span>
                <div class="head-actions">
                  <el-button
                    type="primary"
                    :icon="Check"
                    :loading="saving"
                    :disabled="!detail.allowEdit"
                    @click="handleSave"
                  >
                    保存录入
                  </el-button>
                  <el-button
                    type="success"
                    :icon="Promotion"
                    :loading="submitting"
                    :disabled="!detail.allowEdit || !allEntered"
                    @click="handleSubmit"
                  >
                    提交 → 检验完成
                  </el-button>
                </div>
                <span
                  v-if="!allEntered"
                  class="action-hint"
                >全部单项录入后方可提交</span>
              </div>
            </div>
          </AppCard>

          <!-- 检测单项录入表 -->
          <AppCard
            variant="panel"
            :padding="16"
          >
            <h3 class="section-title">
              检测单项（{{ detail.items.length }}）
              <span class="section-hint">jt1/jt2 由引擎自动判定；jt3 感官项请选择结论</span>
            </h3>
            <el-table
              :data="detail.items"
              stripe
            >
              <el-table-column
                prop="itemOrder"
                label="项次"
                width="64"
                align="center"
              />
              <el-table-column
                prop="itemName"
                label="检验项目"
                min-width="170"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span>{{ row.itemName }}</span>
                  <StatusBadge
                    v-if="row.isReference === 1"
                    tone="warning"
                    size="sm"
                    class="ref-tag"
                  >
                    参考
                  </StatusBadge>
                </template>
              </el-table-column>
              <el-table-column
                label="判定依据"
                min-width="150"
              >
                <template #default="{ row }">
                  <span v-if="row.stdValue">{{ row.stdValue }} {{ row.unit ?? '' }}</span>
                  <span
                    v-else
                    class="muted"
                  >—</span>
                  <div
                    v-if="row.basisCode"
                    class="sub-text"
                  >
                    {{ row.basisCode }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="检出限"
                width="90"
                align="center"
              >
                <template #default="{ row }">
                  <span v-if="row.lowerLimit">{{ row.lowerLimit }}</span>
                  <span
                    v-else
                    class="muted"
                  >—</span>
                </template>
              </el-table-column>
              <el-table-column
                label="判定类型"
                width="130"
                align="center"
              >
                <template #default="{ row }">
                  <span class="muted">{{ judgeTypeLabel(rowItem(row)) }}</span>
                </template>
              </el-table-column>
              <el-table-column
                label="检验结果"
                width="220"
              >
                <template #default="{ row }">
                  <el-select
                    v-if="row.judgeType === 3"
                    v-model="draft[row.id].manualConclusion"
                    placeholder="请选择结论"
                    style="width: 100%"
                    :disabled="!detail.allowEdit"
                    @change="handleJudge(rowItem(row))"
                  >
                    <el-option
                      :value="1"
                      label="合格"
                    />
                    <el-option
                      :value="2"
                      label="不合格"
                    />
                  </el-select>
                  <el-input
                    v-else
                    v-model="draft[row.id].testValue"
                    placeholder="数值或「未检出」"
                    clearable
                    :disabled="!detail.allowEdit"
                    @blur="handleJudge(rowItem(row))"
                    @keyup.enter="handleJudge(rowItem(row))"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="单项结论"
                width="230"
              >
                <template #default="{ row }">
                  <div class="conclusion-cell">
                    <StatusBadge
                      :tone="conclusionTone(conclusionOf(rowItem(row)))"
                      size="sm"
                    >
                      {{ conclusionLabel(conclusionOf(rowItem(row))) }}
                    </StatusBadge>
                    <StatusBadge
                      v-if="conclusionOf(rowItem(row)) != null && sourceLabelOf(rowItem(row))"
                      tone="info"
                      size="sm"
                    >
                      {{ sourceLabelOf(rowItem(row)) }}
                    </StatusBadge>
                    <span
                      v-if="judging[row.id]"
                      class="muted"
                    >判定中…</span>
                  </div>
                  <div
                    v-if="basisOf(rowItem(row))"
                    class="basis-text"
                    :title="basisOf(rowItem(row))"
                  >
                    {{ basisOf(rowItem(row)) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="检验方法"
                min-width="150"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span v-if="row.methods">{{ row.methods }}</span>
                  <span
                    v-else
                    class="muted"
                  >—</span>
                </template>
              </el-table-column>
              <el-table-column
                label="检验员"
                width="120"
              >
                <template #default="{ row }">
                  <span v-if="row.testerName">{{ row.testerName }}</span>
                  <span
                    v-else
                    class="muted"
                  >—</span>
                </template>
              </el-table-column>
            </el-table>
          </AppCard>
        </template>
      </div>
    </el-drawer>
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
.progress-text {
  margin-left: 6px;
  font-size: 12px;
  color: var(--lims-text-secondary);
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
  padding: 0 var(--lims-r-xs);
}
.assign-head {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 2fr minmax(140px, 1fr) minmax(300px, 2fr);
  gap: var(--lims-r-md);
  align-items: center;
}
.head-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cell-label {
  font-size: 12px;
  color: var(--lims-text-secondary);
}
.cell-progress {
  display: flex;
  align-items: center;
  gap: var(--lims-r-xs);
}
.cell-conclusion {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.conclusion-hint {
  font-size: 11px;
  color: var(--lims-text-secondary);
}
.head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.action-hint {
  font-size: 12px;
  color: var(--lims-color-warning);
}
.section-title {
  margin: 0 0 var(--lims-r-sm);
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: var(--lims-r-xs);
}
.section-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--lims-text-secondary);
}
.ref-tag {
  margin-left: 6px;
}
.sub-text {
  font-size: 11px;
  color: var(--lims-text-secondary);
}
.conclusion-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.basis-text {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.4;
  color: var(--lims-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.muted {
  color: var(--lims-text-secondary);
}
</style>
