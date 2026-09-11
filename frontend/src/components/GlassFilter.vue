<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 折射玻璃滤镜（SVG 定义 + 渐进增强探测）
 * =============================================================================
 * 为什么需要它：
 *   普通 backdrop-filter: blur() 是「廉价毛玻璃」的根源 —— 只糊不扭曲。
 *   真正的玻璃感来自**位移折射 + RGB 色差**（feTurbulence → feDisplacementMap
 *   → 三通道分离 → screen 混合），这也是 Mineradio 视觉语言的核心。
 *
 * ⚠️ 性能红线：
 *   backdrop-filter 引用 SVG 滤镜开销远高于 blur()。因此本滤镜**仅对声明了
 *   `.lims-glass-refract` 的元素生效**（登录卡、侧栏品牌区、抽屉头部等
 *   「第一眼看到的表面」），绝不铺满全站。数据表格区一律用 .lims-panel。
 *
 * ⚠️ 渐进增强：
 *   探测通过才在 <html> 加 `.lims-glass-svg-ok`；否则保持 .lims-glass 的 blur 表现。
 *   —— 参数刻意保守（scale 12 / 色差 ±1.2px），确保在任何分辨率下都不会
 *   扭曲到影响可读性（Mineradio 用 scale 160–180 是因为它是全屏视觉舞台）。
 *
 * 调研依据：docs/knowledge/2026-09-11-ui-design-mineradio-research.md 第 3.3 节
 */

const SVG_OK_CLASS = 'lims-glass-svg-ok'

/** 探测 backdrop-filter 是否能引用 SVG 滤镜（Chrome/Edge 支持，Firefox 不支持） */
function detectSvgBackdropFilter(): boolean {
  if (typeof CSS === 'undefined' || typeof CSS.supports !== 'function') return false
  const chromium = /Chrome|Chromium|Edg\//.test(navigator.userAgent)
  const syntaxOk =
    CSS.supports('backdrop-filter', 'url(#lims-glass-refract-filter)') ||
    CSS.supports('-webkit-backdrop-filter', 'url(#lims-glass-refract-filter)')
  return chromium && syntaxOk
}

onMounted(() => {
  if (detectSvgBackdropFilter()) {
    document.documentElement.classList.add(SVG_OK_CLASS)
  }
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove(SVG_OK_CLASS)
})
</script>

<template>
  <!--
    滤镜定义容器：尺寸为 0，不参与布局，仅提供 <defs>。
    aria-hidden + focusable=false 避免被辅助技术/键盘焦点捕获。
  -->
  <svg
    class="lims-glass-filter-defs"
    aria-hidden="true"
    focusable="false"
  >
    <defs>
      <filter
        id="lims-glass-refract-filter"
        x="-12%"
        y="-28%"
        width="124%"
        height="156%"
        color-interpolation-filters="sRGB"
      >
        <!-- 噪声：低频 + 双八度 → 大尺度缓波，不是细碎颗粒 -->
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.007 0.011"
          numOctaves="2"
          seed="7"
          result="noise"
        />

        <!-- 三通道分别位移（尺度递减）→ 边缘产生色散 -->
        <feDisplacementMap
          in="SourceGraphic"
          in2="noise"
          scale="12"
          xChannelSelector="R"
          yChannelSelector="G"
          result="dispR"
        />
        <feDisplacementMap
          in="SourceGraphic"
          in2="noise"
          scale="10"
          xChannelSelector="R"
          yChannelSelector="G"
          result="dispG"
        />
        <feDisplacementMap
          in="SourceGraphic"
          in2="noise"
          scale="8"
          xChannelSelector="R"
          yChannelSelector="G"
          result="dispB"
        />

        <!-- 通道分离（±1.2px）：过大会变成「重影」，这是可读性红线 -->
        <feOffset
          in="dispR"
          dx="1.2"
          dy="0"
          result="offR"
        />
        <feOffset
          in="dispG"
          dx="0"
          dy="0"
          result="offG"
        />
        <feOffset
          in="dispB"
          dx="-1.2"
          dy="0"
          result="offB"
        />

        <!-- 抽取单通道 -->
        <feColorMatrix
          in="offR"
          type="matrix"
          values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0"
          result="chanR"
        />
        <feColorMatrix
          in="offG"
          type="matrix"
          values="0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0"
          result="chanG"
        />
        <feColorMatrix
          in="offB"
          type="matrix"
          values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0"
          result="chanB"
        />

        <!-- screen 混合还原亮度，最后极小高斯收边（不放大，避免糊） -->
        <feBlend
          in="chanR"
          in2="chanG"
          mode="screen"
          result="blendRG"
        />
        <feBlend
          in="blendRG"
          in2="chanB"
          mode="screen"
          result="blendRGB"
        />
        <feGaussianBlur
          in="blendRGB"
          stdDeviation="0.5"
        />
      </filter>
    </defs>
  </svg>
</template>

<style scoped>
.lims-glass-filter-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}
</style>
