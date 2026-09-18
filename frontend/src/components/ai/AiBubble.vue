<script setup lang="ts">
/**
 * AiBubble — 悬浮窗收起态（右下角气泡）。
 *
 * 纯展示 + 手势出口：拖动由父组件统一处理（Pointer Events），
 * 点击（未发生拖动）由父组件判定后打开面板。
 */
import { ChatDotRound } from '@element-plus/icons-vue'

defineProps<{
  online: boolean
  generating: boolean
  /** 是否有标准伴随提示待展示（不打扰小角标） */
  hasHint?: boolean
}>()

const emit = defineEmits<{
  (e: 'dragstart', ev: PointerEvent): void
  (e: 'open'): void
}>()

function onDragStart(e: PointerEvent): void {
  emit('dragstart', e)
}
</script>

<template>
  <button
    type="button"
    class="bubble"
    title="AI 助手（可拖拽移动，点击展开）"
    @pointerdown="onDragStart"
    @click="emit('open')"
  >
    <el-icon :size="22">
      <ChatDotRound />
    </el-icon>
    <span
      v-if="generating"
      class="bubble__spin"
      aria-hidden="true"
    />
    <span
      v-if="hasHint && !generating"
      class="bubble__hint"
      aria-hidden="true"
    />
    <span
      class="bubble__dot"
      :class="{ 'is-offline': !online }"
      aria-hidden="true"
    />
  </button>
</template>

<style scoped>
.bubble {
  position: relative;
  display: grid;
  width: 52px;
  height: 52px;
  border: 1px solid var(--lims-hair-2);
  border-radius: 50%;
  background: var(--lims-layer-elevated);
  color: var(--lims-accent);
  box-shadow: var(--lims-shadow-pop);
  cursor: grab;
  place-items: center;
  touch-action: none;
  transition:
    transform var(--lims-dur-fast) var(--lims-ease-out),
    border-color var(--lims-dur-fast) var(--lims-ease-out);
}

.bubble:hover {
  transform: translateY(-2px);
  border-color: var(--lims-accent);
}

.bubble:active {
  cursor: grabbing;
}

.bubble__dot {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--lims-success);
  box-shadow: 0 0 0 2px var(--lims-layer-elevated);
}

.bubble__dot.is-offline {
  background: var(--lims-warning);
}

/* 标准伴随提示角标（不打扰：仅一个点，不自动展开） */
.bubble__hint {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--lims-accent);
  box-shadow: 0 0 0 2px var(--lims-layer-elevated);
}

/* 生成中的旋转环（尊重 reduced-motion） */
.bubble__spin {
  position: absolute;
  inset: -3px;
  border: 2px solid transparent;
  border-top-color: var(--lims-accent);
  border-radius: 50%;
  animation: bubble-spin 0.9s linear infinite;
}

@keyframes bubble-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .bubble__spin {
    animation: none;
    border-top-color: var(--lims-accent);
    opacity: 0.5;
  }
}
</style>
