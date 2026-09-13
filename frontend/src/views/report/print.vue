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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
  const sampleNo = queryString(route.query.sampleNo)
  if (!sampleNo) {
    ElMessage.error('缺少样品编号，无法加载报告')
    return
  }
  loading.value = true
  try {
    report.value = await getReportDetailApi(sampleNo, queryReportType(route.query.reportType))
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function handlePrint(): void {
  window.print()
}

function handleBack(): void {
  router.back()
}

onMounted(() => {
  void load()
})
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
          :disabled="!report"
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
        class="report-loading"
      >
        未获取到报告数据
      </div>
    </div>
  </Teleport>
</template>
