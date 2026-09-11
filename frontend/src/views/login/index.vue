<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const loginForm = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户号（工号）', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入登录密码', trigger: 'blur' },
    { min: 4, message: '密码长度不能少于 4 位', trigger: 'blur' },
  ],
}

async function handleLogin(): Promise<void> {
  const form = formRef.value
  if (!form) return
  try {
    await form.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    await authStore.login({ username: loginForm.username, password: loginForm.password })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
    ElMessage.success('登录成功')
  } catch (error) {
    // 请求层已统一弹出错误提示，这里仅兜底防止未处理拒绝
    if (!(error instanceof Error)) {
      ElMessage.error('登录失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 装饰性极光光带：纯 CSS，无图片资源 -->
    <div
      class="aurora-band"
      aria-hidden="true"
    />
    <div
      class="aurora-band aurora-band--alt"
      aria-hidden="true"
    />

    <div class="login-card lims-glass lims-glass-refract">
      <!-- 顶部信号线：呼应「实验室仪器读数」的视觉隐喻 -->
      <span
        class="signal-line"
        aria-hidden="true"
      />

      <header class="login-header">
        <div class="brand-row">
          <span
            class="brand-mark"
            aria-hidden="true"
          />
          <h1 class="login-title lims-text-gradient">
            LIMS
          </h1>
        </div>
        <p class="login-subtitle">
          食品质量检验测试中心 · 实验室信息管理系统
        </p>
      </header>

      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户号（工号）"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="登录密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item class="submit-item">
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <footer class="login-foot">
        <span
          class="foot-dot"
          aria-hidden="true"
        />
        <span>CMA / CMA-CATL 资质实验室 · 检验数据全程留痕</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  overflow: hidden;
}

/* ---------------------------------------------------------------------------
 * 极光光带：两条大尺寸模糊渐变，缓慢呼吸。
 * 用 filter: blur() 而非图片，零资源且可随主题变化。
 * ------------------------------------------------------------------------- */
.aurora-band {
  position: absolute;
  width: 62vw;
  height: 62vw;
  border-radius: 50%;
  opacity: 0.5;
  background: radial-gradient(circle, rgba(var(--lims-accent-rgb), 0.32), transparent 62%);
  filter: blur(70px);
  animation: aurora-drift 14s var(--lims-ease-out) infinite alternate;
  pointer-events: none;
}

.aurora-band--alt {
  top: auto;
  bottom: -18vw;
  left: auto;
  right: -12vw;
  background: radial-gradient(circle, rgba(var(--lims-blue-rgb), 0.34), transparent 62%);
  animation-duration: 18s;
  animation-direction: alternate-reverse;
}

@keyframes aurora-drift {
  from {
    transform: translate3d(-6%, -4%, 0) scale(1);
  }

  to {
    transform: translate3d(8%, 6%, 0) scale(1.12);
  }
}

/* ---------------------------------------------------------------------------
 * 登录卡：全站唯一使用「最高规格」玻璃的界面（第一印象）
 * ------------------------------------------------------------------------- */
.login-card {
  position: relative;
  z-index: 1;
  width: 404px;
  padding: 40px 36px 22px;
  border-radius: var(--lims-r-2xl);
  box-shadow: var(--lims-glass-shadow-focus);
}

/* 顶部信号线：青→白→金的渐变细线 */
.signal-line {
  position: absolute;
  top: 0;
  left: 14%;
  width: 72%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(var(--lims-accent-rgb), 0.7),
    rgba(255, 255, 255, 0.85),
    rgba(244, 210, 138, 0.6),
    transparent
  );
  opacity: 0.9;
}

.login-header {
  margin-bottom: 26px;
  text-align: center;
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.brand-mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--lims-brand-gradient);
  box-shadow: 0 0 22px rgba(var(--lims-accent-rgb), 0.42);
}

.login-title {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 5px;
}

.login-subtitle {
  margin-top: 10px;
  color: var(--lims-muted);
  font-size: var(--lims-fs-xs);
  letter-spacing: 0.3px;
}

.submit-item {
  margin-bottom: 8px;
}

.login-button {
  width: 100%;
  height: 42px;
  font-size: var(--lims-fs-lg);
  font-weight: 600;
  letter-spacing: 3px;
  border-radius: var(--lims-r-sm);
}

/* ---------------------------------------------------------------------------
 * 底部说明
 * ------------------------------------------------------------------------- */
.login-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding-top: 14px;
  border-top: 1px solid var(--lims-hair);
  color: var(--lims-faint);
  font-size: 11px;
}

.foot-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--lims-success);
  box-shadow: 0 0 8px rgba(var(--lims-success-rgb), 0.8);
}
</style>
