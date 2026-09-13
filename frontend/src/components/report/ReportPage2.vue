<script setup lang="ts">
/**
 * ReportPage2 — 检验报告第 2 页「检 验 结 果」（T-702）
 * ----------------------------------------------------------------------------
 * 页眉右侧「共2页 第2页」+ 标题；表头两行「样品编号」「样品名称」；
 * 7 列明细表（检验项目 / 检验数据 / 检测依据 / 标准值 / 单位 / 最低检出限 / 单项结论），
 * 数据行取 items；单项结论「不合格」浅红底提示，其余黑白。
 * 本页为最后一页，根容器带 report-page-last（打印时不再分页）。
 */
import type { ReportVO } from '@/types/report'

defineProps<{
  report: ReportVO
}>()
</script>

<template>
  <div class="report-page report-page-last">
    <div class="page-head">
      <div class="page-title">
        检 验 结 果
      </div>
      <div class="page-count">
        共2页 第2页
      </div>
    </div>

    <div class="result-meta">
      <div class="meta-line">
        样品编号：{{ report.reportNo }}
      </div>
      <div class="meta-line">
        样品名称：{{ report.productName }}
      </div>
    </div>

    <table class="result-table">
      <thead>
        <tr>
          <th>检验项目</th>
          <th>检验数据</th>
          <th>检测依据</th>
          <th>标准值</th>
          <th>单位</th>
          <th>最低检出限</th>
          <th>单项结论</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(item, idx) in report.items"
          :key="idx"
        >
          <td>{{ item.itemName }}</td>
          <td>{{ item.testValue }}</td>
          <td>{{ item.basisCode }}</td>
          <td>{{ item.stdValue }}</td>
          <td>{{ item.unit }}</td>
          <td>{{ item.lowerLimit }}</td>
          <td :class="{ 'is-unqualified': item.conclusionCode === 2 }">
            {{ item.conclusionText }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
