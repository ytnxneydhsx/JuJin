<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listArticles } from '@/api/article'
import type { ArticleSummaryVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'

type HomeTab = 'latest' | 'ranking'

const router = useRouter()
const route = useRoute()

const activeTab = ref<HomeTab>('latest')
const loading = ref(false)
const errorText = ref('')
const authorFilterUserId = ref<number | null>(null)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const records = ref<ArticleSummaryVO[]>([])

const tabMeta: Record<HomeTab, { label: string; tip: string }> = {
  latest: {
    label: 'Latest',
    tip: 'Sorted by publish time',
  },
  ranking: {
    label: 'Article Ranking',
    tip: 'Sorted by view count',
  },
}

async function loadArticles() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listArticles({
      userId: authorFilterUserId.value === null ? undefined : authorFilterUserId.value,
      page: page.value,
      size: size.value,
      sortBy: activeTab.value === 'latest' ? 'publishedAt' : 'viewCount',
      sortOrder: 'desc',
    })
    records.value = data.records
    total.value = data.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorText.value = error instanceof Error ? error.message : 'Failed to load article list'
  } finally {
    loading.value = false
  }
}

function parseFilterUserId(raw: unknown) {
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function clearFilter() {
  router.push({
    path: '/',
  })
}

function switchTab(tab: HomeTab) {
  if (activeTab.value === tab) {
    return
  }
  activeTab.value = tab
  page.value = 0
}

function toArticle(articleId: number) {
  router.push(`/article/${articleId}`)
}

watch([activeTab, page], loadArticles)
watch(
  () => route.query.userId,
  (queryUserId) => {
    const parsed = parseFilterUserId(queryUserId)
    const changed = authorFilterUserId.value !== parsed
    authorFilterUserId.value = parsed
    if (changed) {
      page.value = 0
    }
    loadArticles()
  },
  {
    immediate: true,
  },
)
</script>

<template>
  <section class="home-layout">
    <header class="home-head">
      <div>
        <h1>Discover Articles</h1>
        <p>Community feed with ranking and latest publish timeline.</p>
      </div>
      <div class="tab-switch">
        <button
          v-for="tab in (Object.keys(tabMeta) as HomeTab[])"
          :key="tab"
          class="tab-btn"
          :class="{ active: activeTab === tab }"
          @click="switchTab(tab)"
        >
          {{ tabMeta[tab].label }}
        </button>
      </div>
    </header>

    <div class="feed-note">{{ tabMeta[activeTab].tip }}</div>
    <div v-if="authorFilterUserId !== null" class="filter-banner">
      <span>Filtering by author #{{ authorFilterUserId }}</span>
      <button @click="clearFilter">Clear</button>
    </div>
    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <div v-if="loading" class="panel">Loading articles...</div>
    <div v-else-if="records.length === 0" class="panel">No articles available.</div>
    <div v-else class="feed-list">
      <article
        v-for="item in records"
        :key="item.articleId"
        class="feed-card"
        @click="toArticle(item.articleId)"
      >
        <div class="feed-meta">
          <span>#{{ item.articleId }}</span>
          <span>By User {{ item.userId }}</span>
          <span>{{ formatDateTime(item.publishedAt) }}</span>
        </div>
        <h2>{{ item.title }}</h2>
        <p>{{ item.summary || 'No summary provided.' }}</p>
        <footer class="feed-stats">
          <span>Views {{ item.viewCount || 0 }}</span>
          <span>Likes {{ item.likeCount || 0 }}</span>
          <span>Favorites {{ item.favoriteCount || 0 }}</span>
        </footer>
      </article>
    </div>

    <footer class="pager">
      <button :disabled="page <= 0 || loading" @click="page -= 1">Previous</button>
      <span>Page {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
      <button
        :disabled="loading || (page + 1) * size >= total"
        @click="page += 1"
      >
        Next
      </button>
    </footer>
  </section>
</template>

<style scoped>
.home-layout {
  display: grid;
  gap: 14px;
}

.home-head {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: linear-gradient(125deg, #ffffff, #f5f8fd);
  padding: 22px 24px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.home-head h1 {
  margin: 0;
  color: var(--ink-strong);
}

.home-head p {
  margin: 6px 0 0;
  color: var(--ink-muted);
}

.tab-switch {
  display: flex;
  gap: 8px;
}

.tab-btn {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 8px 14px;
  cursor: pointer;
}

.tab-btn.active {
  border-color: var(--brand);
  color: var(--brand);
  background: rgba(20, 88, 166, 0.08);
}

.feed-note {
  color: var(--ink-muted);
  padding: 0 4px;
}

.filter-banner {
  border: 1px solid rgba(20, 88, 166, 0.26);
  border-radius: 999px;
  background: rgba(20, 88, 166, 0.08);
  color: var(--brand-strong);
  padding: 6px 10px;
  display: inline-flex;
  gap: 8px;
  align-items: center;
  width: fit-content;
}

.filter-banner button {
  border: 1px solid rgba(20, 88, 166, 0.35);
  border-radius: 999px;
  background: #fff;
  color: var(--brand);
  padding: 4px 10px;
  cursor: pointer;
}

.error-text {
  margin: 0;
  color: var(--danger);
  padding: 0 4px;
}

.panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 18px;
}

.feed-list {
  display: grid;
  gap: 12px;
}

.feed-card {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 16px 18px;
  cursor: pointer;
  transition: 0.18s ease;
}

.feed-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card);
  border-color: rgba(20, 88, 166, 0.35);
}

.feed-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--ink-muted);
  font-size: 0.85rem;
}

.feed-card h2 {
  margin: 8px 0 6px;
  color: var(--ink-strong);
  font-size: 1.1rem;
}

.feed-card p {
  margin: 0;
  color: var(--ink-main);
  line-height: 1.55;
}

.feed-stats {
  margin-top: 12px;
  display: flex;
  gap: 14px;
  color: var(--ink-muted);
  font-size: 0.88rem;
}

.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.pager button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 7px 14px;
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
