<script setup lang="ts">
/**
 * RollbackTimeline — 流程回溯时间线（正向 + 逆向，设计 §5.5 / PRD §6）。
 *
 * 视觉区分（三类易混淆的动作必须一眼可辨）：
 *   · 正向推进 vs 逆向回退：颜色 + 「正向/逆向」标签；
 *   · 审核退回（S60→S50，业务纠正）vs 回退（任意逐级回退）：色相与文案均不同；
 *   · 回退 vs 恢复：回退=警示色、恢复=品牌色；并标注可否恢复。
 */
import { computed } from 'vue'
import {
  EVENT_TYPE_FORWARD,
  EVENT_TYPE_RECOVER,
  EVENT_TYPE_REPORT,
  EVENT_TYPE_RETURN,
  EVENT_TYPE_ROLLBACK,
  EVENT_TYPE_SIGN,
  EVENT_TYPE_VOID,
  type RollbackEvent,
  type RollbackTimelineVO,
} from '@/types/rollback'
import { sampleStatusInfo } from '@/utils/sampleStatus'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'

const props = defineProps<{
  timeline: RollbackTimelineVO
}>()

const emit = defineEmits<{
  (e: 'recover', rollbackId: number): void
}>()

/** 逆向事件集合（回退方向） */
const REVERSE_TYPES = new Set<number>([
  EVENT_TYPE_RETURN,
  EVENT_TYPE_ROLLBACK,
  EVENT_TYPE_RECOVER,
  EVENT_TYPE_VOID,
])

function isReverse(event: RollbackEvent): boolean {
  return REVERSE_TYPES.has(event.eventType)
}

/** 事件节点颜色：全部取自设计令牌，无硬编码色值 */
function eventColor(event: RollbackEvent): string {
  switch (event.eventType) {
    case EVENT_TYPE_FORWARD:
      return 'var(--lims-info)'
    case EVENT_TYPE_SIGN:
    case EVENT_TYPE_REPORT:
      return 'var(--lims-success)'
    case EVENT_TYPE_RETURN:
      return 'var(--lims-warning)'
    case EVENT_TYPE_ROLLBACK:
      return 'var(--lims-danger)'
    case EVENT_TYPE_RECOVER:
      return 'var(--lims-accent)'
    case EVENT_TYPE_VOID:
      return 'var(--lims-danger)'
    default:
      return 'var(--lims-muted)'
  }
}

/** 事件种类徽标样式 */
function eventTone(event: RollbackEvent): string {
  if (event.eventType === EVENT_TYPE_ROLLBACK) return 'is-rollback'
  if (event.eventType === EVENT_TYPE_RECOVER) return 'is-recover'
  if (event.eventType === EVENT_TYPE_RETURN) return 'is-return'
  if (event.eventType === EVENT_TYPE_VOID) return 'is-void'
  return 'is-forward'
}

function statusLabel(code?: number | null, label?: string | null): string {
  if (label) return label
  if (code == null) return '—'
  return sampleStatusInfo(code).label
}

/** 可回退目标（逐级） */
const rollbackTargets = computed(() =>
  props.timeline.rollbackEdges.map((edge) => ({
    to: edge.to,
    label: statusLabel(edge.to, null),
    group: edge.group,
    groupLabel: edge.groupLabel ?? (edge.group === 2 ? '敏感' : '常规'),
  })),
)
</script>

<template>
  <div class="rb">
    <!-- 概览：当前状态 + 可回退目标 + 被拒路径 -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <div class="rb-overview">
        <div class="rb-overview__cell">
          <span class="rb-overview__label">当前状态</span>
          <StatusBadge tone="info">
            {{ timeline.currentStatusLabel }}
          </StatusBadge>
        </div>
        <div class="rb-overview__cell">
          <span class="rb-overview__label">可回退至（逐级）</span>
          <div
            v-if="rollbackTargets.length > 0"
            class="rb-overview__targets"
          >
            <span
              v-for="t in rollbackTargets"
              :key="t.to"
              class="rb-target"
              :class="{ 'is-sensitive': t.group === 2 }"
            >
              {{ t.label }}
              <em>{{ t.groupLabel }}</em>
            </span>
          </div>
          <span
            v-else
            class="rb-overview__none"
          >当前状态无可回退路径</span>
        </div>
      </div>

      <!-- S80/S90 等不可回退路径：显式展示（不隐藏），并给出替代动作 -->
      <div
        v-if="timeline.rejectedEdges.length > 0"
        class="rb-rejected"
      >
        <p class="rb-rejected__title">
          以下路径不可普通回退（系统显式拒绝）：
        </p>
        <ul class="rb-rejected__list">
          <li
            v-for="(rej, i) in timeline.rejectedEdges"
            :key="i"
            class="rb-rejected__item"
          >
            <span class="rb-rejected__edge">
              {{ statusLabel(rej.from, null) }} → {{ statusLabel(rej.to, null) }}
            </span>
            <span class="rb-rejected__msg">{{ rej.msg }}</span>
            <span class="rb-rejected__alt">替代路径：报告作废 / 召回（report:void）</span>
          </li>
        </ul>
      </div>
    </AppCard>

    <!-- 时间线 -->
    <AppCard
      variant="panel"
      :padding="16"
    >
      <h3 class="rb-title">
        全链路事件（{{ timeline.events.length }}）
      </h3>
      <AppEmpty
        v-if="timeline.events.length === 0"
        title="暂无状态流水"
        hint="该样品尚无状态变更记录"
      />
      <el-timeline v-else>
        <el-timeline-item
          v-for="event in timeline.events"
          :key="event.id"
          :timestamp="event.operatedAt ?? ''"
          :color="eventColor(event)"
          placement="top"
        >
          <div class="rb-event">
            <div class="rb-event__head">
              <span
                class="rb-event__kind"
                :class="eventTone(event)"
              >
                {{ event.eventTypeLabel ?? '事件' }}
              </span>
              <span
                class="rb-event__dir"
                :class="isReverse(event) ? 'is-reverse' : 'is-forward'"
              >
                {{ isReverse(event) ? '逆向' : '正向' }}
              </span>
              <span class="rb-event__action">{{ event.actionLabel ?? '' }}</span>
            </div>
            <div class="rb-event__flow">
              {{ statusLabel(event.fromStatus, event.fromStatusLabel) }}
              <span class="rb-event__arrow">→</span>
              {{ statusLabel(event.toStatus, event.toStatusLabel) }}
            </div>
            <p
              v-if="event.reason"
              class="rb-event__reason"
            >
              原因：{{ event.reason }}
            </p>
            <p
              v-if="event.dataDisposition"
              class="rb-event__disposition"
            >
              数据处置：{{ event.dataDisposition }}
            </p>
            <div class="rb-event__foot">
              <span class="rb-event__op">
                {{ event.operatedBy ?? '—' }}
                <span class="rb-event__source">· {{ event.source ?? '—' }}</span>
              </span>
              <!-- 回退事件：可否恢复 -->
              <template v-if="event.eventType === EVENT_TYPE_ROLLBACK">
                <el-button
                  v-if="event.canRecover && !event.recovered"
                  link
                  type="primary"
                  size="small"
                  @click="emit('recover', event.rollbackId as number)"
                >
                  恢复此回退
                </el-button>
                <span
                  v-else-if="event.recovered"
                  class="rb-event__recovered"
                >已恢复</span>
                <span
                  v-else
                  class="rb-event__irreversible"
                >该回退已产生新下游数据，无法原路恢复</span>
              </template>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </AppCard>
  </div>
</template>

<style scoped>
.rb {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
}

.rb-overview {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) 2fr;
  gap: var(--lims-sp-4);
  align-items: center;
}

.rb-overview__cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rb-overview__label {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
}

.rb-overview__targets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rb-target {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  color: var(--lims-ink-2);
  font-size: 12px;
}

.rb-target.is-sensitive {
  border-color: var(--lims-warning-line);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
}

.rb-target em {
  color: var(--lims-faint);
  font-style: normal;
  font-size: 11px;
}

.rb-overview__none {
  color: var(--lims-muted);
  font-size: var(--lims-fs-sm);
}

.rb-rejected {
  margin-top: var(--lims-sp-4);
  padding: 10px 12px;
  border: 1px solid var(--lims-danger-line);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-danger-soft);
}

.rb-rejected__title {
  margin: 0 0 6px;
  color: var(--lims-danger);
  font-size: var(--lims-fs-xs);
  font-weight: 600;
}

.rb-rejected__list {
  margin: 0;
  padding-left: 18px;
}

.rb-rejected__item {
  margin: 3px 0;
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.rb-rejected__edge {
  font-family: var(--lims-font-mono);
  color: var(--lims-ink);
}

.rb-rejected__msg {
  margin-left: 6px;
}

.rb-rejected__alt {
  display: block;
  color: var(--lims-muted);
}

.rb-title {
  margin: 0 0 var(--lims-sp-3);
  color: var(--lims-ink);
  font-size: var(--lims-fs-base);
  font-weight: 600;
}

.rb-event {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rb-event__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rb-event__kind {
  padding: 1px 8px;
  border-radius: var(--lims-r-pill);
  font-size: 11px;
  border: 1px solid transparent;
}

.rb-event__kind.is-forward {
  color: var(--lims-info);
  background: var(--lims-info-soft);
  border-color: var(--lims-info-line);
}

.rb-event__kind.is-return {
  color: var(--lims-warning);
  background: var(--lims-warning-soft);
  border-color: var(--lims-warning-line);
}

.rb-event__kind.is-rollback {
  color: var(--lims-danger);
  background: var(--lims-danger-soft);
  border-color: var(--lims-danger-line);
}

.rb-event__kind.is-recover {
  color: var(--lims-accent);
  background: var(--lims-accent-soft);
  border-color: var(--lims-accent-soft-3);
}

.rb-event__kind.is-void {
  color: var(--lims-danger);
  background: var(--lims-danger-soft);
  border-color: var(--lims-danger-line);
}

.rb-event__dir {
  font-size: 11px;
  font-weight: 600;
}

.rb-event__dir.is-forward {
  color: var(--lims-success);
}

.rb-event__dir.is-reverse {
  color: var(--lims-danger);
}

.rb-event__action {
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
  font-weight: 500;
}

.rb-event__flow {
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-sm);
}

.rb-event__arrow {
  margin: 0 6px;
  color: var(--lims-faint);
}

.rb-event__reason,
.rb-event__disposition {
  margin: 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.rb-event__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lims-sp-3);
  margin-top: 2px;
}

.rb-event__op {
  color: var(--lims-faint);
  font-size: 11px;
}

.rb-event__source {
  color: var(--lims-faint);
}

.rb-event__recovered {
  color: var(--lims-success);
  font-size: 12px;
}

.rb-event__irreversible {
  color: var(--lims-muted);
  font-size: 11px;
}
</style>
