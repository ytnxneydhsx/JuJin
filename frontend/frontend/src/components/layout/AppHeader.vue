<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/user'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const navItems = [
  { path: '/', label: 'Home' },
  { path: '/search', label: 'Search' },
  { path: '/write', label: 'Write' },
]

const userLabel = computed(() =>
  authStore.state.account || `User ${authStore.state.userId}`,
)

function goAuth() {
  if (authStore.isAuthenticated) {
    router.push('/me')
    return
  }
  router.push('/login')
}

async function handleLogout() {
  try {
    await logout()
  } finally {
    authStore.clearLogin()
    router.push('/')
  }
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
      <div class="user-area">
        <button class="user-entry" @click="goAuth">
          {{ authStore.isAuthenticated ? userLabel : 'Login' }}
        </button>
        <div v-if="authStore.isAuthenticated" class="user-menu">
          <button class="menu-item" @click="router.push('/me')">My Profile</button>
          <button class="menu-item" @click="router.push('/settings')">Settings</button>
          <button class="menu-item danger" @click="handleLogout">Logout</button>
        </div>
      </div>
    </div>
  </header>
</template>
