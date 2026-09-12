<script setup lang="ts">
/**
 * 检验报告审核 / 签发页（T-701，api-spec 第 7 章）。
 *
 * <p>两个页签：**待审核**（S60 检验完成）/ **待签发**（S70 已审核）。点行进入抽屉：
 * <ol>
 *   <li>查看样品摘要、整体结论、**异常项清单**（未录入 / 待判定）与全部单项结果及判定依据；</li>
 *   <li><b>审核通过</b>（S60→S70）：存在异常项时**必须勾选确认**才能放行（放行红线，T-912）；</li>
 *   <li><b>审核退回</b>（S60→S50）：填退回原因，样品回到检验员工作台重录；</li>
 *   <li><b>签发</b>（S70→S80）。</li>
 * </ol>
 * </p>
 *
 * <p><b>设计红线</b>：异常项清单必须在审核人点「通过」之前**可见且被显式确认**——
 * 「有异常仍放行」应当是一个有意识、有留痕的决定，而不是被忽略的默认值。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Refresh, Search, Stamp, Warning } from '@element-plus/icons-vue'
import {
  ABNORMAL_TYPE_PENDING,
  approveAuditApi,
  getAuditDetailApi,
  pagePendingAuditApi,
  pagePendingSignApi,
  returnAuditApi,
  signReportApi,
  type AuditDetail,
  type AuditItem,
  type AuditPendingRow,
} from '@/api/report'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'

function statusTone(label?: string): 'success' | 'warning' | 'info' | 'neutral' | 'pending' | 'purple' {
  if (!label) return 'neutral'
  if (label.includes('签发') || label.includes('S80')) return 'purple'
  if (label.includes('审核') || label.includes('S70')) return 'info'
  if (label.includes('完成') || label.includes('S60')) return 'success'
  return 'pending'
}
function conclusionTone(code: number | null | undefined): 'success' | 'danger' | 'pending' | 'neutral' {
  if (code === 1) return 'success'
  if (code === 2) return 'danger'
  if (code === 3) return 'pending'
  return 'neutral'
}

// ---------------- 列表（两个页签） ----------------
type TabName = 'audit' | 'sign'

const route = useRoute()

const activeTab = ref<TabName>('audit')
const query = reactive({ sampleNo: '', sampleName: '' })
const loading = ref(false)
const tableData = ref<AuditPendingRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

const isAuditTab = computed(() => activeTab.value === 'audit')

async function loadPending(): Promise<void> {
  loading.value = true
  try {
    const params = {
      current: current.value,
      size: size.value,
      sampleNo: query.sampleNo || undefined,
      sampleName: query.sampleName || undefined,
    }
    const res = isAuditTab.value ? await pagePendingAuditApi(params) : await pagePendingSignApi(params)
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function handleTabChange(): void {
  current.value = 1
  void loadPending()
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

// ---------------- 抽屉：明细与动作 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const acting = ref(false)
const detail = ref<AuditDetail | null>(null)

/** 审核通过表单 */
const approveForm = reactive({ opinion: '', abnormalConfirmed: false })
/** 退回表单 */
const returnForm = reactive({ reason: '' })
/** 签发表单 */
const signForm = reactive({ opinion: '' })

const drawerTitle = computed(() =>
  detail.value ? `审核签发 — ${detail.value.sampleNo}（${detail.value.sampleName ?? ''}）` : '审核签发',
)

/** 存在异常项（未录入 / 待判定） */
const hasAbnormal = computed(() => (detail.value?.abnormalCount ?? 0) > 0)

/** 未录入项（更严重：必须补录） */
const blankItems = computed(
  () => detail.value?.abnormalItems.filter((a) => a.type !== ABNORMAL_TYPE_PENDING) ?? [],
)

/** 待判定项（有值但引擎判不出，需人工裁决） */
const pendingItems = computed(
  () => detail.value?.abnormalItems.filter((a) => a.type === ABNORMAL_TYPE_PENDING) ?? [],
)

/** 「审核通过」按钮是否可点（有异常项时必须先勾选确认——放行红线） */
const approveEnabled = computed(() => {
  if (!detail.value?.allowAudit) return false
  if (hasAbnormal.value && !approveForm.abnormalConfirmed) return false
  return true
})

async function openDrawer(row: AuditPendingRow): Promise<void> {
  drawerVisible.value = true
  drawerLoading.value = true
  detail.value = null
  await loadDetail(row.id)
  drawerLoading.value = false
}

async function loadDetail(sampleId: number): Promise<void> {
  try {
    const res = await getAuditDetailApi(sampleId)
    detail.value = res
    approveForm.opinion = ''
    // 无异常项时默认视为已确认（不要求用户做无意义的勾选）
    approveForm.abnormalConfirmed = res.abnormalCount === 0
    returnForm.reason = ''
    signForm.opinion = ''
  } catch {
    // 请求层已统一提示
  }
}

function conclusionTagType(code: number | null | undefined): 'success' | 'danger' | 'warning' | 'info' {
  if (code === 1) return 'success'
  if (code === 2) return 'danger'
  if (code === 3) return 'warning'
  return 'info'
}

function conclusionLabel(item: AuditItem): string {
  if (!item.entered) return '未录入'
  return item.conclusionLabel ?? '—'
}

function itemConclusionType(item: AuditItem): 'success' | 'danger' | 'warning' | 'info' {
  if (!item.entered) return 'info'
  return conclusionTagType(item.conclusion)
}

/** el-table 作用域插槽 row 为宽松类型（Element Plus DefaultRow）→ 统一收窄 */
function rowItem(row: unknown): AuditPendingRow {
  return row as AuditPendingRow
}

/** el-table 作用域插槽 row（明细项）→ 统一收窄 */
function rowAuditItem(row: unknown): AuditItem {
  return row as AuditItem
}

// ---------------- 动作 ----------------

async function handleApprove(): Promise<void> {
  if (!detail.value) return
  if (hasAbnormal.value && !approveForm.abnormalConfirmed) {
    ElMessage.warning('存在异常项，请先勾选「我已逐项确认异常项清单」')
    return
  }
  const tip = hasAbnormal.value
    ? `该样品存在 ${detail.value.abnormalCount} 个异常项（未录入/待判定），确认后仍要放行吗？`
    : '确认审核通过？样品将流转为「已审核」。'
  try {
    await ElMessageBox.confirm(tip, '审核通过', {
      confirmButtonText: '确认通过',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  acting.value = true
  try {
    const res = await approveAuditApi(detail.value.sampleId, approveForm.opinion || null, approveForm.abnormalConfirmed)
    ElMessage.success(`审核通过：${res.statusLabel}`)
    drawerVisible.value = false
    await loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    acting.value = false
  }
}

async function handleReturn(): Promise<void> {
  if (!detail.value) return
  const reason = returnForm.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写退回原因（检验员需要知道要改什么）')
    return
  }
  try {
    await ElMessageBox.confirm(
      `退回后样品将回到「检验中」，重新出现在检验员的结果录入待办中。确认退回？`,
      '审核退回',
      { confirmButtonText: '确认退回', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const res = await returnAuditApi(detail.value.sampleId, reason)
    ElMessage.success(`已退回：${res.statusLabel}`)
    drawerVisible.value = false
    await loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    acting.value = false
  }
}

async function handleSign(): Promise<void> {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      '确认签发？签发后该样品将进入「已签发」，可生成检验报告。',
      '报告签发',
      { confirmButtonText: '确认签发', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const res = await signReportApi(detail.value.sampleId, signForm.opinion || null)
    ElMessage.success(`已签发：${res.statusLabel}`)
    drawerVisible.value = false
    await loadPending()
  } catch {
    // 请求层已统一提示
  } finally {
    acting.value = false
  }
}

onMounted(() => {
  void loadPending()
  // 深链支持：/report/audit?sampleId=1 直接展开该样品的审核抽屉
  // （便于从待办/通知跳转直达；仅 UI 层affordance，不涉及契约）
  const sid = Number(route.query.sampleId)
  if (Number.isFinite(sid) && sid > 0) {
    const target: AuditPendingRow = {
      id: sid,
      sampleNo: `#${sid}`,
      status: isAuditTab.value ? 60 : 70,
      itemTotal: 0,
      enteredCount: 0,
      abnormalCount: 0,
    }
    void openDrawer(target)
  }
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="报告审核签发"
      subtitle="检验数据全部录齐后转入审核；经审核无误由中心领导签发（签发后方可生成检验报告）"
      icon="Stamp"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>实验室业务</el-breadcrumb-item>
          <el-breadcrumb-item>报告审核签发</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="loadPending"
      >
        刷新
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-tabs
        v-model="activeTab"
        @tab-change="handleTabChange"
      >
        <el-tab-pane
          name="audit"
          label="待审核"
        />
        <el-tab-pane
          name="sign"
          label="待签发"
        />
      </el-tabs>

      <el-form
        inline
        class="filter-form"
      >
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
          min-width="130"
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
          prop="inspectType"
          label="检验类别"
          width="120"
          show-overflow-tooltip
        />
        <el-table-column
          label="检测单项"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ row.enteredCount }} / {{ row.itemTotal }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="异常项"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.abnormalCount > 0 ? 'danger' : 'success'"
              effect="plain"
            >
              {{ row.abnormalCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="整体结论"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="conclusionTagType(row.conclusion ?? null)"
              effect="plain"
            >
              {{ row.conclusionLabel ?? '—' }}
            </el-tag>
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
          v-if="!isAuditTab"
          label="审核人"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ row.auditBy ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="120"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              @click="openDrawer(rowItem(row))"
            >
              {{ isAuditTab ? '审核' : '签发' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty :description="isAuditTab ? '暂无待审核样品（需先在「结果录入」提交至检验完成）' : '暂无待签发样品'" />
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

    <!-- 审核/签发抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="88%"
      :destroy-on-close="true"
    >
      <div
        v-loading="drawerLoading"
        class="drawer-body"
      >
        <template v-if="detail">
          <!-- 头部摘要 -->
          <AppCard variant="glass">
            <div class="audit-head">
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
                <span class="cell-label">整体结论</span>
                <StatusBadge
                  :tone="conclusionTone(detail.conclusion)"
                  size="md"
                >
                  {{ detail.conclusionLabel ?? '—' }}
                </StatusBadge>
              </div>
              <div class="head-cell">
                <span class="cell-label">检测单项</span>
                <span class="cell-value">{{ detail.enteredCount }} / {{ detail.itemTotal }} 已录入</span>
              </div>
              <div class="head-cell">
                <span class="cell-label">异常项</span>
                <span
                  class="cell-value"
                  :class="{ 'text-danger': hasAbnormal }"
                >
                  未录入 {{ detail.blankCount }} · 待判定 {{ detail.pendingCount }}
                </span>
              </div>
            </div>
            <div
              v-if="detail.auditBy"
              class="head-audit"
            >
              <span>审核人：{{ detail.auditBy }}</span>
              <span v-if="detail.auditAt">审核时间：{{ detail.auditAt }}</span>
              <span v-if="detail.auditOpinion">审核意见：{{ detail.auditOpinion }}</span>
            </div>
            <div
              v-if="detail.signBy"
              class="head-audit"
            >
              <span>签发人：{{ detail.signBy }}</span>
              <span v-if="detail.signAt">签发时间：{{ detail.signAt }}</span>
            </div>
          </AppCard>

          <!-- 异常项清单（放行红线） -->
          <AppCard
            v-if="hasAbnormal"
            variant="panel"
            :padding="16"
          >
            <h3 class="section-title warning-title">
              <el-icon><Warning /></el-icon>
              异常项清单（{{ detail.abnormalCount }}）—— 放行前必须逐项确认
            </h3>
            <el-alert
              type="warning"
              :closable="false"
              show-icon
              title="未录入项属于操作缺漏（应打回补录）；待判定项属于数据缺口（缺检出限/标准文本），可人工裁决。"
            />
            <div
              v-if="blankItems.length > 0"
              class="abnormal-group"
            >
              <h4 class="group-title">
                未录入（{{ blankItems.length }}）
              </h4>
              <ul class="abnormal-list">
                <li
                  v-for="a in blankItems"
                  :key="a.itemId"
                >
                  <el-tag
                    type="info"
                    size="small"
                    effect="plain"
                  >
                    未录入
                  </el-tag>
                  <span class="ab-item">项次 {{ a.itemOrder }} · {{ a.itemName }}</span>
                  <span class="ab-reason">{{ a.reason }}</span>
                </li>
              </ul>
            </div>
            <div
              v-if="pendingItems.length > 0"
              class="abnormal-group"
            >
              <h4 class="group-title">
                待判定（{{ pendingItems.length }}）
              </h4>
              <ul class="abnormal-list">
                <li
                  v-for="a in pendingItems"
                  :key="a.itemId"
                >
                  <el-tag
                    type="warning"
                    size="small"
                    effect="plain"
                  >
                    待判定
                  </el-tag>
                  <span class="ab-item">项次 {{ a.itemOrder }} · {{ a.itemName }}</span>
                  <span class="ab-reason">{{ a.reason }}</span>
                </li>
              </ul>
            </div>

            <el-checkbox
              v-if="detail.allowAudit"
              v-model="approveForm.abnormalConfirmed"
              class="confirm-check"
            >
              我已逐项确认上述异常项，仍要审核通过（将留痕记录）
            </el-checkbox>
          </AppCard>

          <!-- 检测单项明细 -->
          <AppCard
            variant="panel"
            :padding="16"
          >
            <h3 class="section-title">
              检测单项（{{ detail.items.length }}）
              <span class="section-hint">单项结论由判定引擎生成，审核环节不改数据</span>
            </h3>
            <el-table
              :data="detail.items"
              stripe
              border
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
                min-width="160"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span>{{ row.itemName }}</span>
                  <el-tag
                    v-if="row.isReference === 1"
                    size="small"
                    type="info"
                    effect="plain"
                    class="ref-tag"
                  >
                    参考
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                label="判定依据"
                min-width="140"
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
                width="86"
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
                width="120"
                align="center"
              >
                <template #default="{ row }">
                  <span class="muted">{{ row.judgeTypeLabel }}</span>
                </template>
              </el-table-column>
              <el-table-column
                label="检验结果"
                width="120"
              >
                <template #default="{ row }">
                  <span v-if="row.entered">{{ row.testValue }}</span>
                  <span
                    v-else
                    class="text-danger"
                  >未录入</span>
                </template>
              </el-table-column>
              <el-table-column
                label="单项结论"
                width="200"
              >
                <template #default="{ row }">
                  <el-tag
                    :type="itemConclusionType(rowAuditItem(row))"
                    effect="plain"
                  >
                    {{ conclusionLabel(rowAuditItem(row)) }}
                  </el-tag>
                  <div
                    v-if="row.judgeBasis"
                    class="basis-text"
                    :title="row.judgeBasis"
                  >
                    {{ row.judgeBasis }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="检验员"
                width="110"
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

          <!-- 审核操作 -->
          <AppCard
            v-if="detail.allowAudit"
            variant="glass"
            :padding="20"
            accent
          >
            <h3 class="section-title">
              审核操作
            </h3>
            <div class="action-grid">
              <div class="action-left">
                <el-input
                  v-model="approveForm.opinion"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="审核意见（选填）"
                />
                <div class="action-row">
                  <el-tooltip
                    :disabled="approveEnabled"
                    content="存在异常项，请先勾选确认"
                    placement="top"
                  >
                    <span>
                      <el-button
                        type="success"
                        :icon="Check"
                        :disabled="!approveEnabled"
                        :loading="acting"
                        @click="handleApprove"
                      >
                        审核通过 → S70
                      </el-button>
                    </span>
                  </el-tooltip>
                </div>
              </div>
              <div class="action-right">
                <el-input
                  v-model="returnForm.reason"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="退回原因（必填）—— 检验员需要知道要改什么"
                />
                <div class="action-row">
                  <el-button
                    type="danger"
                    plain
                    :loading="acting"
                    @click="handleReturn"
                  >
                    审核退回 → S50
                  </el-button>
                </div>
              </div>
            </div>
          </AppCard>

          <!-- 签发操作 -->
          <AppCard
            v-if="detail.allowSign"
            variant="glass"
            :padding="20"
            accent
          >
            <h3 class="section-title">
              签发操作
            </h3>
            <el-input
              v-model="signForm.opinion"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="签发意见（选填）"
            />
            <div class="action-row">
              <el-button
                type="primary"
                :icon="Stamp"
                :loading="acting"
                @click="handleSign"
              >
                签发 → S80（可生成报告）
              </el-button>
            </div>
          </AppCard>

          <!-- 审核流水 -->
          <AppCard
            variant="panel"
            :padding="16"
          >
            <h3 class="section-title">
              审核/签发流水（{{ detail.logs.length }}）
            </h3>
            <el-table
              v-if="detail.logs.length > 0"
              :data="detail.logs"
              stripe
              border
            >
              <el-table-column
                prop="actionLabel"
                label="动作"
                width="100"
                align="center"
              />
              <el-table-column
                label="状态变化"
                width="200"
                align="center"
              >
                <template #default="{ row }">
                  <span>{{ row.fromStatusLabel }}</span>
                  <span class="muted"> → </span>
                  <span>{{ row.toStatusLabel }}</span>
                </template>
              </el-table-column>
              <el-table-column
                prop="opinion"
                label="意见/原因"
                min-width="220"
                show-overflow-tooltip
              />
              <el-table-column
                label="异常项确认"
                width="110"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag
                    :type="row.abnormalConfirmed === 1 ? 'warning' : 'success'"
                    size="small"
                    effect="plain"
                  >
                    {{ row.abnormalConfirmed === 1 ? '已确认' : '无需确认' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="operatedBy"
                label="操作人"
                width="110"
              />
              <el-table-column
                prop="operatedAt"
                label="操作时间"
                width="170"
              />
            </el-table>
            <AppEmpty
              v-else
              description="暂无审核/签发记录"
            />
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
.filter-form {
  margin-top: var(--lims-r-sm);
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
  padding: 0 var(--lims-r-xs);
}
.audit-head {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
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
.cell-value {
  font-size: 14px;
}
.text-danger {
  color: var(--lims-color-danger);
}
.head-audit {
  margin-top: var(--lims-r-xs);
  display: flex;
  gap: var(--lims-r-md);
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--lims-text-secondary);
}
.section-title {
  margin: 0 0 var(--lims-r-sm);
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: var(--lims-r-xs);
}
.warning-title {
  color: var(--lims-color-warning);
  align-items: center;
}
.section-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--lims-text-secondary);
}
.abnormal-card {
  border: 1px solid var(--lims-color-warning);
}
.abnormal-group {
  margin-top: var(--lims-r-sm);
}
.group-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}
.abnormal-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}
.abnormal-list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--lims-border-color);
  font-size: 13px;
}
.abnormal-list li:last-child {
  border-bottom: none;
}
.ab-item {
  min-width: 220px;
}
.ab-reason {
  color: var(--lims-text-secondary);
  font-size: 12px;
}
.confirm-check {
  margin-top: var(--lims-r-sm);
}
.ref-tag {
  margin-left: 6px;
}
.sub-text {
  font-size: 11px;
  color: var(--lims-text-secondary);
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
.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--lims-r-md);
}
.action-row {
  margin-top: var(--lims-r-xs);
  display: flex;
  gap: 8px;
  align-items: center;
}
.muted {
  color: var(--lims-text-secondary);
}
</style>
