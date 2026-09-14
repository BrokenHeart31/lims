<script setup lang="ts">
/**
 * 省平台上报导出页（T-802，GET /api/export/province）。
 *
 * <p>把已完成检验（status>=80）的样品结果按省平台要求汇总为一个 Excel 文件下载。
 * 一行 = 一个「样品 × 检验单项」，10 列连续输出；可选用任务编号筛选。</p>
 */
import { reactive, ref } from 'vue'
import { Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { exportProvinceApi } from '@/api/exportApi'
import { downloadBlob } from '@/utils/download'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import DataFilter from '@/components/common/DataFilter.vue'

const query = reactive({
  taskNo: '',
})

const exporting = ref(false)

/** 导出列说明（与后端 ProvinceExportRow 严格一致，供用户核对） */
const COLUMNS = [
  '样品编号',
  '样品名称',
  '抽样日期',
  '检验依据',
  '检验项目',
  '单位',
  '技术要求',
  '检验结果',
  '单项评价',
  '任务编号',
]

async function handleExport(): Promise<void> {
  exporting.value = true
  try {
    const blob = await exportProvinceApi(query.taskNo ? { taskNo: query.taskNo } : undefined)
    downloadBlob(blob)
    ElMessage.success('导出已开始下载')
  } catch {
    // 请求层已统一提示
  } finally {
    exporting.value = false
  }
}

function handleReset(): void {
  query.taskNo = ''
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="导出数据"
      subtitle="汇总已完成检验的样品结果，生成农、畜、水省平台上报对接数据（Excel）"
      icon="Download"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>查询统计</el-breadcrumb-item>
          <el-breadcrumb-item>导出数据</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
    </PageHeader>

    <DataFilter>
      <el-form inline>
        <el-form-item label="任务编号">
          <el-input
            v-model="query.taskNo"
            placeholder="留空导出全部已完成样品"
            clearable
            style="width: 260px"
            @keyup.enter="handleExport"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Download"
            :loading="exporting"
            @click="handleExport"
          >
            导出数据
          </el-button>
          <el-button
            :icon="Refresh"
            @click="handleReset"
          >
            清除
          </el-button>
        </el-form-item>
      </el-form>
    </DataFilter>

    <AppCard
      variant="panel"
      :padding="20"
    >
      <h3 class="section-title">
        导出说明
      </h3>
      <ul class="notice-list">
        <li>数据范围：<b>已完成检验</b>的样品（状态为「已签发」或「已出报告」）。</li>
        <li>粒度：一行 = 一个<b>样品 × 检验单项</b>；同一样品的 N 个项目占 N 行，样品头信息在每行重复。</li>
        <li>文件标题栏显示为「系统导出数据&lt;导出的年月日时分秒&gt;.xlsx」。</li>
        <li>单项评价取自判定结论（合格 / 不合格 / 待判定），如实输出、不静默改判。</li>
        <li>可按任务编号筛选；留空则导出全部符合范围的样品。</li>
      </ul>
      <div class="columns">
        <span class="columns-label">输出列（共 {{ COLUMNS.length }} 列）：</span>
        <el-tag
          v-for="col in COLUMNS"
          :key="col"
          type="info"
          effect="plain"
          size="small"
        >
          {{ col }}
        </el-tag>
      </div>
    </AppCard>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
}
.section-title {
  margin: 0 0 var(--lims-r-sm);
  font-size: 15px;
  font-weight: 600;
}
.notice-list {
  margin: 0 0 var(--lims-r-md);
  padding-left: 20px;
  color: var(--lims-text-secondary);
  font-size: 13px;
  line-height: 1.9;
}
.columns {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.columns-label {
  color: var(--lims-text-secondary);
  font-size: 13px;
}
</style>
