<script setup lang="ts">
/**
 * ReportPage1 — 检验报告第 1 页「检 验 报 告」信息表（T-702）
 * ----------------------------------------------------------------------------
 * 一张 12 行 × 4 列信息表，行序严格对照业务说明书：
 *   ① 产品名称/规格型号 ② 商标 ③ 受检单位/检验类别 ④ 生产单位/样品等级
 *   ⑤ 抽样地点/采样日期 ⑥ 样品数量/采样者 ⑦ 抽样基数/原编号或生产日期
 *   ⑧ 样品状态/检测项目 ⑨ 检测依据/检验日期 ⑩ 检验结论（右列合并跨 2 列）
 *   ⑪ 主要仪器/实验环境条件 ⑫ 备注
 * 表格下方为批准 / 审核 / 编制签署行，三个签名位：有签名图片则 <img>，
 * 否则渲染虚线占位框（框内写姓名）——绝不伪造签名图片。
 */
import type { ReportVO } from '@/types/report'

defineProps<{
  report: ReportVO
}>()
</script>

<template>
  <div class="report-page">
    <div class="page-head">
      <div class="page-title">
        检 验 报 告
      </div>
      <div class="page-count">
        共2页 第1页
      </div>
    </div>

    <table class="info-table">
      <tbody>
        <tr>
          <td class="cell-label">
            产品名称
          </td>
          <td class="cell-value">
            {{ report.productName }}
          </td>
          <td class="cell-label">
            规格型号
          </td>
          <td class="cell-value">
            {{ report.spec }}
          </td>
        </tr>
        <tr>
          <td class="cell-label" />
          <td class="cell-value" />
          <td class="cell-label">
            商标
          </td>
          <td class="cell-value">
            {{ report.brand }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            受检单位
          </td>
          <td class="cell-value">
            {{ report.clientName }}
          </td>
          <td class="cell-label">
            检验类别
          </td>
          <td class="cell-value">
            {{ report.inspectType }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            生产单位
          </td>
          <td class="cell-value">
            {{ report.manufacturer }}
          </td>
          <td class="cell-label">
            样品等级
          </td>
          <td class="cell-value">
            {{ report.grade }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            抽样地点
          </td>
          <td class="cell-value">
            {{ report.samplingAddress }}
          </td>
          <td class="cell-label">
            采样日期
          </td>
          <td class="cell-value">
            {{ report.samplingDate }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            样品数量
          </td>
          <td class="cell-value">
            {{ report.sampleQuantity }}
          </td>
          <td class="cell-label">
            采样者
          </td>
          <td class="cell-value">
            {{ report.sampler }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            抽样基数
          </td>
          <td class="cell-value">
            {{ report.samplingBase }}
          </td>
          <td class="cell-label">
            原编号或生产日期
          </td>
          <td class="cell-value">
            {{ report.originalNo }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            样品状态
          </td>
          <td class="cell-value">
            {{ report.sampleState }}
          </td>
          <td class="cell-label">
            检测项目
          </td>
          <td class="cell-value">
            {{ report.itemSummary }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            检测依据
          </td>
          <td class="cell-value">
            {{ report.basisText }}
          </td>
          <td class="cell-label">
            检验日期
          </td>
          <td class="cell-value">
            {{ report.inspectDate }}
          </td>
        </tr>
        <tr>
          <td class="cell-label cell-conclusion">
            检验结论
          </td>
          <td class="cell-value">
            {{ report.conclusionText }}
          </td>
          <td
            class="cell-value"
            colspan="2"
          >
            <div class="conclusion-seal">
              （检验报告专用章）
            </div>
            <div class="conclusion-sign">
              签发日期：{{ report.signAt }}
            </div>
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            主要仪器
          </td>
          <td class="cell-value">
            {{ report.instrument }}
          </td>
          <td class="cell-label">
            实验环境条件
          </td>
          <td class="cell-value">
            {{ report.environment }}
          </td>
        </tr>
        <tr>
          <td class="cell-label">
            备注
          </td>
          <td class="cell-value">
            {{ report.remark }}
          </td>
          <td class="cell-value" />
          <td class="cell-value" />
        </tr>
      </tbody>
    </table>

    <div class="sign-row">
      <div class="sign-cell">
        <span>批准：{{ report.approveName }}</span>
        <span class="sign-slot">
          <img
            v-if="report.approveSignatureUrl"
            class="sign-img"
            :src="report.approveSignatureUrl"
            alt="批准签名"
          >
          <span
            v-else
            class="sign-placeholder"
          >{{ report.approveName }}</span>
        </span>
      </div>
      <div class="sign-cell">
        <span>审核：{{ report.auditName }}</span>
        <span class="sign-slot">
          <img
            v-if="report.auditSignatureUrl"
            class="sign-img"
            :src="report.auditSignatureUrl"
            alt="审核签名"
          >
          <span
            v-else
            class="sign-placeholder"
          >{{ report.auditName }}</span>
        </span>
      </div>
      <div class="sign-cell">
        <span>编制：{{ report.editName }}</span>
        <span class="sign-slot">
          <img
            v-if="report.editSignatureUrl"
            class="sign-img"
            :src="report.editSignatureUrl"
            alt="编制签名"
          >
          <span
            v-else
            class="sign-placeholder"
          >{{ report.editName }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
