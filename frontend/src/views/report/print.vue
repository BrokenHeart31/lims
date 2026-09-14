<script setup lang="ts">
/**
 * 检验报告打印页（T-702）
 * ----------------------------------------------------------------------------
 * 路由参数：sampleNo（必填）+ reportType（可选，'CMA' | 'CMA_CATL'）。
 * 调 GET /api/report/detail 取数 → 渲染封面 / 第 1 页 / 第 2 页 → 浏览器打印。
 *
 * 设计要点：
 *   - 通过 <Teleport to="body"> 把白底文档挂到 body 直下：既保证 position:fixed
 *     全屏盖住暗色外壳，又让打印时可用 `body > *:not(.report-print-root)` 精确隐藏外壳，
 *     与路由挂在 MainLayout 内还是顶层无关。
 *   - 组件卸载即移除，切回其他页面时暗色主题完全不受影响。
 *   - 版式全部在 report-print.css（作用域限于 .report-print-root）。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getReportDetailApi, type ReportTypeCode } from '@/api/report'
import type { ReportVO } from '@/types/report'
import ReportCover from '@/components/report/ReportCover.vue'
import ReportPage1 from '@/components/report/ReportPage1.vue'
import ReportPage2 from '@/components/report/ReportPage2.vue'
import '@/styles/report-print.css'

const route = useRoute()
const router = useRouter()

const report = ref<ReportVO | null>(null)
const loading = ref(false)
const loadError = ref('')
let loadRequest = 0

/** 从 query 取单值字符串（query 值可能是 string | string[] | null） */
function queryString(raw: unknown): string {
  if (typeof raw === 'string') return raw
  if (Array.isArray(raw) && typeof raw[0] === 'string') return raw[0]
  return ''
}

/** 报告类型：仅接受 1/2（数字 code），其余交给后端按样品已存类型回退 */
function queryReportType(raw: unknown): ReportTypeCode | undefined {
  const value = Number(queryString(raw))
  return value === 1 || value === 2 ? value : undefined
}

async function load(): Promise<void> {
  const request = ++loadRequest
  report.value = null
  loadError.value = ''
  const sampleNo = queryString(route.query.sampleNo)
  if (!sampleNo) {
    loadError.value = '缺少样品编号，请返回报告列表选择样品。'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await getReportDetailApi(sampleNo, queryReportType(route.query.reportType))
    if (request === loadRequest) report.value = result
  } catch {
    if (request === loadRequest) loadError.value = '报告加载失败，请检查网络或权限后重试。'
  } finally {
    if (request === loadRequest) loading.value = false
  }
}

function handlePrint(): void {
  if (!report.value || loading.value || loadError.value) return
  window.print()
}

function handleBack(): void {
  router.back()
}

onMounted(() => document.body.classList.add('lims-report-printing'))
onBeforeUnmount(() => {
  ++loadRequest
  document.body.classList.remove('lims-report-printing')
})
watch(() => [route.query.sampleNo, route.query.reportType], load, { immediate: true })
</script>

<template>
  <Teleport to="body">
    <div class="report-print-root">
      <div class="report-toolbar no-print">
        <el-button @click="handleBack">
          返回
        </el-button>
        <el-button
          type="primary"
          :disabled="!report || loading || !!loadError"
          @click="handlePrint"
        >
          打印
        </el-button>
      </div>

      <div
        v-if="loading"
        class="report-loading no-print"
      >
        报告加载中…
      </div>

      <template v-else-if="report">
        <ReportCover :report="report" />
        <ReportPage1 :report="report" />
        <ReportPage2 :report="report" />
      </template>

      <div
        v-else
        class="report-loading no-print"
        role="alert"
      >
        <p>{{ loadError || '未获取到报告数据' }}</p>
        <el-button
          :loading="loading"
          @click="load"
        >
          重新加载
        </el-button>
      </div>
    </div>
  </Teleport>
</template>
