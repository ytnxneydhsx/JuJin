<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteMyArticle, listMyArticles } from '@/api/article'
import { getUserProfile } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { ArticleSummaryVO, UserPublicProfileVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'

const router = useRouter()
const authStore = useAuthStore()

const loadingProfile = ref(false)
const loadingArticles = ref(false)
const errorText = ref('')
const successText = ref('')
const profile = ref<UserPublicProfileVO | null>(null)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const records = ref<ArticleSummaryVO[]>([])

async function loadProfile() {
  if (authStore.state.userId === null) {
    return
  }
  loadingProfile.value = true
  errorText.value = ''
  try {
    profile.value = await getUserProfile(authStore.state.userId)
  } catch (error) {
    profile.value = null
    errorText.value = error instanceof Error ? error.message : 'Failed to load profile'
  } finally {
    loadingProfile.value = false
  }
}

async function loadMyArticles() {
  loadingArticles.value = true
  errorText.value = ''
  try {
    const data = await listMyArticles(page.value, size.value)
    records.value = data.records
    total.value = data.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorText.value = error instanceof Error ? error.message : 'Failed to load my articles'
  } finally {
    loadingArticles.value = false
  }
}

async function handleDeleteArticle(articleId: number) {
  const confirmed = window.confirm(`Delete article #${articleId}?`)
  if (!confirmed) {
    return
  }
  successText.value = ''
  errorText.value = ''
  try {
    await deleteMyArticle(articleId)
    successText.value = `Article #${articleId} deleted`
    if (records.value.length === 1 && page.value > 0) {
      page.value -= 1
    }
    await loadMyArticles()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to delete article'
  }
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadMyArticles()])
})
</script>

<template>
  <section class="profile-layout">
    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div v-if="loadingProfile" class="panel">Loading profile...</div>
    <section v-else-if="profile" class="profile-card">
      <img
        class="avatar"
        :src="profile.avatarUrl || 'https://placehold.co/120x120?text=Avatar'"
        alt="avatar"
      />
      <div class="profile-main">
        <h1>{{ profile.name || authStore.state.account }}</h1>
        <p>{{ profile.sign || 'No sign text yet.' }}</p>
        <div class="profile-meta">
          <span>User #{{ profile.userId }}</span>
          <span>Account {{ authStore.state.account }}</span>
        </div>
      </div>
      <button class="settings-btn" @click="router.push('/settings')">Go Settings</button>
    </section>

    <section class="article-panel">
      <header>
        <h2>My Articles</h2>
        <button @click="router.push('/write')">Write New</button>
      </header>

      <div v-if="loadingArticles" class="panel">Loading articles...</div>
      <div v-else-if="records.length === 0" class="panel">No published articles yet.</div>
      <div v-else class="article-list">
        <article v-for="item in records" :key="item.articleId" class="article-card">
          <h3 @click="router.push(`/article/${item.articleId}`)">{{ item.title }}</h3>
          <p>{{ item.summary || 'No summary.' }}</p>
          <footer>
            <span>Published {{ formatDateTime(item.publishedAt) }}</span>
            <span>Views {{ item.viewCount || 0 }}</span>
            <span>Likes {{ item.likeCount || 0 }}</span>
            <span>Favorites {{ item.favoriteCount || 0 }}</span>
          </footer>
          <div class="actions">
            <button @click="router.push(`/article/${item.articleId}`)">View</button>
            <button class="danger" @click="handleDeleteArticle(item.articleId)">Delete</button>
          </div>
        </article>
      </div>

      <footer class="pager">
        <button :disabled="page <= 0 || loadingArticles" @click="page -= 1; loadMyArticles()">Previous</button>
        <span>Page {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
        <button
          :disabled="loadingArticles || (page + 1) * size >= total"
          @click="page += 1; loadMyArticles()"
        >
          Next
        </button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.profile-layout {
  display: grid;
  gap: 12px;
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.success-text {
  margin: 0;
  color: var(--ok);
}

.panel,
.profile-card,
.article-panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
}

.panel {
  padding: 16px;
}

.profile-card {
  padding: 16px;
  display: flex;
  gap: 14px;
  align-items: center;
}

.avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--line-soft);
}

.profile-main {
  flex: 1;
}

.profile-main h1 {
  margin: 0;
  color: var(--ink-strong);
}

.profile-main p {
  margin: 6px 0 0;
  color: var(--ink-main);
}

.profile-meta {
  margin-top: 8px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.settings-btn,
.article-panel header button,
.actions button,
.pager button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 8px 13px;
  cursor: pointer;
}

.article-panel {
  padding: 14px;
  display: grid;
  gap: 10px;
}

.article-panel header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.article-panel header h2 {
  margin: 0;
}

.article-list {
  display: grid;
  gap: 10px;
}

.article-card {
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 12px 14px;
  display: grid;
  gap: 8px;
}

.article-card h3 {
  margin: 0;
  color: var(--ink-strong);
  cursor: pointer;
}

.article-card p {
  margin: 0;
  color: var(--ink-main);
}

.article-card footer {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.actions {
  display: flex;
  gap: 8px;
}

.actions .danger {
  border-color: rgba(195, 57, 42, 0.35);
  color: var(--danger);
}

.pager {
  display: flex;
  justify-content: center;
  gap: 10px;
  align-items: center;
}

.pager button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 768px) {
  .profile-card {
    flex-wrap: wrap;
  }
}
</style>

