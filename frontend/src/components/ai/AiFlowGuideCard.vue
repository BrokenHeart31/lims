<script setup lang="ts">
/**
 * AiFlowGuideCard — 业务全流程引导卡片（增量 T05，设计 §2.6 / §2.7 / §5.3）。
 *
 * 事实层 100% 由后端确定性装配（`FlowGuideVO`，不调模型）；本组件只做**展示 + 只读跳转**。
 *
 * 权限收敛（TE）：`hasPermission=false` 的步骤**灰显、不给跳转按钮**，改为一行说明
 * 「需 X 权限，联系 Rn 开通」，**不诱导越权**。每步以「下一步该做什么」为主语（nextAction）。
 */
import { computed } from 'vue'
import { Right } from '@element-plus/icons-vue'
import type { FlowGuide, FlowGuideStep } from '@/types/ai'

const props = defineProps<{
  guide: FlowGuide
}>()

const emit = defineEmits<{
  (e: 'navigate', path: string): void
}>()

/** 角色编码 → 可读文案（供「找谁开权限」） */
function actorLabel(role?: string | null): string {
  if (!role) return '管理员'
  if (role.includes('R100')) return 'R100 / R2（审核·签发）'
  if (role.includes('R2')) return 'R2（任务管理员）'
  if (role.includes('R1')) return 'R1（登记员）'
  if (role.includes('R3')) return 'R3（检验员）'
  return role
}

/** 步骤序号（1-based）；当前步高亮 */
function isClickable(step: FlowGuideStep): boolean {
  return step.hasPermission && !!step.entryPath
}

const hasCurrent = computed(() => props.guide.stageIndex > 0)

function onStep(step: FlowGuideStep): void {
  if (isClickable(step) && step.entryPath) emit('navigate', step.entryPath)
}
</script>

<template>
  <div class="flow">
    <div class="flow__head">
      <span class="flow__title">业务流程引导</span>
      <span
        v-if="guide.currentStatusLabel"
        class="flow__here"
      >当前：{{ guide.currentStatusLabel }}</span>
      <span
        v-else
        class="flow__here is-muted"
      >流程总览</span>
    </div>

    <ol class="flow__steps">
      <li
        v-for="(step, idx) in guide.steps"
        :key="idx"
        class="flow__step"
        :class="{
          'is-current': step.isCurrent,
          'is-dim': hasCurrent && !step.isCurrent,
          'is-denied': !step.hasPermission,
        }"
      >
        <span class="flow__no">{{ idx + 1 }}</span>
        <div class="flow__body">
          <div class="flow__line">
            <span class="flow__stage">{{ step.stageLabel }}</span>
            <span class="flow__action">{{ step.nextAction }}</span>
            <span
              v-if="step.isCurrent"
              class="flow__tag"
            >当前步</span>
          </div>
          <p
            v-if="step.fieldHint"
            class="flow__hint"
          >
            {{ step.fieldHint }}
          </p>

          <button
            v-if="isClickable(step)"
            type="button"
            class="flow__go"
            @click="onStep(step)"
          >
            <el-icon :size="12">
              <Right />
            </el-icon>
            去「{{ step.nextAction }}」
          </button>
          <p
            v-else
            class="flow__denied"
          >
            需 <code>{{ step.requiredPermission }}</code> 权限，当前角色不具备；请联系
            {{ actorLabel(step.actorRole) }} 开通。
          </p>
        </div>
      </li>
    </ol>
    <p class="flow__note">
      AI 建议，仅供参考；判定以系统规则为准
    </p>
  </div>
</template>

<style scoped>
.flow {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card);
}

.flow__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.flow__title {
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  font-weight: 600;
}

.flow__here {
  padding: 0 8px;
  border-radius: var(--lims-r-pill);
  background: rgba(var(--lims-accent-rgb), 0.12);
  color: var(--lims-accent);
  font-size: 11px;
}

.flow__here.is-muted {
  background: rgba(255, 255, 255, 0.05);
  color: var(--lims-muted);
}

.flow__steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.flow__step {
  display: flex;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: var(--lims-r-ctrl);
}

.flow__step.is-current {
  border-color: var(--lims-accent-soft-3);
  background: rgba(var(--lims-accent-rgb), 0.06);
}

.flow__step.is-dim {
  opacity: 0.55;
}

.flow__step.is-denied {
  opacity: 0.6;
}

.flow__no {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  color: var(--lims-muted);
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.flow__step.is-current .flow__no {
  background: var(--lims-accent);
  color: var(--lims-on-accent, #fff);
}

.flow__body {
  flex: 1;
  min-width: 0;
}

.flow__line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.flow__stage {
  color: var(--lims-ink);
  font-size: 12px;
  font-weight: 600;
}

.flow__action {
  color: var(--lims-ink-2);
  font-size: 12px;
}

.flow__tag {
  padding: 0 6px;
  border-radius: var(--lims-r-pill);
  background: var(--lims-accent-soft);
  color: var(--lims-accent);
  font-size: 10px;
}

.flow__hint {
  margin: 3px 0 0;
  color: var(--lims-faint);
  font-size: 11px;
  line-height: 1.5;
}

.flow__go {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-top: 5px;
  padding: 2px 10px;
  border: 1px solid var(--lims-accent-soft-3);
  border-radius: var(--lims-r-pill);
  background: var(--lims-accent-soft);
  color: var(--lims-accent);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
}

.flow__go:hover {
  background: var(--lims-accent-soft-2);
}

.flow__denied {
  margin: 4px 0 0;
  color: var(--lims-warning);
  font-size: 11px;
  line-height: 1.5;
}

.flow__denied code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--lims-warning-soft);
  font-family: var(--lims-font-mono);
}

.flow__note {
  margin: 0;
  padding-top: 6px;
  border-top: 1px dashed var(--lims-hair);
  color: var(--lims-faint);
  font-size: 10px;
  text-align: right;
}
</style>
