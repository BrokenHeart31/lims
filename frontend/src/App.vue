<script setup lang="ts">
// 根组件：承载 SVG 折射玻璃滤镜定义 + 路由出口（带过渡）
import GlassFilter from '@/components/GlassFilter.vue'
</script>

<template>
  <!-- 折射玻璃滤镜（全局定义一次；仅对 .lims-glass-refract 元素生效） -->
  <GlassFilter />

  <router-view v-slot="{ Component }">
    <transition
      name="lims-fade"
      mode="out-in"
    >
      <component :is="Component" />
    </transition>
  </router-view>
</template>

<style>
/*
 * 全局基础样式已迁移至 src/styles/（tokens / base / element-override）。
 * 此处仅保留根容器的最小约束，避免样式分散两处。
 */
html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
}
</style>
