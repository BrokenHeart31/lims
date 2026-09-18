<script setup lang="ts">
/**
 * AiPanel — 悬浮窗展开态（对话面板）。
 *
 * 三态：collapsed（气泡，见 AiBubble）↔ expanded（欢迎/上下文）↔ conversation（消息列表）。
 * 面板头部为拖拽手柄（Pointer Events 由父组件处理）；输入区在离线时**禁用并给出原因**，
 * 生成中提供「停止」。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Close, Delete, Promotion } from '@element-plus/icons-vue'
import { useAiAssistantStore } from '@/stores/aiAssistant'
import { sampleStatusInfo } from '@/utils/sampleStatus'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AiStatusBadge from '@/components/ai/AiStatusBadge.vue'
import AiMessageList from '@/components/ai/AiMessageList.vue'
import AiCompanionBar from '@/components/ai/AiCompanionBar.vue'
import type { AiCompanionHint, AiSuggestion } from '@/types/ai'

const store = useAiAssistantStore()
const router = useRouter()

const emit = defineEmits<{
  (e: 'dragstart', ev: PointerEvent): void
}>()

function onDragStart(e: PointerEvent): void {
  emit('dragstart', e)
}

/** 头部按钮的 pointerdown 不应触发拖拽：阻止冒泡到头部手柄 */
function stopDragStart(e: Event): void {
  e.stopPropagation()
}

const draft = ref('')

/** 输入区禁用原因（非空即禁用 + 展示原因） */
const disabledReason = computed(() => {
  if (!store.online) {
    return `AI 服务离线，请先启动本地模型：${store.startScript}`
  }
  return ''
})
const inputDisabled = computed(() => !store.online)

/** 上下文标签（带入当前样品的页面联动信息） */
const contextChips = computed<{ label: string; value: string }[]>(() => {
  const ctx = store.context
  if (!ctx) return []
  const chips: { label: string; value: string }[] = []
  if (ctx.sampleNo) chips.push({ label: '样品', value: ctx.sampleNo })
  if (ctx.status != null) chips.push({ label: '状态', value: sampleStatusInfo(ctx.status).label })
  if (ctx.stdNo) chips.push({ label: '标准', value: ctx.stdNo })
  return chips
})

const examples = ['解释「检验中」状态', '这个样品下一步该做什么', '走一遍新样品登记流程', 'GB 2762 铅的限量是多少']

const scroller = ref<HTMLElement | null>(null)

/** 新消息或流式增量时自动滚到底部 */
watch(
  () => {
    const last = store.messages[store.messages.length - 1]
    return `${store.messages.length}:${last ? last.content.length : 0}`
  },
  () => {
    void nextTick(() => {
      const el = scroller.value
      if (el) el.scrollTop = el.scrollHeight
    })
  },
)

function onSend(): void {
  const q = draft.value.trim()
  if (!q || !store.online) return
  draft.value = ''
  void store.send(q)
}

function onSuggestion(suggestion: AiSuggestion): void {
  store.useSuggestion(suggestion)
}

/** 当前展示的建议条（取第一条可视） */
const activeHint = computed<AiCompanionHint | null>(() => store.visibleHints[0] ?? null)
/** 未展示的其余条数 */
const moreHints = computed(() => Math.max(0, store.visibleHints.length - 1))

/** 只读跳转（引导卡片 / 数值对齐卡片）：不代填、不代提交 */
function onNavigate(path: string): void {
  if (!path) return
  store.collapse()
  void router.push(path)
}

function onViewHint(hint: AiCompanionHint): void {
  store.openHint(hint)
}
</script>

<template>
  <section class="panel">
    <!-- 头部：拖拽手柄 -->
    <header
      class="panel__head"
      @pointerdown="onDragStart"
    >
      <div class="panel__title">
        <span
          class="panel__mark"
          aria-hidden="true"
        />
        <span class="panel__name">AI 助手</span>
        <AiStatusBadge />
      </div>
      <div class="panel__head-actions">
        <button
          type="button"
          class="panel__icon-btn"
          title="清空会话"
          @pointerdown="stopDragStart"
          @click="store.clearConversation()"
        >
          <el-icon :size="15">
            <Delete />
          </el-icon>
        </button>
        <button
          type="button"
          class="panel__icon-btn"
          title="收起"
          @pointerdown="stopDragStart"
          @click="store.collapse()"
        >
          <el-icon :size="15">
            <Close />
          </el-icon>
        </button>
      </div>
    </header>

    <!-- 标准伴随建议条（头部下方单行，默认折叠） -->
    <AiCompanionBar
      v-if="activeHint"
      :hint="activeHint"
      :more="moreHints"
      @view="onViewHint"
      @dismiss="store.dismissHint"
      @disable="store.setCompanionEnabled(false)"
    />

    <!-- 上下文标签 -->
    <div
      v-if="contextChips.length > 0"
      class="panel__context"
    >
      <span
        v-for="chip in contextChips"
        :key="chip.label"
        class="panel__chip"
      >
        <em>{{ chip.label }}</em>{{ chip.value }}
      </span>
      <button
        type="button"
        class="panel__chip-clear"
        title="清除上下文"
        @click="store.clearContext()"
      >
        清除
      </button>
    </div>

    <!-- 消息区 / 欢迎态 -->
    <div
      ref="scroller"
      class="panel__body"
    >
      <!-- 流程引导卡片（页面打开样品时由 store.openFlowGuide 装配；事实层确定性） -->
      <AiFlowGuideCard
        v-if="store.flowGuide"
        class="panel__flow"
        :guide="store.flowGuide"
        @navigate="onNavigate"
      />
      <AiMessageList
        v-if="store.messages.length > 0"
        :messages="store.messages"
        @suggest="onSuggestion"
        @navigate="onNavigate"
      />
      <div
        v-else
        class="panel__welcome"
      >
        <AppEmpty
          title="你好，我是 LIMS AI 助手"
          hint="我可以解释样品状态、业务规则，或按标准号检索限量条款。答案均标注出处，仅供参考。"
        />
        <div class="panel__examples">
          <button
            v-for="ex in examples"
            :key="ex"
            type="button"
            class="panel__example"
            @click="draft = ex"
          >
            {{ ex }}
          </button>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <footer class="panel__foot">
      <p
        v-if="disabledReason"
        class="panel__disabled-reason"
      >
        {{ disabledReason }}
      </p>
      <div class="panel__input-row">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="2"
          resize="none"
          :disabled="inputDisabled"
          :placeholder="inputDisabled ? 'AI 服务离线，暂不可用' : '输入问题，回车发送（Shift+Enter 换行）'"
          @keydown.enter.exact.prevent="onSend"
        />
        <el-button
          v-if="store.isGenerating"
          class="panel__send"
          type="warning"
          plain
          @click="store.stop()"
        >
          停止
        </el-button>
        <el-button
          v-else
          class="panel__send"
          type="primary"
          :icon="Promotion"
          :disabled="inputDisabled || !draft.trim()"
          @click="onSend"
        >
          发送
        </el-button>
      </div>
      <p class="panel__foot-note">
        AI 建议，仅供参考；判定以系统规则为准
      </p>
    </footer>
  </section>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  width: 384px;
  max-height: min(70vh, 620px);
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-card);
  background: var(--lims-layer-elevated);
  box-shadow: var(--lims-shadow-pop);
  overflow: hidden;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: none;
  padding: 10px 12px;
  border-bottom: 1px solid var(--lims-hair);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.panel__head:active {
  cursor: grabbing;
}

.panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel__mark {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  background: var(--lims-brand-gradient, linear-gradient(135deg, var(--lims-accent), var(--lims-accent-deep)));
  box-shadow: 0 0 10px rgba(var(--lims-accent-rgb), 0.24);
}

.panel__name {
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  font-weight: 600;
}

.panel__head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.panel__icon-btn {
  display: grid;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: var(--lims-r-xs, 4px);
  background: transparent;
  color: var(--lims-muted);
  cursor: pointer;
  place-items: center;
  transition:
    color var(--lims-dur-fast) var(--lims-ease-out),
    background var(--lims-dur-fast) var(--lims-ease-out);
}

.panel__icon-btn:hover {
  color: var(--lims-ink);
  background: rgba(255, 255, 255, 0.06);
}

.panel__context {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: none;
  padding: 8px 12px;
  border-bottom: 1px solid var(--lims-hair);
  background: rgba(var(--lims-accent-rgb), 0.04);
}

.panel__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border: 1px solid var(--lims-accent-soft-3);
  border-radius: var(--lims-r-pill);
  color: var(--lims-ink-2);
  font-size: 11px;
}

.panel__chip em {
  color: var(--lims-faint);
  font-style: normal;
}

.panel__chip-clear {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--lims-muted);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
}

.panel__chip-clear:hover {
  color: var(--lims-accent);
}

.panel__body {
  flex: 1;
  min-height: 140px;
  padding: 12px;
  overflow-y: auto;
}

.panel__flow {
  margin-bottom: 12px;
}

.panel__welcome {
  display: flex;
  flex-direction: column;
}

.panel__examples {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.panel__example {
  padding: 7px 10px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card);
  color: var(--lims-ink-2);
  font-family: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--lims-dur-fast) var(--lims-ease-out),
    color var(--lims-dur-fast) var(--lims-ease-out);
}

.panel__example:hover {
  border-color: var(--lims-accent);
  color: var(--lims-ink);
}

.panel__foot {
  flex: none;
  padding: 10px 12px;
  border-top: 1px solid var(--lims-hair);
}

.panel__disabled-reason {
  margin: 0 0 8px;
  padding: 6px 8px;
  border: 1px solid var(--lims-warning-line);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
  font-size: 11px;
  line-height: 1.5;
  word-break: break-all;
}

.panel__input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.panel__send {
  flex: none;
}

.panel__foot-note {
  margin: 8px 0 0;
  color: var(--lims-faint);
  font-size: 10px;
  text-align: right;
}
</style>
