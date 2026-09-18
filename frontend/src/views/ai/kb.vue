<script setup lang="ts">
/**
 * AI 标准库（GB 知识库）导入管理页（feature A，设计 §5.5，api-spec A6~A13）。
 *
 * 能力：上传文本文件 / 扫描目录 → 异步建索引；导入任务真实进度轮询 + 失败重试；
 * 已入库标准列表（可删除重建）；直接检索调试（复用引用卡片渲染）。
 *
 * 原则：**禁假进度**——所有计数取自后端任务真实字段；PDF 会被后端以 4211 显式拒绝。
 * 权限：写操作 `ai:kb:import`、读操作 `ai:kb:query`，均用 `v-permission` 显隐。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { CopyDocument, Delete, Document, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  deleteKbDocumentApi,
  getKbJobApi,
  pageKbDocumentsApi,
  pageKbJobsApi,
  retryKbJobApi,
  retryScanOcrApi,
  scanKbDirApi,
  scanOcrJobsApi,
  searchKbApi,
  uploadKbFileApi,
} from '@/api/ai'
import type { GbDocumentVO, GbImportJobVO, GbSearchHitVO, ScanOcrRetry, ScanOcrJob } from '@/types/ai'
import { confirm } from '@/utils/confirm'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AiCitationCard from '@/components/ai/AiCitationCard.vue'

// ---------------- 导入任务 ----------------
const jobs = ref<GbImportJobVO[]>([])
const jobsLoading = ref(false)
const uploading = ref(false)
const scanning = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | null = null

const jobTone = (status: number): 'info' | 'warning' | 'success' | 'danger' => {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  if (status === 1) return 'warning'
  return 'info'
}

function jobPercent(job: GbImportJobVO): number {
  if (job.status === 2) return 100
  const total = job.totalClauses ?? 0
  if (total <= 0) return job.status === 3 ? 100 : 0
  return Math.min(100, Math.round(((job.doneClauses ?? 0) / total) * 100))
}

async function loadJobs(): Promise<void> {
  jobsLoading.value = true
  try {
    const page = await pageKbJobsApi(1, 20)
    jobs.value = page.records
    // 有未完成任务 → 轮询刷新真实进度
    if (page.records.some((j) => j.status === 0 || j.status === 1)) schedulePoll()
  } catch {
    jobs.value = []
  } finally {
    jobsLoading.value = false
  }
}

function schedulePoll(): void {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = setTimeout(() => {
    void loadJobs()
  }, 2500)
}

async function customUpload(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  try {
    await uploadKbFileApi(options.file)
    ElMessage.success('已提交导入任务，正在后台建索引')
    await loadJobs()
  } catch {
    // 请求层已统一提示（如 4211 PDF 需先转文本）
  } finally {
    uploading.value = false
  }
}

async function handleScan(): Promise<void> {
  scanning.value = true
  try {
    const jobId = await scanKbDirApi({ dir: 'ai/standards/parsed' })
    ElMessage.success(`已提交扫描任务（任务 #${jobId}）`)
    await loadJobs()
  } catch {
    // 请求层已统一提示
  } finally {
    scanning.value = false
  }
}

async function handleRetry(job: GbImportJobVO): Promise<void> {
  try {
    await retryKbJobApi(job.id)
    ElMessage.success('已提交重试')
    await loadJobs()
  } catch {
    // 请求层已统一提示
  }
}

/** 查看失败明细（A9） */
async function handleJobDetail(job: GbImportJobVO): Promise<void> {
  try {
    const detail = await getKbJobApi(job.id)
    if (!detail.errorMsg) {
      ElMessage.info('该任务无失败明细')
      return
    }
    ElMessage.warning(detail.errorMsg)
  } catch {
    // 请求层已统一提示
  }
}

// ---------------- 已入库标准 ----------------
const documents = ref<GbDocumentVO[]>([])
const documentsLoading = ref(false)
const documentsTotal = ref(0)
const docQuery = ref({ current: 1, size: 10, stdNo: '' })

async function loadDocuments(): Promise<void> {
  documentsLoading.value = true
  try {
    const page = await pageKbDocumentsApi(
      docQuery.value.current,
      docQuery.value.size,
      docQuery.value.stdNo || undefined,
    )
    documents.value = page.records
    documentsTotal.value = page.total
  } catch {
    documents.value = []
    documentsTotal.value = 0
  } finally {
    documentsLoading.value = false
  }
}

function handleDocSearch(): void {
  docQuery.value.current = 1
  void loadDocuments()
}

function handleDocPage(p: number): void {
  docQuery.value.current = p
  void loadDocuments()
}

async function handleDeleteDoc(doc: GbDocumentVO): Promise<void> {
  const ok = await confirm({
    title: '删除文档索引',
    message: `删除「${doc.stdNo}」的索引后该标准将不再被检索命中，但可重新导入重建。确认删除？`,
    tone: 'danger',
    confirmText: '删除',
  })
  if (ok === null) return
  try {
    await deleteKbDocumentApi(doc.id)
    ElMessage.success('已删除索引')
    await loadDocuments()
  } catch {
    // 请求层已统一提示
  }
}

// ---------------- 直接检索 ----------------
const searchQuery = ref('')
const searchStdNo = ref('')
const searching = ref(false)
const searchHits = ref<GbSearchHitVO[]>([])
const searched = ref(false)

async function handleSearch(): Promise<void> {
  const q = searchQuery.value.trim()
  if (!q) {
    ElMessage.warning('请输入检索词')
    return
  }
  searching.value = true
  try {
    searchHits.value = await searchKbApi({
      query: q,
      topN: 5,
      stdNo: searchStdNo.value.trim() || undefined,
    })
    searched.value = true
  } catch {
    searchHits.value = []
  } finally {
    searching.value = false
  }
}

// ---------------- 扫描件 OCR 进度（增量 A17/A18/A19） ----------------
const ocrJobs = ref<ScanOcrJob[]>([])
const ocrLoading = ref(false)
let ocrTimer: ReturnType<typeof setTimeout> | null = null

/** 真实进度（禁假进度）：取自后端读侧车位文件拼装 */
async function loadOcrJobs(): Promise<void> {
  ocrLoading.value = true
  try {
    const list = await scanOcrJobsApi()
    ocrJobs.value = list
    // 有进行中任务 → 轮询刷新真实页级进度
    if (list.some((j) => j.status === 'running' || j.status === 'pending')) scheduleOcrPoll()
  } catch {
    ocrJobs.value = []
  } finally {
    ocrLoading.value = false
  }
}

function scheduleOcrPoll(): void {
  if (ocrTimer) clearTimeout(ocrTimer)
  ocrTimer = setTimeout(() => {
    void loadOcrJobs()
  }, 5000)
}

function ocrPercent(job: ScanOcrJob): number {
  if (job.percent != null) return Math.max(0, Math.min(100, Math.round(job.percent * 10) / 10))
  const total = job.totalPages ?? 0
  if (total <= 0) return 0
  return Math.round(((job.donePages ?? 0) / total) * 1000) / 10
}

function ocrTone(job: ScanOcrJob): 'info' | 'warning' | 'success' | 'danger' {
  if (job.status === 'done') return 'success'
  if (job.status === 'partial_failed') return 'danger'
  if (job.status === 'running') return 'warning'
  return 'info'
}

function ocrStatusLabel(job: ScanOcrJob): string {
  switch (job.status) {
    case 'done':
      return '已完成'
    case 'partial_failed':
      return '部分失败'
    case 'running':
      return '进行中'
    case 'pending':
      return '待开始'
    default:
      return job.status ?? '未知'
  }
}

/** 重试：复位失败页为待跑，并把**待执行命令**展示给用户复制（系统不代跑） */
async function handleOcrRetry(job: ScanOcrJob): Promise<void> {
  try {
    const retry: ScanOcrRetry = await retryScanOcrApi(job.stdKey)
    pendingRetry.value = retry
    if (retry.pendingPages.length === 0) {
      ElMessage.info('该任务没有可复位的失败页')
    } else {
      ElMessage.warning('已复位失败页为待跑，请在下方复制命令到终端执行（本系统不代替运行 OCR）')
    }
    await loadOcrJobs()
  } catch {
    // 请求层已统一提示（如 4204 任务不存在 / 4205 进行中）
  }
}

const pendingRetry = ref<ScanOcrRetry | null>(null)

async function copyCommand(): Promise<void> {
  const cmd = pendingRetry.value?.commandToRun
  if (!cmd) return
  try {
    await navigator.clipboard.writeText(cmd)
    ElMessage.success('命令已复制')
  } catch {
    ElMessage.info('复制失败，请手动选择命令文本')
  }
}

const activeJobs = computed(() => jobs.value.filter((j) => j.status === 0 || j.status === 1).length)

const router = useRouter()

/** 进入会话审计页（无侧栏菜单，由本页入口进入） */
function goConversations(): void {
  void router.push('/ai/conversations')
}

/** el-table 作用域插槽 row 为宽松类型（Element Plus DefaultRow）→ 统一收窄 */
function rowJob(row: unknown): GbImportJobVO {
  return row as GbImportJobVO
}

function rowDoc(row: unknown): GbDocumentVO {
  return row as GbDocumentVO
}

function rowOcr(row: unknown): ScanOcrJob {
  return row as ScanOcrJob
}

onMounted(() => {
  void loadJobs()
  void loadDocuments()
  void loadOcrJobs()
})

onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
  if (ocrTimer) clearTimeout(ocrTimer)
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="AI 标准库"
      subtitle="导入 GB 标准文本并建立 ngram 索引，供 AI 助手按标准号检索限量条款"
      icon="Monitor"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>AI 助手</el-breadcrumb-item>
          <el-breadcrumb-item>标准库</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        v-permission="'ai:log:view'"
        :icon="Document"
        @click="goConversations"
      >
        会话审计
      </el-button>
      <el-upload
        v-permission="'ai:kb:import'"
        action="#"
        :show-file-list="false"
        :http-request="customUpload"
        :disabled="uploading"
      >
        <el-button
          type="primary"
          :icon="Upload"
          :loading="uploading"
        >
          上传标准文件
        </el-button>
      </el-upload>
      <el-button
        v-permission="'ai:kb:import'"
        :icon="Refresh"
        :loading="scanning"
        @click="handleScan"
      >
        扫描 parsed/ 目录
      </el-button>
    </PageHeader>

    <!-- 导入任务进度 -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <div class="page__card-head">
        <h3 class="page__section-title">
          导入任务
          <span
            v-if="activeJobs > 0"
            class="page__badge"
          >{{ activeJobs }} 个进行中</span>
        </h3>
        <el-button
          size="small"
          :icon="Refresh"
          @click="loadJobs"
        >
          刷新
        </el-button>
      </div>
      <el-table
        v-loading="jobsLoading"
        :data="jobs"
        stripe
        border
      >
        <el-table-column
          prop="fileName"
          label="文件 / 目录"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          label="状态"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="jobTone(rowJob(row).status)"
              size="sm"
            >
              {{ row.statusLabel ?? '未知' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="进度"
          min-width="200"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="jobPercent(rowJob(row))"
              :status="rowJob(row).status === 3 ? 'exception' : rowJob(row).status === 2 ? 'success' : ''"
              :stroke-width="14"
              :text-inside="true"
            />
            <span class="page__sub">
              {{ row.doneClauses ?? 0 }} / {{ row.totalClauses ?? 0 }} 条款
              <template v-if="(row.failCount ?? 0) > 0">· 失败 {{ row.failCount }}</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="时间"
          width="190"
        >
          <template #default="{ row }">
            <div class="page__op">
              <span>{{ row.startedAt ?? '' }}</span>
              <span class="page__time">{{ row.finishedAt ?? '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="150"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-if="(rowJob(row).failCount ?? 0) > 0 || rowJob(row).status === 3"
              link
              type="warning"
              size="small"
              @click="handleJobDetail(rowJob(row))"
            >
              失败明细
            </el-button>
            <el-button
              v-if="rowJob(row).status === 3"
              link
              type="primary"
              size="small"
              @click="handleRetry(rowJob(row))"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无导入任务"
            hint="上传标准文本或扫描 parsed/ 目录以建立索引"
          />
        </template>
      </el-table>
    </AppCard>

    <!-- 扫描件 OCR 进度（增量 A17/A18/A19：读侧车位，页级进度，禁假进度） -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <div class="page__card-head">
        <h3 class="page__section-title">
          扫描件 OCR 进度
          <span class="page__sub">离线预处理（ai/scripts/prepare-standards.py --ocr）的页级进度；本系统只读展示、不代跑</span>
        </h3>
        <el-button
          size="small"
          :icon="Refresh"
          @click="loadOcrJobs"
        >
          刷新
        </el-button>
      </div>
      <el-table
        v-loading="ocrLoading"
        :data="ocrJobs"
        stripe
        border
      >
        <el-table-column
          label="标准"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="page__op">
              <span>{{ row.stdNo ?? row.stdKey }}</span>
              <span class="page__time">{{ row.sourceFile ?? '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="ocrTone(rowOcr(row))"
              size="sm"
            >
              {{ ocrStatusLabel(rowOcr(row)) }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="页级进度"
          min-width="240"
        >
          <template #default="{ row }">
            <el-progress
              :percentage="ocrPercent(rowOcr(row))"
              :status="rowOcr(row).status === 'partial_failed' ? 'exception' : rowOcr(row).status === 'done' ? 'success' : ''"
              :stroke-width="14"
              :text-inside="true"
            />
            <span class="page__sub">
              第 {{ rowOcr(row).donePages ?? 0 }} / {{ rowOcr(row).totalPages ?? 0 }} 页（{{ ocrPercent(rowOcr(row)) }}%）
              <template v-if="(rowOcr(row).failedPages?.length ?? 0) > 0">
                · 失败 {{ rowOcr(row).failedPages.length }} 页
              </template>
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="失败页"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span
              v-if="(rowOcr(row).failedPages?.length ?? 0) === 0"
              class="page__time"
            >—</span>
            <span
              v-else
              class="page__time"
            >
              {{ rowOcr(row).failedPages.map((f) => `${f.page}(${f.reason ?? '?'})`).join('；') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="更新时间"
          width="160"
        >
          <template #default="{ row }">
            <span class="page__time">{{ rowOcr(row).updatedAt ?? '' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'ai:kb:import'"
              link
              type="warning"
              size="small"
              :disabled="(rowOcr(row).failedPages?.length ?? 0) === 0"
              @click="handleOcrRetry(rowOcr(row))"
            >
              复位失败页
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无扫描件 OCR 任务"
            hint="把扫描版 PDF 投放 ai/standards/inbox 后运行：python ai/scripts/prepare-standards.py --ocr"
          />
        </template>
      </el-table>

      <!-- 待执行命令（系统不代跑，用户复制到终端执行） -->
      <div
        v-if="pendingRetry && pendingRetry.pendingPages.length > 0"
        class="page__cmd"
      >
        <p class="page__cmd-note">
          {{ pendingRetry.note }}
        </p>
        <div class="page__cmd-row">
          <code class="page__cmd-text">{{ pendingRetry.commandToRun }}</code>
          <el-button
            size="small"
            :icon="CopyDocument"
            @click="copyCommand"
          >
            复制命令
          </el-button>
        </div>
      </div>
    </AppCard>

    <!-- 已入库标准 -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <div class="page__card-head">
        <h3 class="page__section-title">
          已入库标准
        </h3>
        <div class="page__filter">
          <el-input
            v-model="docQuery.stdNo"
            placeholder="按标准号筛选"
            clearable
            size="small"
            style="width: 200px"
            @keyup.enter="handleDocSearch"
          />
          <el-button
            size="small"
            type="primary"
            :icon="Search"
            @click="handleDocSearch"
          >
            查询
          </el-button>
        </div>
      </div>
      <el-table
        v-loading="documentsLoading"
        :data="documents"
        stripe
        border
      >
        <el-table-column
          prop="stdNo"
          label="标准号"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="stdTitle"
          label="标准名称"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="sourceFile"
          label="来源文件"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          label="格式"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.sourceTypeLabel ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="条款数"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.clauseCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <StatusBadge
              :tone="row.status === 2 ? 'neutral' : 'success'"
              size="sm"
            >
              {{ row.statusLabel ?? '—' }}
            </StatusBadge>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'ai:kb:import'"
              link
              type="danger"
              size="small"
              :icon="Delete"
              @click="handleDeleteDoc(rowDoc(row))"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="尚未导入任何标准"
            hint="导入后可被 AI 助手检索命中"
          />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="docQuery.current"
        v-model:page-size="docQuery.size"
        :total="documentsTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="page__pager"
        @current-change="handleDocPage"
        @size-change="handleDocSearch"
      />
    </AppCard>

    <!-- 直接检索（调试 / 页面联动） -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <h3 class="page__section-title">
        标准条款检索
        <span class="page__sub">ngram 分词检索，命中片段按相关度排序</span>
      </h3>
      <div class="page__filter">
        <el-input
          v-model="searchQuery"
          placeholder="检索词（如：铅 限量）"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="searchStdNo"
          placeholder="限定标准号（选填）"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
        <el-button
          type="primary"
          :icon="Search"
          :loading="searching"
          @click="handleSearch"
        >
          检索
        </el-button>
      </div>
      <div
        v-if="searchHits.length > 0"
        class="page__hits"
      >
        <AiCitationCard
          v-for="(hit, idx) in searchHits"
          :key="idx"
          :citation="hit"
        />
      </div>
      <AppEmpty
        v-else-if="searched && !searching"
        title="未命中条款"
        hint="单字词会被 ngram 忽略，请使用词组检索"
      />
      <div
        v-else-if="!searching"
        class="page__tip"
      >
        <el-icon><Document /></el-icon>
        <span>输入检索词以验证标准库索引是否生效。</span>
      </div>
    </AppCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
}

.page__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-3);
  margin-bottom: var(--lims-sp-3);
}

.page__section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 var(--lims-sp-3);
  color: var(--lims-ink);
  font-size: var(--lims-fs-base);
  font-weight: 600;
}

.page__card-head .page__section-title {
  margin-bottom: 0;
}

.page__badge {
  padding: 1px 8px;
  border-radius: var(--lims-r-pill);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
  font-size: 11px;
  font-weight: 500;
}

.page__sub {
  display: block;
  margin-top: 4px;
  color: var(--lims-faint);
  font-size: 11px;
  font-weight: 400;
}

.page__filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.page__op {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.page__time {
  color: var(--lims-faint);
  font-size: 11px;
}

.page__hits {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.page__tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lims-muted);
  font-size: var(--lims-fs-sm);
}

.page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-sp-3);
}

.page__cmd {
  margin-top: var(--lims-sp-3);
  padding: 10px 12px;
  border: 1px solid var(--lims-warning-line);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-warning-soft);
}

.page__cmd-note {
  margin: 0 0 8px;
  color: var(--lims-warning);
  font-size: 12px;
  line-height: 1.5;
}

.page__cmd-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.page__cmd-text {
  flex: 1;
  min-width: 240px;
  padding: 6px 8px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card);
  color: var(--lims-ink-2);
  font-family: var(--lims-font-mono);
  font-size: 12px;
  word-break: break-all;
}
</style>
