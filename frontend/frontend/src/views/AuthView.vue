<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '@/api/user'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

type Mode = 'login' | 'register'

const mode = ref<Mode>('login')
const submitting = ref(false)
const errorText = ref('')

const loginForm = reactive({
  account: '',
  password: '',
})

const registerForm = reactive({
  account: '',
  password: '',
  name: '',
})

function switchMode(nextMode: Mode) {
  mode.value = nextMode
  errorText.value = ''
}

async function submit() {
  errorText.value = ''
  submitting.value = true
  try {
    if (mode.value === 'login') {
      const result = await login({
        account: loginForm.account.trim(),
        password: loginForm.password,
      })
      authStore.setLogin({ userId: result.userId, account: result.account })
    } else {
      const result = await register({
        account: registerForm.account.trim(),
        password: registerForm.password,
        name: registerForm.name.trim(),
      })
      authStore.setLogin({ userId: result.userId, account: result.account })
    }

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '提交失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="auth-banner">
      <p class="section-label">内容社区</p>
      <h1>登录后开始创作与互动</h1>
      <p>继续写文章、管理草稿、点赞收藏，所有能力都基于你当前已有的后端接口。</p>
    </div>

    <section class="auth-panel">
      <header class="auth-header">
        <h2>{{ mode === 'login' ? '账号登录' : '注册账号' }}</h2>
        <div class="auth-mode">
          <button class="mode-btn" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button class="mode-btn" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
        </div>
      </header>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span>账号</span>
          <input v-if="mode === 'login'" v-model.trim="loginForm.account" required minlength="3" maxlength="64" autocomplete="username" />
          <input v-else v-model.trim="registerForm.account" required minlength="3" maxlength="64" autocomplete="username" />
        </label>

        <label v-if="mode === 'register'" class="field">
          <span>昵称</span>
          <input v-model.trim="registerForm.name" maxlength="64" placeholder="可选" />
        </label>

        <label class="field">
          <span>密码</span>
          <input v-if="mode === 'login'" v-model="loginForm.password" type="password" required minlength="6" maxlength="64" autocomplete="current-password" />
          <input v-else v-model="registerForm.password" type="password" required minlength="6" maxlength="64" autocomplete="new-password" />
        </label>

        <p v-if="errorText" class="error-text">{{ errorText }}</p>
        <button class="submit-btn" :disabled="submitting" type="submit">
          {{ submitting ? '提交中...' : mode === 'login' ? '登录' : '创建账号' }}
        </button>
      </form>
    </section>
  </section>
</template>

<style scoped>
.auth-layout { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(360px, 440px); gap: 22px; align-items: stretch; }
.auth-banner, .auth-panel { border: 1px solid var(--line-soft); border-radius: 22px; background: #fff; }
.auth-banner { padding: 34px; background: radial-gradient(circle at top right, rgba(30, 128, 255, 0.12), transparent 28%), linear-gradient(135deg, #ffffff, #f7fbff); }
.section-label { margin: 0 0 12px; color: var(--brand); font-size: 0.85rem; font-weight: 700; }
.auth-banner h1 { margin: 0; color: var(--ink-strong); font-size: clamp(2rem, 1.5rem + 1.4vw, 3rem); line-height: 1.2; }
.auth-banner p:last-child { margin: 16px 0 0; color: var(--ink-main); line-height: 1.8; max-width: 38rem; }
.auth-panel { padding: 28px; }
.auth-header { display: flex; justify-content: space-between; align-items: center; gap: 14px; }
.auth-header h2 { margin: 0; color: var(--ink-strong); }
.auth-mode { display: inline-flex; padding: 4px; border-radius: 999px; background: #f4f7fb; }
.mode-btn { border: none; border-radius: 999px; padding: 8px 14px; cursor: pointer; background: transparent; color: var(--ink-main); }
.mode-btn.active { background: #fff; color: var(--brand); box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06); }
.auth-form { margin-top: 24px; display: grid; gap: 16px; }
.field { display: grid; gap: 8px; }
.field span { color: var(--ink-main); font-weight: 600; }
.field input { border: 1px solid var(--line-soft); border-radius: 12px; background: #f7f8fa; padding: 12px 14px; color: var(--ink-strong); }
.field input:focus { outline: none; border-color: rgba(30, 128, 255, 0.3); background: #fff; }
.submit-btn { border: none; border-radius: 14px; padding: 12px 16px; background: linear-gradient(135deg, #1e80ff, #5ea1ff); color: #fff; cursor: pointer; font-weight: 700; }
.submit-btn:disabled { opacity: 0.6; cursor: default; }
.error-text { margin: 0; color: var(--danger); }
@media (max-width: 980px) { .auth-layout { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .auth-header { flex-direction: column; align-items: flex-start; } }
</style>
