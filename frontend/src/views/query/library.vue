<script setup lang="ts">
/**
 * 项目库查询页（T-801 A3，GET /api/query/library/page + /{productLibId}/items）。
 *
 * <p>上半部分为产品（product_lib）分页检索；点行打开抽屉查看该产品的检测单项
 * （product_lib_item，含判定依据、检验方法、标准值、判定类型、参考性标记等）。只读。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  getLibraryItemsApi,
  pageLibraryQueryApi,
  type LibraryItemRow,
  type LibraryQueryParams,
  type LibraryQueryRow,
} from '@/api/query'
import PageHeader from '@/components/common/PageHeader.vue'
import AppCard from '@/components/common/AppCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import DataFilter from '@/components/common/DataFilter.vue'

const query = reactive({
  productName: '',
  category: '',
})

const loading = ref(false)
const tableData = ref<LibraryQueryRow[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)

// ---------------- 抽屉：检测单项 ----------------
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const currentProduct = ref<LibraryQueryRow | null>(null)
const libraryItems = ref<LibraryItemRow[]>([])

function rowItem(row: unknown): LibraryQueryRow {
  return row as LibraryQueryRow
}

function itemRow(row: unknown): LibraryItemRow {
  return row as LibraryItemRow
}

function buildParams(): LibraryQueryParams {
  const params: LibraryQueryParams = {
    current: current.value,
    size: size.value,
  }
  if (query.productName) params.productName = query.productName
  if (query.category) params.category = query.category
  return params
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await pageLibraryQueryApi(buildParams())
    tableData.value = res.records
    total.value = res.total
  } catch {
    // 请求层已统一提示
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  current.value = 1
  void load()
}

function handleReset(): void {
  query.productName = ''
  query.category = ''
  handleSearch()
}

function handlePageChange(p: number): void {
  current.value = p
  void load()
}

function handleSizeChange(s: number): void {
  size.value = s
  current.value = 1
  void load()
}

async function openItems(row: LibraryQueryRow): Promise<void> {
  currentProduct.value = row
  drawerVisible.value = true
  drawerLoading.value = true
  libraryItems.value = []
  try {
    libraryItems.value = await getLibraryItemsApi(row.id)
  } catch {
    // 请求层已统一提示
  } finally {
    drawerLoading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="项目库"
      subtitle="产品标准库检索：按产品查看应检项目与判定依据"
      icon="Search"
    >
      <template #breadcrumb>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">
            工作台
          </el-breadcrumb-item>
          <el-breadcrumb-item>查询统计</el-breadcrumb-item>
          <el-breadcrumb-item>项目库</el-breadcrumb-item>
        </el-breadcrumb>
      </template>
      <el-button
        :icon="Refresh"
        @click="load"
      >
        刷新
      </el-button>
    </PageHeader>

    <DataFilter>
      <el-form inline>
        <el-form-item label="产品名称">
          <el-input
            v-model="query.productName"
            placeholder="模糊查询"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="食品大类">
          <el-input
            v-model="query.category"
            placeholder="精确匹配"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
      </el-form>
      <template #actions>
        <el-button
          type="primary"
          :icon="Search"
          @click="handleSearch"
        >
          查询
        </el-button>
        <el-button
          :icon="Refresh"
          @click="handleReset"
        >
          重置
        </el-button>
      </template>
    </DataFilter>

    <AppCard
      variant="panel"
      :padding="16"
    >
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        @row-dblclick="openItems"
      >
        <el-table-column
          prop="productName"
          label="产品名称"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          prop="category"
          label="食品大类"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="itemCount"
          label="检测单项数"
          width="120"
          align="center"
        />
        <el-table-column
          label="操作"
          width="120"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              @click="openItems(rowItem(row))"
            >
              查看单项
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty
            title="暂无项目库数据"
            hint="当前筛选条件下没有匹配的产品"
          />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </AppCard>

    <el-drawer
      v-model="drawerVisible"
      :title="currentProduct ? `检测单项 — ${currentProduct.productName ?? ''}` : '检测单项'"
      direction="rtl"
      size="64%"
      :destroy-on-close="true"
    >
      <div
        v-loading="drawerLoading"
        class="drawer-body"
      >
        <el-table
          :data="libraryItems"
          stripe
          border
        >
          <el-table-column
            prop="itemOrder"
            label="项次"
            width="64"
            align="center"
          />
          <el-table-column
            label="检验项目"
            min-width="180"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span>{{ itemRow(row).itemName }}</span>
              <StatusBadge
                v-if="itemRow(row).isReference === 1"
                tone="warning"
                size="sm"
                class="ref-tag"
              >
                参考
              </StatusBadge>
            </template>
          </el-table-column>
          <el-table-column
            prop="basisCode"
            label="判定依据"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="methods"
            label="检验方法"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="stdValue"
            label="标准值"
            width="110"
            show-overflow-tooltip
          />
          <el-table-column
            prop="unit"
            label="单位"
            width="80"
            align="center"
          />
          <el-table-column
            prop="lowerLimit"
            label="最低检出限"
            width="110"
            align="center"
          />
          <el-table-column
            label="判定类型"
            width="150"
            align="center"
          >
            <template #default="{ row }">
              <span class="muted">{{ itemRow(row).judgeTypeLabel ?? '—' }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <AppEmpty
              title="该产品暂无检测单项"
              hint="请先在项目库维护该产品的应检项目"
            />
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--lims-r-md);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--lims-r-sm);
}
.drawer-body {
  padding: 0 var(--lims-r-xs);
}
.ref-tag {
  margin-left: 6px;
}
.muted {
  color: var(--lims-text-secondary);
}
</style>
