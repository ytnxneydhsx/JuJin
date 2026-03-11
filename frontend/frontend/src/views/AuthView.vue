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
      authStore.setLogin({
        userId: result.userId,
        account: result.account,
      })
    } else {
      const result = await register({
        account: registerForm.account.trim(),
        password: registerForm.password,
        name: registerForm.name.trim(),
      })
      authStore.setLogin({
        userId: result.userId,
        account: result.account,
      })
    }

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Submit failed'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-panel">
    <header class="auth-header">
      <h1>Welcome to JUJIN</h1>
      <p>Use one account to write, search, and interact with articles.</p>
      <div class="auth-mode">
        <button
          class="mode-btn"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          Login
        </button>
        <button
          class="mode-btn"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          Register
        </button>
      </div>
    </header>

    <form class="auth-form" @submit.prevent="submit">
      <label class="field">
        <span>Account</span>
        <input
          v-if="mode === 'login'"
          v-model.trim="loginForm.account"
          required
          minlength="3"
          maxlength="64"
          autocomplete="username"
        />
        <input
          v-else
          v-model.trim="registerForm.account"
          required
          minlength="3"
          maxlength="64"
          autocomplete="username"
        />
      </label>

      <label v-if="mode === 'register'" class="field">
        <span>Display Name</span>
        <input
          v-model.trim="registerForm.name"
          maxlength="64"
          placeholder="Optional"
        />
      </label>

      <label class="field">
        <span>Password</span>
        <input
          v-if="mode === 'login'"
          v-model="loginForm.password"
          type="password"
          required
          minlength="6"
          maxlength="64"
          autocomplete="current-password"
        />
        <input
          v-else
          v-model="registerForm.password"
          type="password"
          required
          minlength="6"
          maxlength="64"
          autocomplete="new-password"
        />
      </label>

      <p v-if="errorText" class="error-text">{{ errorText }}</p>
      <button class="submit-btn" :disabled="submitting" type="submit">
        {{ submitting ? 'Submitting...' : mode === 'login' ? 'Login' : 'Create Account' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.auth-panel {
  max-width: 520px;
  margin: 28px auto;
  padding: 30px;
  border-radius: 18px;
  background: #fff;
  border: 1px solid var(--line-soft);
  box-shadow: var(--shadow-soft);
}

.auth-header h1 {
  margin: 0 0 8px;
  color: var(--ink-strong);
}

.auth-header p {
  margin: 0;
  color: var(--ink-muted);
}

.auth-mode {
  margin-top: 16px;
  display: inline-flex;
  padding: 4px;
  border: 1px solid var(--line-soft);
  border-radius: 999px;
  background: var(--bg-panel-muted);
}

.mode-btn {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  background: transparent;
  color: var(--ink-main);
}

.mode-btn.active {
  background: #fff;
  color: var(--brand-strong);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.auth-form {
  margin-top: 18px;
  display: grid;
  gap: 14px;
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  font-weight: 600;
  color: var(--ink-main);
}

.field input {
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fff;
  color: var(--ink-strong);
}

.field input:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(20, 88, 166, 0.16);
}

.submit-btn {
  border: none;
  border-radius: 10px;
  padding: 11px 14px;
  background: linear-gradient(140deg, #1458a6, #1d77d2);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.submit-btn:disabled {
  opacity: 0.65;
  cursor: default;
}

.error-text {
  margin: 0;
  color: var(--danger);
}
</style>
