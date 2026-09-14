<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome(): void {
  void router.push('/')
}
</script>

<template>
  <el-result
    icon="warning"
    title="无访问权限"
    sub-title="当前账号没有该功能的访问权限。请从左侧菜单进入你负责的业务功能；如需开通，请联系管理员在「角色管理」中分配对应权限。"
  >
    <template #extra>
      <el-button
        type="primary"
        @click="goHome"
      >
        返回工作台
      </el-button>
    </template>
  </el-result>
</template>
