import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'juejin_auth_state'

export interface AuthState {
  userId: number | null
  account: string
}

function readStoredState(): AuthState {
  const text = localStorage.getItem(STORAGE_KEY)
  if (!text) {
    return {
      userId: null,
      account: '',
    }
  }
  try {
    const data = JSON.parse(text) as Partial<AuthState>
    return {
      userId: typeof data.userId === 'number' ? data.userId : null,
      account: typeof data.account === 'string' ? data.account : '',
    }
  } catch {
    return {
      userId: null,
      account: '',
    }
  }
}

export const useAuthStore = defineStore('auth', () => {
  const state = ref<AuthState>(readStoredState())

  const isAuthenticated = computed(() => state.value.userId !== null)

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.value))
  }

  function setLogin(payload: { userId: number; account: string }) {
    state.value.userId = payload.userId
    state.value.account = payload.account
    persist()
  }

  function clearLogin() {
    state.value.userId = null
    state.value.account = ''
    persist()
  }

  return {
    state,
    isAuthenticated,
    setLogin,
    clearLogin,
  }
})
