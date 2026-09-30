<script setup lang="ts">
/**
 * ReportVoidButton — 「作废 / 召回」入口（S80 已签发 / S90 已出报告专用治理动作）。
 *
 * <p>为什么它在这一轮被补上：S80/S90 **物理上没有回退边**（`ROLLBACK` 白名单不含这两个状态
 * 的出发边），因此「回退入口不出现在报告页」的前提是「作废/召回必须出现在报告页」。
 * 改造前该后端接口与前端 API 封装都已存在、但**没有任何页面调用它**——即能力存在而入口缺失。
 * 本轮把入口补在「报告生成」页（S80/S90 的自然归属页面），使两条路径都可达。</p>
 *
 * <p>语义（与后端一致，不得混淆）：
 * <ul>
 *   <li>作废 / 召回是**治理标记**，`status` 保持不变（不产生状态流转）；</li>
 *   <li>需 `report:void` 权限 + **二次确认** + 原因必填；</li>
 *   <li>已作废/已召回的报告不可重复标记（后端拒绝）。</li>
 * </ul>
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { voidReportApi } from '@/api/rollback'
import { VOID_TYPE } from '@/types/rollback'
import { confirm } from '@/utils/confirm'

const props = withDefaults(
  defineProps<{
    sampleNo: string
    /** 当前状态 code（80/90），仅用于文案 */
    status?: number
    link?: boolean
    size?: 'small' | 'default' | 'large'
    disabled?: boolean
  }>(),
  { status: undefined, link: true, size: 'small', disabled: false },
)

const emit = defineEmits<{
  (e: 'done'): void
}>()

const open = ref(false)
const submitting = ref(false)
const voidType = ref<number>(VOID_TYPE.VOID)
const reason = ref('')
const secondConfirmed = ref(false)

const statusLabel = computed(() => (props.status === 90 ? '已出报告' : '已签发'))

const canSubmit = computed(() => reason.value.trim().length > 0 && secondConfirmed.value)

function openDialog(): void {
  voidType.value = VOID_TYPE.VOID
  reason.value = ''
  secondConfirmed.value = false
  open.value = true
}

async function submit(): Promise<void> {
  if (!reason.value.trim()) {
    ElMessage.warning('请填写作废 / 召回原因')
    return
  }
  if (!secondConfirmed.value) {
    ElMessage.warning('作废 / 召回需二次确认')
    return
  }
  const action = voidType.value === VOID_TYPE.VOID ? '作废' : '召回'
  const ok = await confirm({
    title: `确认${action}报告`,
    message:
      `报告 ${props.sampleNo}（${statusLabel.value}）将被标记为「${action}」。`
      + `该动作不改变样品状态，仅作治理标记并留痕，供后续更正与追溯。`,
    tone: 'danger',
    confirmText: `确认${action}`,
  })
  if (!ok) return
  submitting.value = true
  try {
    const res = await voidReportApi({
      sampleNo: props.sampleNo,
      voidType: voidType.value,
      reason: reason.value.trim(),
      secondConfirmed: true,
    })
    ElMessage.success(`已${res.voidTypeLabel ?? action}：${res.sampleNo}`)
    open.value = false
    emit('done')
  } catch {
    // 请求层已统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <span class="rvb">
    <el-button
      v-permission="'report:void'"
      type="warning"
      :link="link"
      :size="size"
      :disabled="disabled"
      @click="openDialog"
    >
      作废/召回
    </el-button>

    <el-dialog
      v-model="open"
      title="报告作废 / 召回"
      width="520px"
      align-center
      :close-on-click-modal="false"
    >
      <div class="rvb__body">
        <div class="rvb__section">
          <span class="rvb__label">报告</span>
          <p class="rvb__value">
            {{ sampleNo }}
            <span class="rvb__status">{{ statusLabel }}</span>
          </p>
        </div>

        <div class="rvb__section">
          <span class="rvb__label">处置方式</span>
          <el-radio-group v-model="voidType">
            <el-radio :value="VOID_TYPE.VOID">
              作废（报告不对外生效）
            </el-radio>
            <el-radio :value="VOID_TYPE.RECALL">
              召回（报告已发出，需追回）
            </el-radio>
          </el-radio-group>
        </div>

        <div class="rvb__section">
          <span class="rvb__label">
            原因<em class="rvb__required">必填</em>
          </span>
          <el-input
            v-model="reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明作废 / 召回原因（将写入审计流水）"
          />
        </div>

        <el-checkbox v-model="secondConfirmed">
          我已确认，执行报告{{ voidType === VOID_TYPE.VOID ? '作废' : '召回' }}（需二次确认）
        </el-checkbox>

        <p class="rvb__hint">
          说明：本动作<strong>不修改样品状态</strong>，也不会删除任何已有数据；它只写一条治理标记
          与一条「作废/召回」状态流水，供后续追溯。若需按业务重新走签发流程，请另行评估（当前不在支持范围内）。
        </p>
      </div>

      <template #footer>
        <el-button @click="open = false">
          取消
        </el-button>
        <el-button
          type="warning"
          :disabled="!canSubmit"
          :loading="submitting"
          @click="submit"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </span>
</template>

<style scoped>
.rvb {
  display: inline-flex;
}

.rvb__body {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-3);
}

.rvb__section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rvb__label {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
}

.rvb__required {
  margin-left: 6px;
  color: var(--lims-danger);
  font-style: normal;
}

.rvb__value {
  margin: 0;
  color: var(--lims-ink);
  font-size: var(--lims-fs-sm);
}

.rvb__status {
  margin-left: 8px;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
}

.rvb__hint {
  margin: 0;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  line-height: 1.6;
}
</style>
