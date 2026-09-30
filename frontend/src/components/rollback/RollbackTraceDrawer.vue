<script setup lang="ts">
/**
 * RollbackTraceDrawer — 样品流程留痕抽屉（feature B，2026-09-30 新增）。
 *
 * <p>为什么需要它：改造后「流程回溯」不再有独立页面（菜单/路由已移除），但**留痕查看与
 * 撤销回退**这两件事不能跟着消失——它们是 ALCOA+ 审计链的对外可见部分，也是用户
 * 「我看看到底改了什么」的入口。故把原独立页面的时间线 + 撤销回退能力收进一个抽屉，
 * 由业务页的「留痕」入口就地打开。</p>
 *
 * 权限：`rollback:view`（时间线读取）；撤销回退需 `rollback:execute`（按钮显隐 + 后端兜底）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getRollbackTimelineApi, recoverRollbackApi } from '@/api/rollback'
import type { RollbackTimelineVO } from '@/types/rollback'
import { confirm } from '@/utils/confirm'
import RollbackTimeline from '@/components/rollback/RollbackTimeline.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'

const props = defineProps<{
  modelValue: boolean
  sampleId: number
  sampleNo?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'done'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const loading = ref(false)
const timeline = ref<RollbackTimelineVO | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    timeline.value = await getRollbackTimelineApi(props.sampleId)
  } catch {
    timeline.value = null
  } finally {
    loading.value = false
  }
}

watch(visible, (open) => {
  if (open) void load()
})

/** 撤销某次回退（仅未被后续操作覆盖时可用；后端 4107 兜底） */
async function handleRecover(rollbackId: number): Promise<void> {
  const reason = await confirm({
    title: '撤销回退',
    message:
      '撤销将把该次回退失效的下游数据复原（仅当回退后未产生新的明细/结果时可撤销）。请填写撤销原因：',
    tone: 'warning',
    confirmText: '确认撤销',
    input: true,
    inputPlaceholder: '撤销原因（不少于 2 字，写入审计流水）',
    // 空值给出明确反馈而不是静默中止（confirm 在 input 模式下空值会返回 null）
    inputValidator: (v: string) => (v && v.trim().length >= 2) || '请填写撤销原因（不少于 2 字）',
  })
  if (reason === null) return
  try {
    await recoverRollbackApi({ rollbackId, reason })
    ElMessage.success('已撤销该次回退')
    await load()
    emit('done')
  } catch {
    // 请求层已统一提示（如 4107 不可撤销）
  }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="流程留痕"
    size="720px"
    :destroy-on-close="true"
  >
    <div
      v-loading="loading"
      class="rtd"
    >
      <div class="rtd__head">
        <span class="rtd__no">{{ sampleNo || timeline?.sampleNo || `#${sampleId}` }}</span>
        <span
          v-if="timeline"
          class="rtd__sub"
        >
          当前状态：{{ timeline.currentStatusLabel }}
        </span>
      </div>

      <RollbackTimeline
        v-if="timeline"
        :timeline="timeline"
        @recover="handleRecover"
      />
      <AppEmpty
        v-else-if="!loading"
        title="未能读取流程留痕"
        hint="请刷新后重试；若持续失败请确认当前账号具备 rollback:view 权限"
      />
    </div>
  </el-drawer>
</template>

<style scoped>
.rtd {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-3);
  min-height: 120px;
}

.rtd__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.rtd__no {
  color: var(--lims-ink);
  font-size: var(--lims-fs-md);
  font-weight: 600;
}

.rtd__sub {
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
}
</style>
