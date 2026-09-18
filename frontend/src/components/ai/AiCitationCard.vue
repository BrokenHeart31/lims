<script setup lang="ts">
/**
 * AiCitationCard — 引用卡片（标准号 / 条款号 / 标题 / 片段 / 出处）。
 *
 * 结构化引用的意义：让「必带标准号 + 出处」成为结构性必然——无 citations 即无卡片。
 */
import type { AiCitationVO } from '@/types/ai'
import AiSourceTag from '@/components/ai/AiSourceTag.vue'

defineProps<{
  citation: AiCitationVO
}>()

function scoreText(score?: number | null): string {
  return typeof score === 'number' ? score.toFixed(2) : '—'
}
</script>

<template>
  <div class="cite">
    <div class="cite__head">
      <span class="cite__std">{{ citation.stdNo }}</span>
      <span
        v-if="citation.clauseNo"
        class="cite__clause"
      >{{ citation.clauseNo }}</span>
      <span
        v-if="citation.clauseTitle"
        class="cite__title"
      >{{ citation.clauseTitle }}</span>
      <AiSourceTag
        class="cite__tag"
        :ocr-derived="citation.ocrDerived"
        :source-type-label="citation.sourceTypeLabel"
      />
      <span
        class="cite__score"
        :title="`ngram 相关度得分 ${scoreText(citation.score)}`"
      >{{ scoreText(citation.score) }}</span>
    </div>
    <p class="cite__snippet">
      {{ citation.snippet }}
    </p>
    <div
      v-if="citation.sourceFile"
      class="cite__source"
    >
      出处：{{ citation.sourceFile }}
    </div>
    <div
      v-if="citation.ocrDerived"
      class="cite__ocr-note"
    >
      本条来自扫描件识别，可能有错字；数值请以系统标准库为准。
    </div>
  </div>
</template>

<style scoped>
.cite {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card);
}

.cite__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.cite__std {
  color: var(--lims-accent);
  font-family: var(--lims-font-mono);
  font-size: 12px;
  font-weight: 600;
}

.cite__clause {
  padding: 0 6px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-xs, 4px);
  color: var(--lims-ink-2);
  font-size: 11px;
}

.cite__title {
  color: var(--lims-ink);
  font-size: 12px;
  font-weight: 500;
}

.cite__score {
  margin-left: auto;
  color: var(--lims-faint);
  font-family: var(--lims-font-mono);
  font-size: 11px;
}

.cite__snippet {
  margin: 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.55;
}

.cite__source {
  color: var(--lims-faint);
  font-size: 11px;
  word-break: break-all;
}

.cite__tag {
  flex: none;
}

.cite__ocr-note {
  padding: 5px 8px;
  border: 1px dashed var(--lims-warning-line);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
  font-size: 11px;
  line-height: 1.5;
}
</style>
