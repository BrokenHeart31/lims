<script setup lang="ts">
/**
 * ReportCover — 检验报告封面（T-702）
 * ----------------------------------------------------------------------------
 * 逐格还原业务说明书封面：右上编号 → 资质行（CMA 1 行 / CMA-CATL 2 行）→
 * 大标题「检 验 报 告」→ 产品名称/受检单位/检验类别三行（值加下划线）→
 * 机构名 → 注意事项 7 条 → 联系方式块。
 * 版式样式集中在 styles/report-print.css（根 .report-print-root），本组件不带样式。
 */
import type { ReportVO } from '@/types/report'

defineProps<{
  report: ReportVO
}>()
</script>

<template>
  <div class="report-page cover-page">
    <div class="cover-no">
      编号：{{ report.reportNo }}
    </div>

    <div class="cover-qual">
      <div
        v-for="(line, i) in report.qualificationLines"
        :key="i"
        class="qual-line"
      >
        {{ line }}
      </div>
    </div>

    <div class="cover-title">
      检 验 报 告
    </div>

    <div class="cover-fields">
      <div class="cover-field">
        <span class="cover-field__label">产品名称：</span>
        <span class="cover-field__value">{{ report.productName }}</span>
      </div>
      <div class="cover-field">
        <span class="cover-field__label">受检单位：</span>
        <span class="cover-field__value">{{ report.clientName }}</span>
      </div>
      <div class="cover-field">
        <span class="cover-field__label">检验类别：</span>
        <span class="cover-field__value">{{ report.inspectType }}</span>
      </div>
    </div>

    <div class="cover-org">
      {{ report.orgName }}
    </div>

    <div class="cover-notes">
      <div class="notes-title">
        注  意  事  项
      </div>
      <ol class="notes-list">
        <li
          v-for="(note, i) in report.notes"
          :key="i"
        >
          {{ note }}
        </li>
      </ol>
    </div>

    <div class="cover-contact">
      <div><span class="contact-label">地址</span>：{{ report.address }}</div>
      <div><span class="contact-label">电话</span>：{{ report.phone }}</div>
      <div><span class="contact-label">邮政编码</span>：{{ report.postcode }}</div>
      <div><span class="contact-label">传真</span>：{{ report.fax }}</div>
    </div>
  </div>
</template>
