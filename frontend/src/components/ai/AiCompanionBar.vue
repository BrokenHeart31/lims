<script setup lang="ts">
/**
 * AiCompanionBar — 标准伴随建议条（增量 T05，设计 §2.5 / §5.3）。
 *
 * 形态：悬浮窗头部下方**单行建议条**（默认折叠、不自动展开会话、不自动聚焦输入框、不遮挡操作区）。
 * 动作只有「查看」（打开会话展示引用卡片 + 数值对齐）与「×」（关闭并本会话静默）——
 * **无任何「填入 / 提交」按钮**（助手不代操作，PRD TC）。
 */
import { Close, View } from '@element-plus/icons-vue'
import type { AiCompanionHint } from '@/types/ai'

defineProps<{
  /** 当前展示的建议条 */
  hint: AiCompanionHint
  /** 同一会话还有多少条未展示（>0 时显示 +N） */
  more?: number
}>()

const emit = defineEmits<{
  (e: 'view', hint: AiCompanionHint): void
  (e: 'dismiss', hintKey: string): void
  (e: 'disable'): void
}>()
</script>

<template>
  <div class="comp">
    <span
      class="comp__mark"
      aria-hidden="true"
    />
    <button
      type="button"
      class="comp__text"
      title="查看对应标准出处与数值对齐"
      @click="emit('view', hint)"
    >
      {{ hint.oneLine }}
      <span
        v-if="more && more > 0"
        class="comp__more"
      >+{{ more }}</span>
    </button>
    <button
      type="button"
      class="comp__view"
      @click="emit('view', hint)"
    >
      <el-icon :size="13">
        <View />
      </el-icon>
      查看
    </button>
    <button
      type="button"
      class="comp__icon"
      title="本会话不再提示此类"
      @click="emit('dismiss', hint.hintKey)"
    >
      <el-icon :size="13">
        <Close />
      </el-icon>
    </button>
    <button
      type="button"
      class="comp__off"
      title="关闭标准伴随提示"
      @click="emit('disable')"
    >
      关
    </button>
  </div>
</template>

<style scoped>
.comp {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding: 6px 12px;
  border-bottom: 1px solid var(--lims-hair);
  background: rgba(var(--lims-accent-rgb), 0.05);
}

.comp__mark {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lims-accent);
  box-shadow: 0 0 6px rgba(var(--lims-accent-rgb), 0.5);
}

.comp__text {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--lims-ink-2);
  font-family: inherit;
  font-size: 12px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.comp__text:hover {
  color: var(--lims-ink);
}

.comp__more {
  margin-left: 4px;
  color: var(--lims-faint);
  font-size: 11px;
}

.comp__view {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex: none;
  padding: 2px 8px;
  border: 1px solid var(--lims-accent-soft-3);
  border-radius: var(--lims-r-pill);
  background: var(--lims-accent-soft);
  color: var(--lims-accent);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
}

.comp__view:hover {
  background: var(--lims-accent-soft-2);
}

.comp__icon {
  display: grid;
  flex: none;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: var(--lims-r-xs, 4px);
  background: transparent;
  color: var(--lims-muted);
  cursor: pointer;
  place-items: center;
}

.comp__icon:hover {
  color: var(--lims-ink);
  background: rgba(255, 255, 255, 0.06);
}

.comp__off {
  flex: none;
  border: none;
  background: transparent;
  color: var(--lims-faint);
  font-family: inherit;
  font-size: 10px;
  cursor: pointer;
}

.comp__off:hover {
  color: var(--lims-warning);
}
</style>
