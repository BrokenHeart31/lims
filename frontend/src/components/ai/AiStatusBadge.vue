<script setup lang="ts">
/**
 * AiStatusBadge — AI 服务状态指示 + 启动指引 popover（设计 §5.5）。
 *
 * 离线不是错误：用中性/警示色圆点表达，点开给出可执行的修复指引
 * （启动脚本路径），绝不弹全局错误、不阻塞业务。
 */
import { computed } from 'vue'
import { useAiAssistantStore } from '@/stores/aiAssistant'
import { useAuthStore } from '@/stores/auth'

const store = useAiAssistantStore()
const authStore = useAuthStore()

/**
 * 是否向当前用户暴露「启动/部署脚本路径」——F31（2026-10-08）。
 * 只有具备 `ai:kb:import`（知识库导入，运维/管理员）权限的用户才需要也才允许看到
 * 服务器脚本路径；普通业务用户只应看到「联系管理员」这类友好提示，避免泄露运维细节。
 */
const canManageAi = computed(() => authStore.hasPermission('ai:kb:import'))

/** 状态色：在线=成功；离线/未就绪=警示（属「需关注」而非「错误」） */
const tone = computed<'success' | 'warning' | 'neutral'>(() => {
  if (store.statusLoading && !store.status) return 'neutral'
  if (!store.status) return 'neutral'
  return store.online && store.modelReady ? 'success' : 'warning'
})

const label = computed(() => {
  if (store.statusLoading && !store.status) return '探测中…'
  if (!store.status) return '状态未知'
  if (!store.online) return 'AI 离线'
  if (!store.modelReady) return '模型未就绪'
  return 'AI 在线'
})

/**
 * popover 顶部提示文案（F31）：后端 statusHint 会内嵌启动/部署脚本路径，
 * 仅对具备 ai:kb:import 的用户原样展示；其余用户在「未就绪/离线」时只给通用提示。
 */
const displayHint = computed(() => {
  if (canManageAi.value) return store.statusHint
  if (store.online && store.modelReady) return store.statusHint
  return 'AI 暂不可用，请联系管理员。'
})
</script>

<template>
  <el-popover
    :width="292"
    trigger="click"
    placement="top-end"
    popper-class="lims-popover"
  >
    <template #reference>
      <span
        class="ai-status"
        :class="`is-${tone}`"
      >
        <span
          class="ai-status__dot"
          aria-hidden="true"
        />
        <span class="ai-status__text">{{ label }}</span>
      </span>
    </template>
    <div class="ai-status__pop">
      <p class="ai-status__hint">
        {{ displayHint }}
      </p>
      <template v-if="store.online && store.modelReady">
        <p class="ai-status__meta">
          模型：{{ store.status?.model || '—' }}
        </p>
        <p class="ai-status__meta">
          地址：{{ store.status?.baseUrl || '—' }}
        </p>
        <p class="ai-status__meta">
          往返延迟：{{ store.status?.latencyMs ?? '—' }} ms
        </p>
      </template>
      <template v-else-if="store.online && !store.modelReady">
        <template v-if="canManageAi">
          <p class="ai-status__meta">
            已连接服务，但目标模型尚未拉取。请先执行部署脚本：
          </p>
          <code class="ai-status__cmd">ai/scripts/deploy-ollama.ps1</code>
        </template>
        <p
          v-else
          class="ai-status__meta"
        >
          已连接服务，但模型尚未就绪。请联系管理员处理。
        </p>
      </template>
      <template v-else>
        <template v-if="canManageAi">
          <p class="ai-status__meta">
            本地模型服务未启动；业务功能不受影响。请执行：
          </p>
          <code class="ai-status__cmd">{{ store.startScript }}</code>
        </template>
        <p
          v-else
          class="ai-status__meta"
        >
          AI 暂不可用，请联系管理员。
        </p>
      </template>
      <div class="ai-status__foot">
        <el-button
          link
          type="primary"
          size="small"
          :loading="store.statusLoading"
          @click="store.refreshStatus()"
        >
          重新检测
        </el-button>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.ai-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  background: rgba(255, 255, 255, 0.04);
  font-size: 11px;
  line-height: 1.6;
  white-space: nowrap;
  cursor: pointer;
}

.ai-status__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

.ai-status.is-success {
  color: var(--lims-success);
}

.ai-status.is-warning {
  color: var(--lims-warning);
}

.ai-status.is-neutral {
  color: var(--lims-muted);
}

.ai-status__text {
  color: var(--lims-ink-2);
}

.ai-status__pop {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-status__hint {
  margin: 0;
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  line-height: 1.5;
}

.ai-status__meta {
  margin: 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.ai-status__cmd {
  display: block;
  padding: 6px 8px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-bg-deep);
  color: var(--lims-accent);
  font-family: var(--lims-font-mono);
  font-size: 11px;
  word-break: break-all;
}

.ai-status__foot {
  display: flex;
  justify-content: flex-end;
}
</style>
