<script setup lang="ts">
/**
 * AiSourceTag — 引用来源标签（增量 T05，设计 §2.3 第 3 条）。
 *
 * 由后端结构化下发 `ocrDerived` 决定：
 *   · `ocrDerived=false` → 「文本版」（中性色）；
 *   · `ocrDerived=true`  → 「扫描件 OCR · 可能有识别误差」（警示色）+ tooltip。
 *
 * 为什么必须由后端下发而非仅 UI 提示：前端一旦漏渲染就失去警示，
 * 故来源类型是**结构性必然**（与「引用卡片必带出处」同源）。
 */
defineProps<{
  /** 是否来自扫描件 OCR */
  ocrDerived?: boolean | null
  /** 可选：来源类型中文名（后端 sourceTypeLabel，优先展示） */
  sourceTypeLabel?: string | null
}>()
</script>

<template>
  <span
    class="src-tag"
    :class="ocrDerived ? 'is-ocr' : 'is-text'"
    :title="
      ocrDerived
        ? '本条来自扫描件识别，可能有错字；数值请以系统标准库为准'
        : '本条来自文本版标准文件'
    "
  >
    <span
      class="src-tag__dot"
      aria-hidden="true"
    />
    {{
      ocrDerived
        ? '扫描件 OCR · 可能有识别误差'
        : (sourceTypeLabel && sourceTypeLabel !== '扫描件OCR' ? sourceTypeLabel : '文本版')
    }}
  </span>
</template>

<style scoped>
.src-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border-radius: var(--lims-r-pill);
  font-size: 11px;
  line-height: 18px;
  white-space: nowrap;
}

.src-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.src-tag.is-text {
  border: 1px solid var(--lims-hair-2);
  background: rgba(255, 255, 255, 0.04);
  color: var(--lims-muted);
}

.src-tag.is-ocr {
  border: 1px solid var(--lims-warning-line);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
}
</style>
