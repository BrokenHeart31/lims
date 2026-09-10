import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission 按钮级权限指令（T-102，AGENTS 5/8.2）
 *
 * 用法：
 *   <el-button v-permission="'task:add'">新建</el-button>
 *   <el-button v-permission="['task:edit', 'task:add']">任一命中即显示</el-button>
 *
 * 说明：前端权限只做显隐（体验层），接口安全由后端 @PreAuthorize 兜底（AGENTS 8.4）。
 * 权限数据由路由守卫保证在页面渲染前加载完成（见 router/index.ts）。
 */
export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const required = binding.value
    if (!required || (Array.isArray(required) && required.length === 0)) {
      return
    }
    const authStore = useAuthStore()
    const need = Array.isArray(required) ? required : [required]
    const permitted = need.some((code) => authStore.hasPermission(code))
    if (!permitted) {
      el.parentNode?.removeChild(el)
    }
  },
}
