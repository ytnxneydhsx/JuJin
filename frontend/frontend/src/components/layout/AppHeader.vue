<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const navItems = [
  { path: '/', label: 'Home' },
  { path: '/search', label: 'Search' },
  { path: '/write', label: 'Write' },
]

const userLabel = computed(() => {
  if (!authStore.isAuthenticated) {
    return 'Login'
  }
  return authStore.state.account || `User ${authStore.state.userId}`
})

function goAuth() {
  if (authStore.isAuthenticated) {
    router.push('/me')
    return
  }
  router.push('/login')
}
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <button class="brand" @click="router.push('/')">JUJIN</button>
      <nav class="topnav">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>
      <button class="user-entry" @click="goAuth">{{ userLabel }}</button>
    </div>
  </header>
</template>
