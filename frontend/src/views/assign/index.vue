<script setup lang="ts">
/**
 * 检验任务安排页（T-501，api-spec 第 5 章）。
 *
 * <p>列表只显示已分解（S30）的样品；点行进入抽屉：
 * <ol>
 *   <li>查看明细 + 指派状态 + 候选检验员；</li>
 *   <li>「自动分配」按分类规则（编号含 NA/XA/SA → 对应共享检验员）或方法资质（tester_method）匹配；</li>
 *   <li>单行「改派」可对单项选有资质检验员；</li>
 *   <li>全部已指派后，「安排确认」流转 S30 → S40。</li>
 * </ol>
 *
 * <p>设计要点：
 * <ul>
 *   <li>明细只读展示（不在此页调整分解，分解走 /item/decompose），本页专注指派；</li>
 *   <li>候选检验员后端已按当前样品的资质过滤，前端仅展示；</li>
 *   <li>改派后立即刷新该行（按 assignType=3 标记人工改派，后续自动分配不覆盖）；</li>
 *   <li>安排确认按钮在 inputPermitted=true 时启用，否则禁用并 hover 提示原因。</li>
 * </ul>
 * </p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Refresh, Search, User } from '@element-plus/icons-vue'
import {
  ASSIGN_TYPE_OPTIONS,
  CANDIDATE_SOURCE_OPTIONS,
  autoAssignApi,
  confirmAssignApi,
  getAssignDetailApi,
  pagePendingAssignApi,
  reassignApi,
  type AssignDetail,
  type AssignItemRow,
  type AssignPendingRow,
} from '@/api/assign'

// ---------------- 待安排列表 ----------------
const query = reactive({ sampleNo: '', sampleName: '' })
const loading = ref(false)
const tableData = ref<AssignPendingRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

async function loadPending(): Promise<void> {
  loading.value = true
  try {
    const res = await pagePendingAssignApi({
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

// ---------------- 抽屉：安排明细 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const autoAssigning = ref(false)
const confirming = ref(false)
const currentSample = ref<AssignPendingRow | null>(null)
const detail = ref<AssignDetail | null>(null)

const drawerTitle = computed(() =>
  currentSample.value
    ? `任务安排 — ${currentSample.value.sampleNo}（${currentSample.value.sampleName}）`
    : '任务安排',
)

const assignedCount = computed(() => detail.value?.items.filter((i) => i.assignStatus === 1).length ?? 0)

async function openDrawer(row: AssignPendingRow): Promise<void> {
  currentSample.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  await loadDetail(row.id)
  drawerLoading.value = false
}

async function loadDetail(sampleId: number): Promise<void> {
  try {
    detail.value = await getAssignDetailApi(sampleId)
  } catch {
    // 请求层已统一提示
  }
}

/** 指派类型枚举 → tag 类型 */
function assignTypeTag(t: number): 'primary' | 'success' | 'warning' | 'info' {
  const found = ASSIGN_TYPE_OPTIONS.find((o) => o.value === t)
  return (found?.type ?? 'info') as 'primary' | 'success' | 'warning' | 'info'
}

function assignTypeLabel(t: number): string {
  return ASSIGN_TYPE_OPTIONS.find((o) => o.value === t)?.label ?? String(t)
}

function sourceLabel(src: string): string {
  return (CANDIDATE_SOURCE_OPTIONS as Record<string, string>)[src] ?? src
}

/** 自动分配（5.4） */
async function handleAutoAssign(): Promise<void> {
  if (!currentSample.value) return
  await ElMessageBox.confirm(
    '将对该样品所有未人工改派的项按「分类规则」与「方法资质」自动指派，可重跑。',
    '执行自动分配',
    { confirmButtonText: '执行', cancelButtonText: '取消', type: 'info' },
  ).catch(() => null).then(async (ok) => {
    if (ok !== 'confirm') return
    autoAssigning.value = true
    try {
      const res = await autoAssignApi({ sampleId: currentSample.value!.id })
      const pending = res.details.filter((d) => d.assignStatus === 0)
      const ok2 = res.details.length - pending.length
      ElMessage.success(`自动分配完成：${ok2} 项已指派${pending.length ? `，${pending.length} 项仍待人工指派` : ''}`)
      await loadDetail(currentSample.value!.id)
      await loadPending()
    } finally {
      autoAssigning.value = false
    }
  })
}

/** 人工改派（5.5） */
async function handleReassign(item: AssignItemRow, testerNo: string): Promise<void> {
  if (!testerNo) return
  if (item.testerNo === testerNo) return
  await ElMessageBox.confirm(
    `将「${item.itemName}」指派给 ${testerNo}（标记为人工改派，后续自动分配不再覆盖）。`,
    '人工改派',
    { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => null).then(async (ok) => {
    if (ok !== 'confirm') return
    try {
      await reassignApi({ itemId: item.id, testerNo })
      ElMessage.success('改派成功')
      await loadDetail(currentSample.value!.id)
      await loadPending()
    } catch {
      // 请求层已统一提示（含「无资质」拒绝）
    }
  })
}

/** 安排确认（5.6） */
async function handleConfirm(): Promise<void> {
  if (!detail.value || !currentSample.value) return
  if (!detail.value.inputPermitted) {
    ElMessage.warning('仍有未指派项，请先指派或自动分配')
    return
  }
  await ElMessageBox.confirm(
    `确认安排 ${detail.value.items.length} 项检测任务，流转样品状态 S30 → S40？`,
    '安排确认',
    { confirmButtonText: '确认安排', cancelButtonText: '取消', type: 'success' },
  ).catch(() => null).then(async (ok) => {
    if (ok !== 'confirm') return
    confirming.value = true
    try {
      const res = await confirmAssignApi({ sampleId: currentSample.value!.id })
      ElMessage.success(`已确认安排，样品状态：${res.statusLabel}`)
      drawerVisible.value = false
      await loadPending()
    } catch {
      // 请求层已统一提示（含乐观条件冲突）
    } finally {
      confirming.value = false
    }
  })
}

onMounted(() => {
  void loadPending()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div>
        <h2 class="page-title">
          任务安排
        </h2>
        <p class="page-desc">
          阶段五：将已分解（S30）的样品检测单项指派给有资格检验员，确认后流转 S40。
        </p>
      </div>
    </header>

    <!-- 查询卡片 -->
    <el-card
      class="glass-card filter-card"
      shadow="never"
    >
      <el-form
        :inline="true"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="样品编号">
          <el-input
            v-model="query.sampleNo"
            placeholder="模糊匹配"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="样品名称">
          <el-input
            v-model="query.sampleName"
            placeholder="模糊匹配"
            clearable
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
    </el-card>

    <!-- 待安排样品表格 -->
    <el-card
      class="glass-card table-card"
      shadow="never"
    >
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
      >
        <el-table-column
          prop="sampleNo"
          label="样品编号"
          min-width="180"
        />
        <el-table-column
          prop="sampleName"
          label="样品名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="clientName"
          label="委托单位"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="分配进度"
          width="180"
          align="center"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="row.assignTotal === 0 ? 0 : Math.round((row.assignDone / row.assignTotal) * 100)"
              :status="row.assignDone === row.assignTotal && row.assignTotal > 0 ? 'success' : ''"
              :stroke-width="14"
              :text-inside="true"
            />
            <span class="progress-text">{{ row.assignDone }} / {{ row.assignTotal }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="120"
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
              @click="openDrawer(row as AssignPendingRow)"
            >
              任务安排
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="empty-tip">暂无待安排样品（需先在「项目分解」完成分解确认）</span>
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
    </el-card>

    <!-- 安排抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="82%"
      :destroy-on-close="true"
    >
      <div
        v-loading="drawerLoading"
        class="drawer-body"
      >
        <template v-if="detail">
          <!-- 头部：样品摘要 + 进度 + 操作 -->
          <section class="assign-head glass-card">
            <div class="head-grid">
              <div class="head-cell">
                <span class="cell-label">样品状态</span>
                <el-tag
                  type="primary"
                  effect="plain"
                >
                  {{ detail.statusLabel }}
                </el-tag>
              </div>
              <div class="head-cell">
                <span class="cell-label">指派进度</span>
                <div class="cell-progress">
                  <el-progress
                    :percentage="detail.assignTotal === 0 ? 0 : Math.round((assignedCount / detail.assignTotal) * 100)"
                    :stroke-width="14"
                    :text-inside="true"
                  />
                  <span class="progress-text">{{ assignedCount }} / {{ detail.assignTotal }}</span>
                </div>
              </div>
              <div class="head-cell">
                <span class="cell-label">可用操作</span>
                <div class="head-actions">
                  <el-button
                    type="primary"
                    :icon="MagicStick"
                    :loading="autoAssigning"
                    @click="handleAutoAssign"
                  >
                    一键自动分配
                  </el-button>
                  <el-button
                    type="success"
                    :disabled="!detail.inputPermitted"
                    :loading="confirming"
                    @click="handleConfirm"
                  >
                    安排确认 → S40
                  </el-button>
                </div>
                <el-tooltip
                  v-if="!detail.inputPermitted"
                  content="仍有未指派项，请先指派或自动分配"
                  placement="top"
                >
                  <span class="action-hint">需先指派全部单项</span>
                </el-tooltip>
              </div>
            </div>
          </section>

          <!-- 检测单项明细表 -->
          <section class="glass-card">
            <h3 class="section-title">
              检测单项（{{ detail.items.length }}）
            </h3>
            <el-table
              :data="detail.items"
              stripe
              border
            >
              <el-table-column
                prop="itemOrder"
                label="项次"
                width="70"
                align="center"
              />
              <el-table-column
                prop="itemName"
                label="检验项目"
                min-width="160"
                show-overflow-tooltip
              />
              <el-table-column
                prop="methods"
                label="检验方法"
                min-width="160"
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
                label="指派状态"
                width="100"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag
                    :type="row.assignStatus === 1 ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ row.assignStatus === 1 ? '已指派' : '待指派' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="指派方式"
                width="110"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag
                    :type="assignTypeTag(row.assignType)"
                    effect="plain"
                  >
                    {{ assignTypeLabel(row.assignType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="检验员"
                min-width="200"
              >
                <template #default="{ row }">
                  <div
                    v-if="row.assignStatus === 1"
                    class="tester-cell"
                  >
                    <el-icon class="tester-icon">
                      <User />
                    </el-icon>
                    <span class="tester-name">{{ row.testerName || row.testerNo }}</span>
                    <span class="tester-no">({{ row.testerNo }})</span>
                  </div>
                  <el-select
                    v-else
                    :model-value="row.testerNo || ''"
                    placeholder="选择有资质检验员"
                    filterable
                    style="width: 100%"
                    @change="(v: string) => handleReassign(row as unknown as AssignItemRow, v)"
                  >
                    <el-option
                      v-for="c in detail!.candidates"
                      :key="`${c.testerNo}-${c.source}`"
                      :label="`${c.testerName}（${c.testerNo}） · ${sourceLabel(c.source)}`"
                      :value="c.testerNo"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column
                label="指派时间"
                width="170"
                align="center"
              >
                <template #default="{ row }">
                  <span
                    v-if="row.assignedAt"
                    class="muted"
                  >{{ row.assignedAt }}</span>
                  <span
                    v-else
                    class="muted"
                  >—</span>
                </template>
              </el-table-column>
            </el-table>
          </section>

          <!-- 候选检验员说明 -->
          <section class="glass-card candidate-card">
            <h3 class="section-title">
              候选检验员（{{ detail.candidates.length }}）
              <span class="section-hint">仅列出对该样品任一单项具备资质者，无资质者不展示</span>
            </h3>
            <el-table
              :data="detail.candidates"
              stripe
              size="small"
            >
              <el-table-column
                prop="testerNo"
                label="工号"
                width="120"
              />
              <el-table-column
                prop="testerName"
                label="姓名"
                width="120"
              />
              <el-table-column
                label="来源"
                width="100"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag
                    :type="row.source === 'CATEGORY' ? 'primary' : 'success'"
                    effect="plain"
                    size="small"
                  >
                    {{ sourceLabel(row.source) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="matchedMethodNo"
                label="匹配方法"
              >
                <template #default="{ row }">
                  <span v-if="row.matchedMethodNo">{{ row.matchedMethodNo }}</span>
                  <span
                    v-else
                    class="muted"
                  >—</span>
                </template>
              </el-table-column>
              <template #empty>
                <span class="empty-tip">当前样品无任何有资质检验员，请先在「基础数据 → 检验员方法资质」补录资质</span>
              </template>
            </el-table>
          </section>
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
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--lims-r-xs);
}
.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.page-desc {
  margin: 4px 0 0;
  color: var(--lims-text-secondary);
  font-size: 13px;
}
.glass-card {
  border-radius: var(--lims-r-md);
}
.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}
.empty-tip {
  color: var(--lims-text-secondary);
  font-size: 13px;
  padding: var(--lims-r-sm) 0;
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
.assign-head .head-grid {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 2fr minmax(280px, 2fr);
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
.candidate-card {
  margin-top: var(--lims-r-xs);
}

.tester-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tester-icon {
  color: var(--lims-accent);
}
.tester-name {
  font-weight: 500;
}
.tester-no {
  color: var(--lims-text-secondary);
  font-size: 12px;
}
.muted {
  color: var(--lims-text-secondary);
}
</style>