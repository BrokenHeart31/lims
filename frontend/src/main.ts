import { createApp, type Component } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

// 设计令牌与主题（顺序不可调换：令牌 → 基础 → 组件覆盖）
// 依据：docs/knowledge/2026-09-11-ui-design-mineradio-research.md
import './styles/tokens.css'
import './styles/base.css'
import './styles/element-override.css'

import App from './App.vue'
import router from './router'
import { vPermission } from './directives/permission'

// Element Plus 的暗色变量作用域为 html.dark；本系统为沉浸式暗色主题，启动即启用
document.documentElement.classList.add('dark')

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 按钮级权限指令（T-102）：v-permission="'resource:action'"
app.directive('permission', vPermission)

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as Component)
}

app.mount('#app')
