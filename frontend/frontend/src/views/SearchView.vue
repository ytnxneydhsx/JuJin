<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchArticles, searchUsers } from '@/api/search'
import type { ArticleSearchVO, UserSearchVO } from '@/types/models'

type SearchTab = 'article' | 'user'

const router = useRouter()

const tab = ref<SearchTab>('article')
const keyword = ref('')
const loading = ref(false)
const errorText = ref('')
const page = ref(0)
const size = ref(10)
const total = ref(0)
const articleRecords = ref<ArticleSearchVO[]>([])
const userRecords = ref<UserSearchVO[]>([])

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    if (tab.value === 'article') {
      const data = await searchArticles({
        q: keyword.value.trim() || undefined,
        page: page.value,
        size: size.value,
      })
      articleRecords.value = data.records
      userRecords.value = []
      total.value = data.total
      return
    }
    const data = await searchUsers({
      q: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    userRecords.value = data.records
    articleRecords.value = []
    total.value = data.total
  } catch (error) {
    articleRecords.value = []
    userRecords.value = []
    total.value = 0
    errorText.value = error instanceof Error ? error.message : 'Search failed'
  } finally {
    loading.value = false
  }
}

function switchTab(next: SearchTab) {
  if (tab.value === next) {
    return
  }
  tab.value = next
  page.value = 0
  load()
}

function submitSearch() {
  page.value = 0
  load()
}

function gotoArticle(articleId: number) {
  router.push(`/article/${articleId}`)
}

function gotoUser(userId: number) {
  router.push({
    path: '/',
    query: {
      userId: String(userId),
    },
  })
}

load()
</script>

<template>
  <section class="search-layout">
    <header class="search-head">
      <h1>Search</h1>
      <p>Fuzzy search users and articles through Elasticsearch.</p>
    </header>

    <form class="search-bar" @submit.prevent="submitSearch">
      <input v-model.trim="keyword" placeholder="Input keyword" />
      <button type="submit" :disabled="loading">Search</button>
    </form>

    <div class="tabs">
      <button :class="{ active: tab === 'article' }" @click="switchTab('article')">Articles</button>
      <button :class="{ active: tab === 'user' }" @click="switchTab('user')">Users</button>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <div v-if="loading" class="panel">Searching...</div>
    <div v-else-if="tab === 'article' && articleRecords.length === 0" class="panel">No articles found.</div>
    <div v-else-if="tab === 'user' && userRecords.length === 0" class="panel">No users found.</div>
    <div v-else-if="tab === 'article'" class="result-list">
      <article
        v-for="item in articleRecords"
        :key="item.articleId"
        class="result-card"
        @click="gotoArticle(item.articleId)"
      >
        <h2>{{ item.title }}</h2>
        <p>{{ item.summary || 'No summary.' }}</p>
        <footer>Article #{{ item.articleId }} · Author {{ item.userId }}</footer>
      </article>
    </div>
    <div v-else class="result-list">
      <article
        v-for="item in userRecords"
        :key="item.id"
        class="result-card"
        @click="gotoUser(item.id)"
      >
        <h2>{{ item.name }}</h2>
        <footer>User #{{ item.id }}</footer>
      </article>
    </div>

    <footer class="pager">
      <button :disabled="page <= 0 || loading" @click="page -= 1; load()">Previous</button>
      <span>Page {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
      <button :disabled="loading || (page + 1) * size >= total" @click="page += 1; load()">Next</button>
    </footer>
  </section>
</template>

<style scoped>
.search-layout {
  display: grid;
  gap: 12px;
}

.search-head {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: linear-gradient(130deg, #fff, #f6f9ff);
  padding: 20px 22px;
}

.search-head h1 {
  margin: 0;
  color: var(--ink-strong);
}

.search-head p {
  margin: 6px 0 0;
  color: var(--ink-muted);
}

.search-bar {
  display: flex;
  gap: 8px;
}

.search-bar input {
  flex: 1;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  padding: 10px 14px;
}

.search-bar button,
.tabs button,
.pager button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 8px 14px;
  cursor: pointer;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tabs button.active {
  border-color: rgba(20, 88, 166, 0.45);
  color: var(--brand);
  background: rgba(20, 88, 166, 0.08);
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 16px;
}

.result-list {
  display: grid;
  gap: 10px;
}

.result-card {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 12px 14px;
  cursor: pointer;
}

.result-card h2 {
  margin: 0;
  color: var(--ink-strong);
}

.result-card p {
  margin: 6px 0 0;
  color: var(--ink-main);
}

.result-card footer {
  margin-top: 8px;
  color: var(--ink-muted);
  font-size: 0.86rem;
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
</style>

