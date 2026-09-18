<script setup lang="ts">
/**
 * RollbackDialog — 回退确认框（PRD B-05 / 设计 §5.5）。
 *
 * 流程：打开 → 拉时间线取「可回退目标」→ 选目标 → 调 `preview`（下游影响）→ 填原因
 * → 必要时二次确认 → `execute`。确认框内**必须可见**：当前→目标、将失效的下游数据
 * （计数 + 清单）、原因必填、二次确认、不可逆提示；被拒边展示替代路径（作废/召回）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getRollbackTimelineApi, previewRollbackApi } from '@/api/rollback'
import { executeRollbackApi } from '@/api/rollback'
import type {
  RollbackActionResultVO,
  RollbackPreviewVO,
  RollbackTimelineVO,
} from '@/types/rollback'
import { sampleStatusInfo } from '@/utils/sampleStatus'

const props = defineProps<{
  modelValue: boolean
  sampleId: number
  sampleNo?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'done', result: RollbackActionResultVO): void
}>()

const authStore = useAuthStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const timelineLoading = ref(false)
const previewLoading = ref(false)
const submitting = ref(false)
const timeline = ref<RollbackTimelineVO | null>(null)
const targetStatus = ref<number | undefined>(undefined)
const preview = ref<RollbackPreviewVO | null>(null)
const reason = ref('')
const secondConfirmed = ref(false)

/** 是否具备敏感回退权限 */
const sensitiveAllowed = computed(() => authStore.hasPermission('rollback:sensitive'))

/** 可回退目标（逐级） */
const targets = computed(() => {
  const tl = timeline.value
  if (!tl) return []
  return tl.canRollbackTo.map((code) => ({
    code,
    label: sampleStatusInfo(code).label,
  }))
})

/** 是否必须二次确认（敏感边 / 服务端要求） */
const needSecond = computed(
  () => !!preview.value && (preview.value.needSecondConfirm || preview.value.needSensitive),
)

const canSubmit = computed(() => {
  const p = preview.value
  if (!p || !p.allowed || targetStatus.value == null) return false
  if (p.reasonRequired && !reason.value.trim()) return false
  if (needSecond.value && !secondConfirmed.value) return false
  if (p.needSensitive && !sensitiveAllowed.value) return false
  return true
})

async function loadPreview(): Promise<void> {
  if (targetStatus.value == null) return
  previewLoading.value = true
  try {
    preview.value = await previewRollbackApi({
      sampleId: props.sampleId,
      targetStatus: targetStatus.value,
    })
  } catch {
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}

async function openLoad(): Promise<void> {
  reason.value = ''
  secondConfirmed.value = false
  preview.value = null
  timeline.value = null
  targetStatus.value = undefined
  timelineLoading.value = true
  try {
    const tl = await getRollbackTimelineApi(props.sampleId)
    timeline.value = tl
    targetStatus.value = tl.canRollbackTo.length > 0 ? tl.canRollbackTo[0] : undefined
  } catch {
    timeline.value = null
  } finally {
    timelineLoading.value = false
  }
  if (targetStatus.value != null) await loadPreview()
}

watch(visible, (open) => {
  if (open) void openLoad()
})

watch(targetStatus, () => {
  if (visible.value && targetStatus.value != null) {
    secondConfirmed.value = false
    void loadPreview()
  }
})

async function submit(): Promise<void> {
  const p = preview.value
  if (!p || !p.allowed || targetStatus.value == null) return
  if (p.reasonRequired && !reason.value.trim()) {
    ElMessage.warning('请填写回退原因（不少于 4 字，便于审计追溯）')
    return
  }
  if (needSecond.value && !secondConfirmed.value) {
    ElMessage.warning('该回退需二次确认，请勾选确认项')
    return
  }
  if (p.needSensitive && !sensitiveAllowed.value) {
    ElMessage.warning('敏感回退需要「业务管理员 / 系统管理员」权限')
    return
  }
  submitting.value = true
  try {
    const res = await executeRollbackApi({
      sampleId: props.sampleId,
      targetStatus: targetStatus.value,
      reason: reason.value.trim(),
      secondConfirmed: secondConfirmed.value,
    })
    ElMessage.success(`回退成功：${res.statusLabel ?? ''}`)
    visible.value = false
    emit('done', res)
  } catch {
    // 请求层已统一提示（4101~4108 业务码）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="流程回退确认"
    width="620px"
    align-center
    :close-on-click-modal="false"
  >
    <div
      v-loading="timelineLoading"
      class="rbd"
    >
      <!-- 无可用回退路径 -->
      <template v-if="timeline && timeline.canRollbackTo.length === 0">
        <p class="rbd__blocked">
          样品「{{ timeline.sampleNo }}」当前状态为「{{ timeline.currentStatusLabel }}」，无可用普通回退路径。
        </p>
        <div
          v-if="timeline.rejectedEdges.length > 0"
          class="rbd__alt"
        >
          <p
            v-for="(rej, i) in timeline.rejectedEdges"
            :key="i"
            class="rbd__alt-item"
          >
            {{ rej.msg }} —— 替代动作：报告作废 / 召回（需 report:void 权限）。
          </p>
        </div>
      </template>

      <template v-else>
        <!-- 目标选择 -->
        <div class="rbd__section">
          <span class="rbd__label">回退目标（仅支持逐级）</span>
          <el-radio-group
            v-model="targetStatus"
            class="rbd__targets"
          >
            <el-radio
              v-for="t in targets"
              :key="t.code"
              :value="t.code"
            >
              {{ t.label }}
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 预览：当前 → 目标 + 下游失效 -->
        <div
          v-loading="previewLoading"
          class="rbd__section"
        >
          <template v-if="preview">
            <template v-if="preview.allowed">
              <div class="rbd__flow">
                <span class="rbd__status">{{ preview.fromStatusLabel }}</span>
                <span class="rbd__arrow">→</span>
                <span class="rbd__status is-target">{{ preview.toStatusLabel }}</span>
                <span
                  v-if="preview.groupLabel"
                  class="rbd__group"
                  :class="{ 'is-sensitive': preview.needSensitive || preview.needSecondConfirm }"
                >
                  {{ preview.groupLabel }}
                </span>
              </div>

              <p
                v-if="preview.hint"
                class="rbd__hint"
              >
                {{ preview.hint }}
              </p>

              <!-- 将失效的下游数据 -->
              <div
                v-if="preview.invalidations.length > 0"
                class="rbd__invalid"
              >
                <p class="rbd__invalid-title">
                  将失效的下游数据（保留留档，可恢复）：
                </p>
                <div
                  v-for="inv in preview.invalidations"
                  :key="inv.type"
                  class="rbd__invalid-group"
                >
                  <span class="rbd__invalid-type">{{ inv.typeLabel }}（{{ inv.count }}）</span>
                  <span class="rbd__invalid-items">
                    {{ inv.items.slice(0, 12).map((it) => it.label).join('、') }}
                    <template v-if="inv.count > inv.items.length">… 等 {{ inv.count }} 项</template>
                  </span>
                </div>
              </div>
              <p
                v-else
                class="rbd__hint"
              >
                本次回退不失效任何下游数据。
              </p>

              <!-- 不可逆提示 -->
              <p
                v-if="preview.irreversible"
                class="rbd__danger"
              >
                该回退不可逆，请谨慎操作。
              </p>

              <!-- 敏感权限不足 -->
              <p
                v-if="preview.needSensitive && !sensitiveAllowed"
                class="rbd__danger"
              >
                敏感回退需要「业务管理员 / 系统管理员」权限，当前账号无此权限，无法提交。
              </p>
            </template>

            <!-- 被拒边：allowed=false，携带业务码与说明（不抛 HTTP 错误） -->
            <p
              v-else
              class="rbd__blocked"
            >
              该回退路径被系统拒绝：{{ preview.msg || '（无说明，请刷新后重试）' }}
            </p>
          </template>
          <p
            v-else-if="!previewLoading"
            class="rbd__blocked"
          >
            未能获取回退预览，请刷新后重试。
          </p>
        </div>

        <!-- 原因（必填） -->
        <div class="rbd__section">
          <span class="rbd__label">
            回退原因<em class="rbd__required">必填</em>
          </span>
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明为何回退（将写入审计流水，不可为空）"
          />
        </div>

        <!-- 二次确认 -->
        <div
          v-if="needSecond"
          class="rbd__section"
        >
          <el-checkbox v-model="secondConfirmed">
            我已确认，执行敏感回退（需二次确认）
          </el-checkbox>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        取消
      </el-button>
      <el-button
        type="danger"
        :disabled="!canSubmit"
        :loading="submitting"
        @click="submit"
      >
        确认回退
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rbd {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
  min-height: 80px;
}

.rbd__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rbd__label {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
}

.rbd__required {
  margin-left: 6px;
  color: var(--lims-danger);
  font-style: normal;
}

.rbd__targets {
  display: flex;
  gap: var(--lims-sp-4);
}

.rbd__flow {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rbd__status {
  padding: 2px 10px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  color: var(--lims-ink-2);
  font-size: 13px;
}

.rbd__status.is-target {
  border-color: var(--lims-danger-line);
  background: var(--lims-danger-soft);
  color: var(--lims-danger);
}

.rbd__arrow {
  color: var(--lims-faint);
}

.rbd__group {
  padding: 1px 8px;
  border: 1px solid var(--lims-hair-2);
  border-radius: var(--lims-r-pill);
  color: var(--lims-muted);
  font-size: 11px;
}

.rbd__group.is-sensitive {
  border-color: var(--lims-warning-line);
  background: var(--lims-warning-soft);
  color: var(--lims-warning);
}

.rbd__hint {
  margin: 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.rbd__invalid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--lims-hair);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card-hover);
}

.rbd__invalid-title {
  margin: 0;
  color: var(--lims-ink);
  font-size: var(--lims-fs-xs);
  font-weight: 600;
}

.rbd__invalid-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rbd__invalid-type {
  color: var(--lims-warning);
  font-size: var(--lims-fs-xs);
}

.rbd__invalid-items {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.rbd__danger {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid var(--lims-danger-line);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-danger-soft);
  color: var(--lims-danger);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}

.rbd__blocked {
  margin: 0;
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-sm);
  line-height: 1.6;
}

.rbd__alt {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--lims-warning-line);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-warning-soft);
}

.rbd__alt-item {
  margin: 0;
  color: var(--lims-warning);
  font-size: var(--lims-fs-xs);
  line-height: 1.5;
}
</style>
