<script setup lang="ts">
/**
 * AI 会话审计页（feature A，api-spec A4/A5，设计 §5.5）。
 *
 * 用途：审计助手会话——谁问了什么、答了什么、命中了哪些标准、是否拒答。
 * 与 `sys_operation_log` 双轨互补：后者记录「调了接口」，本页记录「问了什么 / 答了什么」。
 *
 * 权限：`ai:log:view`（seed sys_menu id=123）。本页无侧栏菜单，由标准库页 / 悬浮窗入口进入。
 */
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listAiMessagesApi, pageAiConversationsApi } from '@/api/ai'
import type { AiConversationVO, AiMessageVO } from '@/types/ai'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AiCitationCard from '@/components/ai/AiCitationCard.vue'

const conversations = ref<AiConversationVO[]>([])
const loading = ref(false)
const total = ref(0)
const query = ref({ current: 1, size: 20 })

async function loadConversations(): Promise<void> {
  loading.value = true
  try {
    const page = await pageAiConversationsApi(query.value.current, query.value.size)
    conversations.value = page.records
    total.value = page.total
  } catch {
    conversations.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePage(p: number): void {
  query.value.current = p
  void loadConversations()
}

// ---------------- 消息明细 ----------------
const drawerVisible = ref(false)
const messagesLoading = ref(false)
const messages = ref<AiMessageVO[]>([])
const activeConversation = ref<AiConversationVO | null>(null)

const drawerTitle = computed(() =>
  activeConversation.value
    ? `会话 #${activeConversation.value.id} — ${activeConversation.value.title ?? '（无标题）'}`
    : '会话明细',
)

async function openMessages(row: AiConversationVO): Promise<void> {
  activeConversation.value = row
  drawerVisible.value = true
  messagesLoading.value = true
  messages.value = []
  try {
    messages.value = await listAiMessagesApi(row.id)
  } catch {
    messages.value = []
  } finally {
    messagesLoading.value = false
  }
}

/** el-table 作用域插槽 row 为宽松类型（Element Plus DefaultRow）→ 统一收窄 */
function rowConversation(row: unknown): AiConversationVO {
  return row as AiConversationVO
}

function onRowDblclick(row: unknown): void {
  void openMessages(rowConversation(row))
}

function roleTone(role: number): 'info' | 'success' {
  return role === 2 ? 'success' : 'info'
}

function paragraphs(content: string): string[] {
  return content
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

onMounted(() => {
  void loadConversations()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="AI 会话审计"
      subtitle="查看助手会话与消息明细（含引用与拒答留痕），用于审计与质量回溯"
      icon="Monitor"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>AI 助手</el-breadcrumb-item>
          <el-breadcrumb-item>会话审计</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        :loading="loading"
        @click="loadConversations"
      >
        刷新
      </el-button>
    </PageHeader>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-table
        v-loading="loading"
        :data="conversations"
        stripe
        border
        @row-dblclick="onRowDblclick"
      >
        <el-table-column
          prop="id"
          label="会话ID"
          width="90"
          align="center"
        />
        <el-table-column
          prop="title"
          label="标题"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="userNo"
          label="用户"
          width="130"
        />
        <el-table-column
          prop="model"
          label="模型"
          width="170"
          show-overflow-tooltip
        />
        <el-table-column
          label="消息数"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{ row.messageCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column
          prop="createdAt"
          label="创建时间"
          width="180"
        />
        <el-table-column
          label="操作"
          width="100"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="openMessages(rowConversation(row))"
            >
              明细
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无会话记录"
            hint="使用 AI 助手提问后，此处可审计问答与引用"
          />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50]"
        layout="total, sizes, prev, pager, next"
        class="page__pager"
        @current-change="handlePage"
      />
    </AppCard>

    <!-- 消息明细 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="52%"
    >
      <div
        v-loading="messagesLoading"
        class="msg-drawer"
      >
        <AppEmpty
          v-if="!messagesLoading && messages.length === 0"
          title="该会话暂无消息"
        />
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-drawer__item"
        >
          <div class="msg-drawer__head">
            <StatusBadge
              :tone="roleTone(msg.role)"
              size="sm"
            >
              {{ msg.roleLabel ?? (msg.role === 2 ? '助手' : '用户') }}
            </StatusBadge>
            <span
              v-if="msg.refused === 1"
              class="msg-drawer__refused"
            >超出业务范围</span>
            <span class="msg-drawer__meta">{{ msg.createdAt ?? '' }}</span>
          </div>
          <p
            v-for="(para, i) in paragraphs(msg.content)"
            :key="i"
            class="msg-drawer__para"
          >
            {{ para }}
          </p>
          <div
            v-if="msg.citations.length > 0"
            class="msg-drawer__cites"
          >
            <AiCitationCard
              v-for="(cite, ci) in msg.citations"
              :key="ci"
              :citation="cite"
            />
          </div>
          <div class="msg-drawer__foot">
            <span v-if="msg.domain">领域：{{ msg.domain }}</span>
            <span v-if="msg.retrievedCount != null">命中：{{ msg.retrievedCount }}</span>
            <span v-if="msg.elapsedMs != null">耗时：{{ msg.elapsedMs }} ms</span>
            <span v-if="msg.model">模型：{{ msg.model }}</span>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
}

.page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-sp-3);
}

.msg-drawer {
  display: flex;
  flex-direction: column;
  gap: var(--lims-sp-4);
}

.msg-drawer__item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--lims-hair);
  border-radius: var(--lims-r-ctrl);
  background: var(--lims-layer-card);
}

.msg-drawer__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg-drawer__refused {
  color: var(--lims-muted);
  font-size: 11px;
}

.msg-drawer__meta {
  margin-left: auto;
  color: var(--lims-faint);
  font-size: 11px;
}

.msg-drawer__para {
  margin: 0;
  color: var(--lims-ink-2);
  font-size: var(--lims-fs-sm);
  line-height: 1.6;
}

.msg-drawer__cites {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg-drawer__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--lims-faint);
  font-size: 11px;
}
</style>
