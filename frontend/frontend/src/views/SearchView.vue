<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchArticles, searchUsers } from '@/api/search'
import type { ArticleSearchVO, UserSearchVO } from '@/types/models'

type SearchTab = 'article' | 'user'

const router = useRouter()
const route = useRoute()

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
    errorText.value = error instanceof Error ? error.message : '搜索失败'
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
  void load()
}

function submitSearch() {
  page.value = 0
  router.replace({
    path: '/search',
    query: keyword.value.trim()
      ? {
          q: keyword.value.trim(),
        }
      : undefined,
  })
  void load()
}

function gotoArticle(articleId: number) {
  router.push(`/article/${articleId}`)
}

function gotoUser(userId: number) {
  router.push({
    path: '/',
    query: { userId: String(userId) },
  })
}

watch(
  () => route.query.q,
  (queryText) => {
    keyword.value = typeof queryText === 'string' ? queryText : ''
    page.value = 0
    void load()
  },
  {
    immediate: true,
  },
)
</script>

<template>
  <section class="search-layout">
    <header class="search-head">
      <div>
        <p class="section-label">搜索中心</p>
        <h1>查找文章与用户</h1>
        <p>依托你现在的 Elasticsearch 接口，支持文章和用户的模糊搜索。</p>
      </div>
    </header>

    <form class="search-bar" @submit.prevent="submitSearch">
      <input v-model.trim="keyword" placeholder="输入关键词，搜索文章或用户" />
      <button type="submit" :disabled="loading">搜索</button>
    </form>

    <div class="tabs">
      <button :class="{ active: tab === 'article' }" @click="switchTab('article')">文章</button>
      <button :class="{ active: tab === 'user' }" @click="switchTab('user')">用户</button>
    </div>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <div v-if="loading" class="panel">正在搜索...</div>
    <div v-else-if="tab === 'article' && articleRecords.length === 0" class="panel">没有找到相关文章。</div>
    <div v-else-if="tab === 'user' && userRecords.length === 0" class="panel">没有找到相关用户。</div>
    <div v-else-if="tab === 'article'" class="result-list">
      <article v-for="item in articleRecords" :key="item.articleId" class="result-card" @click="gotoArticle(item.articleId)">
        <h2>{{ item.title }}</h2>
        <p>{{ item.summary || '暂无摘要。' }}</p>
        <footer>文章 #{{ item.articleId }} · 作者 {{ item.userId }}</footer>
      </article>
    </div>
    <div v-else class="result-list">
      <article v-for="item in userRecords" :key="item.id" class="result-card" @click="gotoUser(item.id)">
        <h2>{{ item.name }}</h2>
        <footer>User #{{ item.id }}</footer>
      </article>
    </div>

    <footer class="pager">
      <button :disabled="page <= 0 || loading" @click="page -= 1; load()">上一页</button>
      <span>第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
      <button :disabled="loading || (page + 1) * size >= total" @click="page += 1; load()">下一页</button>
    </footer>
  </section>
</template>

<style scoped>
.search-layout { display: grid; gap: 16px; }
.search-head, .panel, .result-card { border: 1px solid var(--line-soft); border-radius: 18px; background: #fff; }
.search-head { padding: 22px; }
.section-label { margin: 0 0 8px; color: var(--brand); font-size: 0.85rem; font-weight: 700; }
.search-head h1 { margin: 0; color: var(--ink-strong); }
.search-head p:last-child { margin: 8px 0 0; color: var(--ink-muted); }
.search-bar { display: flex; gap: 10px; padding: 8px; border: 1px solid var(--line-soft); border-radius: 16px; background: #fff; }
.search-bar input { flex: 1; border: none; background: transparent; padding: 10px 12px; color: var(--ink-strong); }
.search-bar input:focus { outline: none; }
.search-bar button, .tabs button, .pager button { border: none; border-radius: 12px; background: #f4f7fb; color: var(--ink-main); padding: 10px 14px; cursor: pointer; font: inherit; }
.tabs { display: flex; gap: 10px; }
.tabs button.active { background: #eef5ff; color: var(--brand); font-weight: 700; }
.error-text { margin: 0; color: var(--danger); }
.panel { padding: 18px; }
.result-list { display: grid; gap: 14px; }
.result-card { padding: 18px 20px; cursor: pointer; }
.result-card h2 { margin: 0; color: var(--ink-strong); }
.result-card p { margin: 8px 0 0; color: var(--ink-main); line-height: 1.7; }
.result-card footer { margin-top: 10px; color: var(--ink-muted); font-size: 0.86rem; }
.pager { display: flex; justify-content: center; gap: 12px; align-items: center; }
.pager button:disabled { opacity: 0.5; cursor: default; }
</style>
