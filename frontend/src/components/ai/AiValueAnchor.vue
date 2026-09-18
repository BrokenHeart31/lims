<script setup lang="ts">
/**
 * AiValueAnchor — 数值对齐卡片（增量 T05，设计 §2.3 / §4.2 / §5.3）。
 *
 * **红线**：数值只能来自系统标准库（后端 `ValueAnchorAssembler` 只读 `product_lib_item` /
 * `sample_item` 快照），**绝不由模型断言**。本组件只做「展示 + 只读跳转」：
 *   · 有 `jumpPath`（用户对目标对象有权限）→ 给一个「查看系统标准库」只读跳转按钮；
 *   · 无 → 只展示值 + 一行权限提示。
 * **无任何「填入 / 提交」按钮**（助手不代操作）。
 */
import { Right } from '@element-plus/icons-vue'
import type { AiValueAnchor } from '@/types/ai'

defineProps<{
  anchor: AiValueAnchor
}>()

const emit = defineEmits<{
  (e: 'navigate', path: string): void
}>()
</script>

<template>
  <div class="anchor">
    <div class="anchor__head">
      <span class="anchor__name">{{ anchor.itemName }}</span>
      <span class="anchor__value">
        {{ anchor.stdValue }}
        <em v-if="anchor.unit">{{ anchor.unit }}</em>
      </span>
    </div>
    <div class="anchor__meta">
      <span
        v-if="anchor.judgeTypeLabel"
        class="anchor__chip"
      >{{ anchor.judgeTypeLabel }}</span>
      <span
        v-if="anchor.isReference"
        class="anchor__chip is-ref"
      >参考限量</span>
      <span
        v-if="anchor.basisCode"
        class="anchor__basis"
      >依据 {{ anchor.basisCode }}</span>
    </div>
    <div class="anchor__foot">
      <span class="anchor__source">{{ anchor.sourceLabel ?? '系统标准库（权威）' }}</span>
      <button
        v-if="anchor.jumpPath"
        type="button"
        class="anchor__go"
        @click="emit('navigate', anchor.jumpPath as string)"
      >
        <el-icon :size="12">
          <Right />
        </el-icon>
        查看系统标准库
      </button>
      <span
        v-else
        class="anchor__no-jump"
      >查看入口需 base:lib:list 权限，此处仅展示权威值</span>
    </div>
  </div>
</template>

<style scoped>
.anchor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid var(--lims-accent-soft-3);
  border-radius: var(--lims-r-ctrl);
  background: rgba(var(--lims-accent-rgb), 0.05);
}

.anchor__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.anchor__name {
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  font-weight: 600;
}

.anchor__value {
  color: var(--lims-accent);
  font-family: var(--lims-font-mono);
  font-size: 15px;
  font-weight: 700;
}

.anchor__value em {
  color: var(--lims-muted);
  font-size: 11px;
  font-style: normal;
  font-weight: 400;
}

.anchor__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.anchor__chip {
  padding: 0 7px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  color: var(--lims-ink-2);
  font-size: 11px;
}

.anchor__chip.is-ref {
  border-color: var(--lims-warning-line);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
}

.anchor__basis {
  color: var(--lims-faint);
  font-size: 11px;
}

.anchor__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--lims-hair);
}

.anchor__source {
  color: var(--lims-muted);
  font-size: 11px;
}

.anchor__go {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 10px;
  border: 1px solid var(--lims-accent-soft-3);
  border-radius: var(--lims-r-pill);
  background: var(--lims-accent-soft);
  color: var(--lims-accent);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
}

.anchor__go:hover {
  background: var(--lims-accent-soft-2);
}

.anchor__no-jump {
  color: var(--lims-faint);
  font-size: 11px;
}
</style>
