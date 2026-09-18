<script setup lang="ts">
/**
 * AiMessageList — 会话消息列表（用户气泡 + 助手回答块）。
 *
 * 渲染规则（设计 §2.6）：`answer` 按 `\n` 拆段渲染（**不支持 Markdown**），
 * 引用渲染为 `AiCitationCard`，每条助手回答右下角固定「仅供参考」标注；
 * `refused=true` 用**中性样式**（非红色错误）；流式生成时显示「正在生成」指示。
 */
import AppEmpty from '@/components/common/AppEmpty.vue'
import AiCitationCard from '@/components/ai/AiCitationCard.vue'
import AiFlowGuideCard from '@/components/ai/AiFlowGuideCard.vue'
import AiValueAnchor from '@/components/ai/AiValueAnchor.vue'
import type { AiChatMessage, AiSuggestion } from '@/types/ai'

defineProps<{
  messages: AiChatMessage[]
}>()

const emit = defineEmits<{
  (e: 'suggest', suggestion: AiSuggestion): void
  (e: 'navigate', path: string): void
}>()

/** 纯文本按段拆行（空行忽略），逐段渲染 */
function paragraphs(content: string): string[] {
  return content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

function confidenceLabel(confidence?: string | null): string {
  if (confidence === 'high') return '高置信'
  if (confidence === 'medium') return '中置信'
  if (confidence === 'low') return '低置信'
  return ''
}
</script>

<template>
  <div class="msg-list">
    <AppEmpty
      v-if="messages.length === 0"
      title="开始提问"
      hint="可以询问检验业务、样品状态、标准限量等问题"
    />

    <div
      v-for="msg in messages"
      :key="msg.id"
      class="msg"
      :class="`is-${msg.role}`"
    >
      <!-- 用户 -->
      <div
        v-if="msg.role === 'user'"
        class="msg__user"
      >
        {{ msg.content }}
      </div>

      <!-- 助手 -->
      <div
        v-else
        class="msg__assistant"
        :class="{ 'is-refused': msg.refused }"
      >
        <div class="msg__body">
          <p
            v-for="(para, i) in paragraphs(msg.content)"
            :key="i"
            class="msg__para"
          >
            {{ para }}
          </p>

          <p
            v-if="msg.streaming && !msg.content"
            class="msg__generating"
          >
            正在生成
            <span class="dots"><i /><i /><i /></span>
          </p>

          <p
            v-if="msg.error"
            class="msg__error"
          >
            {{ msg.error }}
          </p>
        </div>

        <!-- 引用卡片 -->
        <div
          v-if="msg.citations.length > 0"
          class="msg__cites"
        >
          <AiCitationCard
            v-for="(cite, idx) in msg.citations"
            :key="idx"
            :citation="cite"
          />
        </div>

        <!-- 流程引导卡片（业务域，事实层确定性装配） -->
        <AiFlowGuideCard
          v-if="msg.flowGuide"
          :guide="msg.flowGuide"
          @navigate="emit('navigate', $event)"
        />

        <!-- 数值对齐卡片（标准域，数值来自系统标准库） -->
        <div
          v-if="msg.valueAnchors.length > 0"
          class="msg__anchors"
        >
          <AiValueAnchor
            v-for="(anchor, idx) in msg.valueAnchors"
            :key="idx"
            :anchor="anchor"
            @navigate="emit('navigate', $event)"
          />
        </div>

        <!-- 拒答 / 建议 -->
        <div
          v-if="msg.refused && !msg.streaming"
          class="msg__refused-chip"
        >
          超出业务范围
        </div>
        <div
          v-if="msg.suggestions.length > 0 && !msg.streaming"
          class="msg__suggest"
        >
          <button
            v-for="(sug, idx) in msg.suggestions"
            :key="idx"
            type="button"
            class="msg__suggest-btn"
            @click="emit('suggest', sug)"
          >
            {{ sug.text }}
          </button>
        </div>

        <!-- 元信息 + 仅供参考标注 -->
        <div
          v-if="!msg.streaming && (msg.confidence || msg.elapsedMs != null)"
          class="msg__meta"
        >
          <span v-if="confidenceLabel(msg.confidence)">{{ confidenceLabel(msg.confidence) }}</span>
          <span v-if="msg.elapsedMs != null">{{ msg.elapsedMs }} ms</span>
        </div>
        <div
          v-if="!msg.streaming && msg.role === 'assistant'"
          class="msg__disclaimer"
        >
          AI 建议，仅供参考；判定以系统规则为准
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg {
  display: flex;
  flex-direction: column;
}

.msg.is-user {
  align-items: flex-end;
}

.msg__user {
  max-width: 84%;
  padding: 8px 12px;
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-accent-soft-2);
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg__assistant {
  max-width: 100%;
  padding: 12px;
  border: 1px solid var(--lims-hair);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card-hover);
}

/* 拒答：中性灰（不是错误红），与 conclusion-* 语义色严格区分 */
.msg__assistant.is-refused {
  border-style: dashed;
  border-color: var(--lims-hair-2);
  background: var(--lims-layer-card);
}

.msg__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.msg__para {
  margin: 0;
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-sm);
  line-height: 1.6;
  word-break: break-word;
}

.msg__generating {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
}

.dots {
  display: inline-flex;
  gap: 3px;
}

.dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--lims-muted);
  animation: ai-dot 1s infinite ease-in-out;
}

.dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes ai-dot {
  0%,
  100% {
    opacity: 0.25;
  }
  50% {
    opacity: 1;
  }
}

.msg__error {
  margin: 0;
  color: var(--lims-warning);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.msg__cites {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.msg__anchors {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg__refused-chip {
  align-self: flex-start;
  margin-top: 8px;
  padding: 1px 8px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  background: rgba(255, 255, 255, 0.04);
  color: var(--lims-muted);
  font-size: 11px;
}

.msg__suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.msg__suggest-btn {
  padding: 4px 10px;
  border: 1px solid var(--lims-accent-soft-3);
  border-radius: var(--lims-r-pill);
  background: var(--lims-accent-soft);
  color: var(--lims-accent);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition:
    background var(--lims-dur-fast) var(--lims-ease-out),
    border-color var(--lims-dur-fast) var(--lims-ease-out);
}

.msg__suggest-btn:hover {
  background: var(--lims-accent-soft-2);
  border-color: var(--lims-accent);
}

.msg__meta {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  color: var(--lims-faint);
  font-size: 11px;
}

/* 仅供参考标注：中性弱化，明确区别于业务结论色 */
.msg__disclaimer {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--lims-hair);
  color: var(--lims-faint);
  font-size: 11px;
  text-align: right;
}

@media (prefers-reduced-motion: reduce) {
  .dots i {
    animation: none;
    opacity: 0.6;
  }
}
</style>
