<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
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
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">LIMS</h1>
        <p class="login-subtitle">食品质量检验测试中心 · 实验室信息管理系统</p>
      </div>
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
        <el-form-item>
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
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #1f3b73 0%, #2f5cad 60%, #3d7bd6 100%);
}

.login-card {
  width: 400px;
  padding: 40px 36px 24px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 12px 32px rgb(0 0 0 / 18%);
}

.login-header {
  margin-bottom: 28px;
  text-align: center;
}

.login-title {
  margin: 0;
  font-size: 32px;
  letter-spacing: 4px;
  color: #1f3b73;
}

.login-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
}

.login-button {
  width: 100%;
}
</style>
