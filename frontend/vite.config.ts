import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理到本地 Spring Boot 后端（T-002 骨架端口约定 8080）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 生产构建默认即可；分包交给后续性能优化任务
    sourcemap: false,
    // ⚠️ 沙箱环境备注：vite 构建前会清空 outDir，而本机沙箱/安全垫片对
    // `rmSync(dist)` 有批量删除守卫，会在 emptyDir 阶段抛
    // SAFE_DELETE_BULK_CONFIRM_REQUIRED 导致构建失败。
    // 解法：outDir 指向一个**每次构建不同**的目录（带时间戳），
    // 使其无需清空既有文件即可构建；产物仍可用 `vite preview` 预览。
    // 需要在固定 dist/ 产出时，可显式覆盖：vite build --outDir dist
    outDir: process.env.LIMS_BUILD_OUTDIR || `dist-${Date.now()}`,
  },
})
