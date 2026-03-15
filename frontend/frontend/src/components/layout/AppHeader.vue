<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/user'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const navItems = [
  { path: '/', label: '首页' },
  { path: '/search', label: '搜索' },
  { path: '/drafts', label: '草稿箱' },
]

const userLabel = computed(() =>
  authStore.state.account || `User ${authStore.state.userId}`,
)
const avatarLabel = computed(() => {
  const seed = authStore.state.account || String(authStore.state.userId || '')
  return seed.slice(0, 1).toUpperCase() || 'J'
})
const searchKeyword = ref('')

watch(
  () => route.query.q,
  (queryText) => {
    searchKeyword.value = typeof queryText === 'string' ? queryText : ''
  },
  {
    immediate: true,
  },
)

function isActive(path: string) {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

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

function submitSearch() {
  router.push({
    path: '/search',
    query: searchKeyword.value.trim()
      ? {
          q: searchKeyword.value.trim(),
        }
      : undefined,
  })
}
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <div class="brand-cluster">
        <button class="brand" @click="router.push('/')">
          <span class="brand-mark">J</span>
          <span class="brand-text">掘迹社区</span>
        </button>
        <nav class="topnav">
          <button
            v-for="item in navItems"
            :key="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            @click="router.push(item.path)"
          >
            {{ item.label }}
          </button>
        </nav>
      </div>

      <form class="searchbar" @submit.prevent="submitSearch">
        <input
          v-model.trim="searchKeyword"
          class="search-input"
          type="search"
          placeholder="探索文章与用户"
        />
        <button class="search-submit" type="submit">搜索</button>
      </form>

      <div class="header-actions">
        <button class="creator-btn" @click="router.push('/write')">创作者中心</button>
        <button
          v-if="!authStore.isAuthenticated"
          class="login-btn"
          @click="goAuth"
        >
          登录
        </button>
        <div v-else class="user-area">
          <button class="user-entry" @click="goAuth">
            <span class="avatar-chip">{{ avatarLabel }}</span>
          </button>
          <div class="user-menu">
            <div class="user-card">
              <span class="avatar-chip large">{{ avatarLabel }}</span>
              <div>
                <strong>{{ userLabel }}</strong>
                <p>已登录创作者</p>
              </div>
            </div>
            <button class="menu-item strong" @click="router.push('/me')">我的主页</button>
            <button class="menu-item" @click="router.push('/settings')">账号设置</button>
            <button class="menu-item" @click="router.push('/drafts')">草稿箱</button>
            <button class="menu-item danger" @click="handleLogout">退出登录</button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.brand-cluster {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--ink-strong);
  cursor: pointer;
}

.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1e80ff, #5ea1ff);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
  box-shadow: 0 10px 20px rgba(30, 128, 255, 0.25);
}

.brand-text {
  font-family: var(--font-title);
  font-size: 1.05rem;
  font-weight: 700;
}

.topnav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-item {
  border: none;
  background: transparent;
  color: var(--ink-muted);
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: 0.18s ease;
}

.nav-item:hover {
  background: rgba(30, 128, 255, 0.08);
  color: var(--ink-strong);
}

.nav-item.active {
  color: var(--brand);
  font-weight: 700;
}

.searchbar {
  flex: 1;
  max-width: 420px;
  min-width: 180px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: var(--bg-panel);
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--ink-strong);
  padding: 8px 10px;
}

.search-input:focus {
  outline: none;
}

.search-submit,
.creator-btn,
.login-btn {
  border: none;
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: 0.18s ease;
}

.search-submit {
  background: #f4f7fb;
  color: var(--ink-main);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.creator-btn {
  background: linear-gradient(135deg, #1e80ff, #4f9dff);
  color: #fff;
  box-shadow: 0 10px 24px rgba(30, 128, 255, 0.22);
}

.login-btn {
  background: #f4f7fb;
  color: var(--ink-main);
}

.user-area {
  position: relative;
}

.user-entry {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.avatar-chip {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #f7d79b, #f39db9 55%, #93c7ff);
  color: #24324a;
  font-weight: 700;
  box-shadow: 0 8px 20px rgba(36, 50, 74, 0.12);
}

.avatar-chip.large {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  flex-shrink: 0;
}

.user-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 14px);
  width: 230px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 48px rgba(17, 34, 68, 0.12);
  opacity: 0;
  pointer-events: none;
  transform: translateY(8px);
  transition: 0.18s ease;
}

.user-area:hover .user-menu {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  margin-bottom: 6px;
  border-bottom: 1px solid var(--line-soft);
}

.user-card strong {
  display: block;
  color: var(--ink-strong);
}

.user-card p {
  margin: 4px 0 0;
  color: var(--ink-muted);
  font-size: 0.85rem;
}

.menu-item {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--ink-main);
  text-align: left;
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
}

.menu-item:hover,
.menu-item.strong {
  background: #f4f7fb;
}

.menu-item.strong {
  color: var(--brand);
  font-weight: 700;
}

.menu-item.danger {
  color: var(--danger);
}

@media (max-width: 1080px) {
  .brand-cluster {
    width: 100%;
    justify-content: space-between;
  }

  .searchbar {
    order: 3;
    max-width: none;
    width: 100%;
  }

  .header-actions {
    margin-left: auto;
  }
}

@media (max-width: 768px) {
  .topnav {
    display: none;
  }

  .creator-btn {
    padding-inline: 12px;
  }

  .search-submit {
    display: none;
  }
}
</style>
