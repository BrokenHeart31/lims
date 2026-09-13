/**
 * 检验报告渲染模型类型 —— T-702
 *
 * 与后端 com.lims.vo.ReportVO / ReportItemVO 字段严格对齐（camelCase）。
 * 打印页（views/report/print.vue）与报告组件（components/report/*）共用。
 */

/** 报告明细行（第 2 页 7 列表） */
export interface ReportItemVO {
  itemOrder: number
  /** 检验项目名称（参考项前缀 "*"） */
  itemName: string
  /** 检验数据（无结果行时 null） */
  testValue?: string | null
  basisCode?: string | null
  stdValue?: string | null
  unit?: string | null
  lowerLimit?: string | null
  /** 单项结论中文（无结论时 null） */
  conclusionText?: string | null
  /** 单项结论码：1=合格 2=不合格 3=待判定 */
  conclusionCode?: number | null
  /** 是否参考性限量项：0=否 1=是 */
  isReference?: number | null
}

/** 完整报告渲染模型 */
export interface ReportVO {
  /** 报告类型 code：1=CMA 2=CMA-CATL */
  reportType: number
  reportTypeLabel?: string
  /** 报告编号（= 样品编号） */
  reportNo: string
  /** 封面资质行（CMA 1 行、CMA-CATL 2 行） */
  qualificationLines: string[]

  // 机构信息
  orgName?: string | null
  address?: string | null
  phone?: string | null
  postcode?: string | null
  fax?: string | null
  /** 封面注意事项（7 条） */
  notes: string[]

  // 封面三要素
  productName?: string | null
  clientName?: string | null
  inspectType?: string | null

  // 第 1 页信息表
  spec?: string | null
  brand?: string | null
  manufacturer?: string | null
  grade?: string | null
  samplingAddress?: string | null
  /** 采样日期 yyyy.MM.dd */
  samplingDate?: string | null
  sampleQuantity?: string | null
  sampler?: string | null
  samplingBase?: string | null
  originalNo?: string | null
  sampleState?: string | null
  itemSummary?: string | null
  basisText?: string | null
  /** 检验日期 yyyy.MM.dd */
  inspectDate?: string | null
  conclusionText?: string | null
  instrument?: string | null
  environment?: string | null
  remark?: string | null

  // 签署
  /** 签发日期 yyyy.MM.dd */
  signAt?: string | null
  approveName?: string | null
  auditName?: string | null
  editName?: string | null
  approveSignatureUrl?: string | null
  auditSignatureUrl?: string | null
  editSignatureUrl?: string | null

  // 第 2 页明细
  items: ReportItemVO[]
}
