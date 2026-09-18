<script setup lang="ts">
/**
 * AiFloatingAssistant — AI 助手悬浮窗主体（设计 §2.7）。
 *
 * - 宿主：仅挂在 `MainLayout`，通过 `Teleport to="body"` 渲染，**不新增外壳内 DOM 层级**，
 *   因此不影响既有三区贴合布局与多分辨率 `scrollWidth == clientWidth` 基线；
 * - 拖拽：原生 Pointer Events（窗口级 move/up 监听）+ 视口边界钳制（拖动/缩放/切换态都不出屏）；
 * - 位置记忆：`localStorage['lims_ai_panel_pos']`（键风格与 `lims_access_token` 一致）；
 * - 三态：collapsed（气泡）/ expanded（欢迎）/ conversation（对话），由 store 状态机驱动。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAiAssistantStore } from '@/stores/aiAssistant'
import AiBubble from '@/components/ai/AiBubble.vue'
import AiPanel from '@/components/ai/AiPanel.vue'

const store = useAiAssistantStore()

const rootRef = ref<HTMLElement | null>(null)

/** 未记忆位置时的默认落点（右下角），挂载后按视口计算 */
const fallback = ref<{ x: number; y: number }>({ x: 24, y: 24 })

const posX = computed(() => store.position?.x ?? fallback.value.x)
const posY = computed(() => store.position?.y ?? fallback.value.y)
const rootStyle = computed(() => ({ left: `${posX.value}px`, top: `${posY.value}px` }))

const dragging = ref(false)
let offsetX = 0
let offsetY = 0
let startX = 0
let startY = 0
/** 本次手势是否发生过位移（用于区分「点击展开」与「拖动」） */
let moved = false

function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max)
}

/** 把当前位置钳制到视口内（拖动 / 窗口缩放 / 状态切换后调用） */
function applyClamp(): void {
  const el = rootRef.value
  if (!el) return
  const maxX = Math.max(0, window.innerWidth - el.offsetWidth)
  const maxY = Math.max(0, window.innerHeight - el.offsetHeight)
  const curX = store.position?.x ?? fallback.value.x
  const curY = store.position?.y ?? fallback.value.y
  const nextX = clamp(curX, 0, maxX)
  const nextY = clamp(curY, 0, maxY)
  if (nextX !== curX || nextY !== curY) {
    store.setPosition(nextX, nextY)
  }
}

function startDrag(e: PointerEvent): void {
  const el = rootRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  offsetX = e.clientX - rect.left
  offsetY = e.clientY - rect.top
  startX = e.clientX
  startY = e.clientY
  moved = false
  dragging.value = true
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  window.addEventListener('pointercancel', onUp)
}

function onMove(e: PointerEvent): void {
  if (!dragging.value) return
  if (Math.abs(e.clientX - startX) > 3 || Math.abs(e.clientY - startY) > 3) {
    moved = true
  }
  const el = rootRef.value
  if (!el) return
  const x = clamp(e.clientX - offsetX, 0, Math.max(0, window.innerWidth - el.offsetWidth))
  const y = clamp(e.clientY - offsetY, 0, Math.max(0, window.innerHeight - el.offsetHeight))
  store.setPosition(x, y)
}

function onUp(): void {
  dragging.value = false
  window.removeEventListener('pointermove', onMove)
  window.removeEventListener('pointerup', onUp)
  window.removeEventListener('pointercancel', onUp)
}

function onBubbleOpen(): void {
  // 拖动结束时浏览器仍会派发 click，避免「拖完就意外展开」
  if (moved) return
  store.expand()
}

onMounted(() => {
  if (!store.available) return
  // 默认贴右下角（24px 边距），随后交给 applyClamp 依据真实尺寸矫正
  fallback.value = {
    x: window.innerWidth - 52 - 24,
    y: window.innerHeight - 52 - 24,
  }
  void nextTick(applyClamp)
  window.addEventListener('resize', applyClamp)
  void store.refreshStatus()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', applyClamp)
  onUp()
})

// 气泡 ↔ 面板尺寸不同：状态切换后重新钳制，避免展开后跑出屏幕
watch(
  () => store.panelState,
  () => {
    void nextTick(applyClamp)
  },
)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="store.available"
      ref="rootRef"
      class="ai-fa"
      :class="{ 'is-dragging': dragging }"
      :style="rootStyle"
    >
      <AiBubble
        v-if="store.panelState === 'collapsed'"
        :online="store.online"
        :generating="store.isGenerating"
        :has-hint="store.hasCompanionBadge"
        @dragstart="startDrag"
        @open="onBubbleOpen"
      />
      <AiPanel
        v-else
        @dragstart="startDrag"
      />
    </div>
  </Teleport>
</template>

<style scoped>
.ai-fa {
  position: fixed;
  z-index: var(--lims-z-overlay);
}

.ai-fa.is-dragging {
  user-select: none;
}
</style>
