<script setup lang="ts">
/**
 * RollbackDialog — 环节内嵌回退确认框（PRD B-05 / 设计 §5.5，2026-09-30 改造）。
 *
 * 改造要点：
 *   1. **可选目标步**：不再只有「上一级」。目标步清单由后端
 *      `GET /rollback/targets/{sampleId}` 一次返回（沿 ROLLBACK 白名单逐级可达的全部落点），
 *      前端**不自行枚举状态**——否则状态机一改，前端就会给出后端不接受的选项。
 *   2. **跨级链式**：选到跨级目标时，界面显式展示「检验中 → 已安排 → 已登记」这条链路与级数，
 *      让用户知道系统会逐级执行（而不是「跳级」），避免对留痕形态产生误解。
 *   3. **批量**：`samples` 可为多条样品；批量要求**同状态**（不同状态无法共用一个目标步），
 *      状态不一致时前端直接阻断并说明原因（不猜、不静默丢）。
 *   4. **影响预览来自后端**：`invalidations` 由 B7 接口给出（整链口径），前端只渲染不推算。
 *   5. 敏感链路（含 S70→S60 任一级）需 `rollback:sensitive` + 二次确认；
 *      前端 `v-permission` 只做显隐，**后端 `@PreAuthorize` 与服务层二次鉴权不变**。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { executeRollbackApi, getRollbackTargetsApi } from '@/api/rollback'
import type {
  RollbackBatchResultVO,
  RollbackSampleRef,
  RollbackTarget,
  RollbackTargetsVO,
} from '@/types/rollback'
import { sampleStatusInfo } from '@/utils/sampleStatus'
import StatusBadge from '@/components/common/StatusBadge.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 批量入口：多条样品（单条回退传长度为 1 的数组） */
    samples?: RollbackSampleRef[]
    /** 单条入口的兼容写法（等价于 samples=[{id,sampleNo}]） */
    sampleId?: number
    sampleNo?: string
  }>(),
  { samples: undefined, sampleId: undefined, sampleNo: '' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'done', result: RollbackBatchResultVO): void
}>()

const authStore = useAuthStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

/** 归一化后的待回退样品（单条写法 → 长度 1 的批量） */
const items = computed<RollbackSampleRef[]>(() => {
  if (props.samples && props.samples.length > 0) return props.samples
  if (props.sampleId != null) return [{ id: props.sampleId, sampleNo: props.sampleNo }]
  return []
})

/** 批量入口时参与的样品 id */
const ids = computed(() => items.value.map((s) => s.id))

/** 所选样品是否同状态（不同状态无法共用一个目标步） */
const statuses = computed(() => Array.from(new Set(items.value.map((s) => s.status ?? -1))))
const sameStatus = computed(() => statuses.value.length <= 1)

const firstStatusLabel = computed(() => {
  const s = items.value[0]?.status
  return s == null ? '—' : sampleStatusInfo(s).label
})

const loading = ref(false)
const submitting = ref(false)
const targets = ref<RollbackTargetsVO | null>(null)
const targetStatus = ref<number | undefined>(undefined)
const reason = ref('')
const secondConfirmed = ref(false)
/** 批量结果（仅在出现失败项时展示明细，成功项不打扰用户） */
const batchResult = ref<RollbackBatchResultVO | null>(null)

/** 当前选中的目标步 */
const current = computed<RollbackTarget | undefined>(() =>
  targets.value?.targets.find((t) => t.status === targetStatus.value),
)

/** 是否具备敏感回退权限 */
const sensitiveAllowed = computed(() => authStore.hasPermission('rollback:sensitive'))

/** 是否必须二次确认（敏感链路） */
const needSecond = computed(() => !!current.value && current.value.needSecondConfirm)

/** 可提交：同状态 + 有目标步 + 原因非空 + 敏感项已确认且有权 */
const canSubmit = computed(() => {
  const t = current.value
  if (!t || !sameStatus.value) return false
  if (!reason.value.trim()) return false
  if (t.needSensitive && !sensitiveAllowed.value) return false
  if (t.needSecondConfirm && !secondConfirmed.value) return false
  return true
})

/** 批量时的下游失效总量估算（单条影响 × 条数） */
const totalInvalidated = computed(() => {
  const t = current.value
  if (!t) return 0
  return t.invalidatedTotal * items.value.length
})

async function openLoad(): Promise<void> {
  reason.value = ''
  secondConfirmed.value = false
  batchResult.value = null
  targets.value = null
  targetStatus.value = undefined

  if (items.value.length === 0) return
  // 状态不一致：不请求，直接提示（见模板「状态不一致」分支）
  if (!sameStatus.value) return

  loading.value = true
  try {
    const res = await getRollbackTargetsApi(items.value[0].id)
    targets.value = res
    // 默认落在最近的一步（最常见的「退一级改一改」场景）
    targetStatus.value = res.targets.length > 0 ? res.targets[0].status : undefined
  } catch {
    targets.value = null
  } finally {
    loading.value = false
  }
}

watch(visible, (open) => {
  if (open) void openLoad()
})

watch(targetStatus, () => {
  secondConfirmed.value = false
})

async function submit(): Promise<void> {
  const t = current.value
  if (!t) return
  if (!sameStatus.value) {
    ElMessage.warning('所选样品状态不一致，请只勾选同一环节的样品')
    return
  }
  if (!reason.value.trim()) {
    ElMessage.warning('请填写回退原因（将写入审计流水，不可为空）')
    return
  }
  if (t.needSensitive && !sensitiveAllowed.value) {
    ElMessage.warning('敏感回退需要「业务管理员 / 系统管理员」权限')
    return
  }
  if (t.needSecondConfirm && !secondConfirmed.value) {
    ElMessage.warning('该回退含敏感环节（撤销审核），请勾选二次确认')
    return
  }
  submitting.value = true
  try {
    const res = await executeRollbackApi({
      ids: ids.value,
      targetStatus: t.status,
      reason: reason.value.trim(),
      secondConfirmed: secondConfirmed.value,
    })
    if (res.failCount === 0) {
      ElMessage.success(
        items.value.length > 1
          ? `回退完成：${res.successCount} 条全部成功（目标步「${res.targetStatusLabel ?? ''}」）`
          : `回退成功：${res.items[0]?.result?.statusLabel ?? ''}`,
      )
      visible.value = false
    } else {
      // 部分/全部失败：不关闭弹窗，把逐条原因摆在用户面前（不得静默跳过）
      batchResult.value = res
      ElMessage.warning(`回退部分失败：成功 ${res.successCount} 条 / 失败 ${res.failCount} 条`)
    }
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
    width="640px"
    align-center
    :close-on-click-modal="false"
  >
    <div
      v-loading="loading"
      class="rbd"
    >
      <!-- ① 批量但状态不一致：直接阻断并说明（不同状态无法共用一个目标步） -->
      <p
        v-if="items.length > 0 && !sameStatus"
        class="rbd__danger"
      >
        所选 {{ items.length }} 条样品状态不一致（{{ statuses.map((s) => sampleStatusInfo(s).label).join(' / ') }}）。
        回退必须针对<strong>同一环节</strong>的样品，请只勾选状态相同的记录后重试。
      </p>

      <template v-else>
        <!-- ② 选中样品 + 当前状态 -->
        <div class="rbd__section">
          <span class="rbd__label">待回退样品（{{ items.length }} 条）</span>
          <div class="rbd__flow">
            <span class="rbd__status">{{ items[0]?.sampleNo || `#${items[0]?.id}` }}</span>
            <span
              v-if="items.length > 1"
              class="rbd__more"
            >等 {{ items.length }} 条</span>
            <StatusBadge
              v-if="items[0]?.status != null"
              :tone="sampleStatusInfo(items[0].status).tone"
            >
              {{ firstStatusLabel }}
            </StatusBadge>
          </div>
          <ul
            v-if="items.length > 1"
            class="rbd__list"
          >
            <li
              v-for="s in items.slice(0, 20)"
              :key="s.id"
            >
              {{ s.sampleNo || `#${s.id}` }}
            </li>
            <li v-if="items.length > 20">
              … 等共 {{ items.length }} 条
            </li>
          </ul>
        </div>

        <!-- ③ 无可用回退路径（S80/S90 → 只保留作废/召回） -->
        <template v-if="targets && !targets.rollbackAvailable">
          <p class="rbd__blocked">
            样品当前状态为「{{ targets.currentStatusLabel }}」，<strong>无可用回退路径</strong>。
          </p>
          <div
            v-if="targets.rejected.length > 0"
            class="rbd__alt"
          >
            <p
              v-for="(rej, i) in targets.rejected"
              :key="i"
              class="rbd__alt-item"
            >
              {{ rej.msg }} —— 替代动作：报告<strong>作废 / 召回</strong>（在「报告生成」页操作，需 report:void 权限）。
            </p>
          </div>
        </template>

        <!-- ④ 目标步选择 -->
        <template v-else-if="targets">
          <div class="rbd__section">
            <span class="rbd__label">回退目标步（可选任意可达步；跨级由系统逐级执行）</span>
            <el-radio-group
              v-model="targetStatus"
              class="rbd__targets"
            >
              <el-radio
                v-for="t in targets.targets"
                :key="t.status"
                :value="t.status"
              >
                {{ t.statusLabel }}
                <span
                  v-if="t.stepCount > 1"
                  class="rbd__steps"
                >（{{ t.stepCount }} 级）</span>
              </el-radio>
            </el-radio-group>
          </div>

          <!-- ⑤ 链路 + 影响预览（数据来自后端 B7，前端不推算） -->
          <div
            v-if="current"
            class="rbd__section"
          >
            <div class="rbd__flow">
              <span class="rbd__status">{{ current.chainText }}</span>
              <span
                v-if="current.groupLabel"
                class="rbd__group"
                :class="{ 'is-sensitive': current.needSensitive }"
              >
                {{ current.groupLabel }}
              </span>
            </div>

            <p
              v-if="current.hint"
              class="rbd__hint"
            >
              {{ current.hint }}
            </p>

            <div
              v-if="current.invalidations.length > 0"
              class="rbd__invalid"
            >
              <p class="rbd__invalid-title">
                回退到该步将失效的下游数据（保留留档，可撤销）：
              </p>
              <div
                v-for="inv in current.invalidations"
                :key="inv.type"
                class="rbd__invalid-group"
              >
                <span class="rbd__invalid-type">{{ inv.typeLabel }}（{{ inv.count }}）</span>
                <span class="rbd__invalid-items">
                  {{ inv.items.slice(0, 12).map((it) => it.label).join('、') }}
                  <template v-if="inv.count > inv.items.length">… 等 {{ inv.count }} 项</template>
                </span>
              </div>
              <p
                v-if="items.length > 1"
                class="rbd__hint"
              >
                以上为单个样品的影响；本次共 {{ items.length }} 条，合计约
                {{ totalInvalidated }} 条下游数据将失效（逐条独立执行、逐条留痕）。
              </p>
            </div>
            <p
              v-else
              class="rbd__hint"
            >
              本次回退不失效任何下游数据（仅回退状态）。
            </p>

            <p
              v-if="current.needSensitive && !sensitiveAllowed"
              class="rbd__danger"
            >
              该回退链路包含「撤销审核」（S70→S60），需要「业务管理员 / 系统管理员」权限，
              当前账号无此权限，无法提交。
            </p>
          </div>

          <!-- ⑥ 原因（必填） -->
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

          <!-- ⑦ 敏感链路二次确认 -->
          <div
            v-if="needSecond"
            class="rbd__section"
          >
            <el-checkbox v-model="secondConfirmed">
              我已确认，执行含敏感环节的回退（需二次确认）
            </el-checkbox>
          </div>
        </template>

        <p
          v-else-if="!loading"
          class="rbd__blocked"
        >
          未能获取可回退目标步，请刷新后重试。
        </p>

        <!-- ⑧ 批量逐条结果（仅失败项需要解释） -->
        <div
          v-if="batchResult && batchResult.failCount > 0"
          class="rbd__section"
        >
          <p class="rbd__invalid-title">
            逐条结果（成功 {{ batchResult.successCount }} / 失败 {{ batchResult.failCount }}）：
          </p>
          <ul class="rbd__list">
            <li
              v-for="it in batchResult.items"
              :key="it.sampleId"
              :class="it.success ? 'is-ok' : 'is-fail'"
            >
              {{ it.sampleNo || `#${it.sampleId}` }}：
              {{ it.success ? `成功（${it.result?.statusLabel ?? ''}）` : `失败 —— ${it.reason ?? '未知原因'}（${it.code ?? '-'}）` }}
            </li>
          </ul>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ batchResult && batchResult.failCount > 0 ? '关闭' : '取消' }}
      </el-button>
      <el-button
        v-if="!targets || targets.rollbackAvailable"
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
  flex-wrap: wrap;
  gap: var(--lims-sp-4);
}

.rbd__steps {
  color: var(--lims-warning);
  font-size: 11px;
}

.rbd__flow {
  display: flex;
  flex-wrap: wrap;
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

.rbd__more {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
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

.rbd__list {
  max-height: 160px;
  margin: 0;
  padding-left: 18px;
  overflow-y: auto;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.7;
}

.rbd__list .is-fail {
  color: var(--lims-danger);
}

.rbd__list .is-ok {
  color: var(--lims-success);
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
