/**
 * 样品状态机 code → 状态标签/色调（前端唯一权威，与后端 SampleStatus.code 对齐）
 * ----------------------------------------------------------------------------
 * 用法：
 *   import { sampleStatusInfo } from '@/utils/sampleStatus'
 *   sampleStatusInfo(row.status).label
 *   sampleStatusInfo(row.status).tone  // 传给 StatusBadge
 */
import { SAMPLE_STATUS_OPTIONS } from '@/api/sample'

export type SampleTone =
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'purple'
  | 'neutral'

export interface SampleStatusInfo {
  code: number
  label: string
  tone: SampleTone
}

const TONE_MAP: Record<number, SampleTone> = {
  10: 'info', // 已登记
  20: 'purple', // 登记确认
  30: 'info', // 已分解
  40: 'warning', // 已安排
  50: 'warning', // 检验中
  60: 'success', // 检验完成
  70: 'success', // 已审核
  80: 'success', // 已签发
  90: 'success', // 已出报告
}

export function sampleStatusInfo(code: number): SampleStatusInfo {
  const opt = SAMPLE_STATUS_OPTIONS.find((o) => o.code === code)
  return {
    code,
    label: opt?.label ?? `S${code}`,
    tone: TONE_MAP[code] ?? 'neutral',
  }
}