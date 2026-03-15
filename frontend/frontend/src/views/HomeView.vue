<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listArticles } from '@/api/article'
import { useAuthStore } from '@/stores/auth'
import type { ArticleSummaryVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'

type HomeTab = 'latest' | 'ranking'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref<HomeTab>('latest')
const loading = ref(false)
const loadingHot = ref(false)
const errorText = ref('')
const authorFilterUserId = ref<number | null>(null)
const page = ref(0)
const size = ref(10)
const total = ref(0)
const records = ref<ArticleSummaryVO[]>([])
const hotRecords = ref<ArticleSummaryVO[]>([])

const tabMeta: Record<HomeTab, { label: string; tip: string }> = {
  latest: {
    label: '最新',
    tip: '查看最近发布的文章',
  },
  ranking: {
    label: '热榜',
    tip: '按浏览量排序的热门内容',
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
    errorText.value = error instanceof Error ? error.message : '加载文章列表失败'
  } finally {
    loading.value = false
  }
}

async function loadHotArticles() {
  loadingHot.value = true
  try {
    const data = await listArticles({
      page: 0,
      size: 6,
      sortBy: 'viewCount',
      sortOrder: 'desc',
    })
    hotRecords.value = data.records
  } catch {
    hotRecords.value = []
  } finally {
    loadingHot.value = false
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

function goToAuthor(userId: number) {
  router.push({
    path: '/',
    query: {
      userId: String(userId),
    },
  })
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
    void Promise.all([loadArticles(), loadHotArticles()])
  },
  {
    immediate: true,
  },
)
</script>

<template>
  <section class="home-layout">
    <aside class="home-side left-side">
      <section class="side-card welcome-card">
        <p class="eyebrow">内容社区</p>
        <h1>发现值得读的技术文章</h1>
        <p>
          用你现有的文章接口构建首页信息流，保持内容优先的阅读体验。
        </p>
        <div class="side-actions">
          <button class="primary-btn" @click="router.push('/write')">开始创作</button>
          <button class="ghost-btn" @click="router.push('/search')">去搜索</button>
        </div>
      </section>

      <section class="side-card section-card">
        <h2>内容流</h2>
        <button
          v-for="tab in (Object.keys(tabMeta) as HomeTab[])"
          :key="tab"
          class="section-item"
          :class="{ active: activeTab === tab }"
          @click="switchTab(tab)"
        >
          <strong>{{ tabMeta[tab].label }}</strong>
          <span>{{ tabMeta[tab].tip }}</span>
        </button>
      </section>
    </aside>

    <main class="feed-column">
      <header class="feed-head">
        <div class="feed-head-main">
          <div class="feed-tabs">
            <button
              v-for="tab in (Object.keys(tabMeta) as HomeTab[])"
              :key="tab"
              class="feed-tab"
              :class="{ active: activeTab === tab }"
              @click="switchTab(tab)"
            >
              {{ tabMeta[tab].label }}
            </button>
          </div>
          <p class="feed-tip">{{ tabMeta[activeTab].tip }}</p>
        </div>
      </header>

      <div v-if="authorFilterUserId !== null" class="filter-banner">
        <span>当前仅查看用户 #{{ authorFilterUserId }} 发布的文章</span>
        <button @click="clearFilter">清除筛选</button>
      </div>

      <p v-if="errorText" class="error-text">{{ errorText }}</p>

      <div v-if="loading" class="panel">正在加载文章...</div>
      <div v-else-if="records.length === 0" class="panel">这里暂时还没有内容。</div>
      <div v-else class="feed-list">
        <article
          v-for="item in records"
          :key="item.articleId"
          class="feed-card"
          @click="toArticle(item.articleId)"
        >
          <div class="feed-copy">
            <div class="card-meta">
              <button class="author-link" @click.stop="goToAuthor(item.userId)">作者 {{ item.userId }}</button>
              <span>{{ formatDateTime(item.publishedAt) }}</span>
            </div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.summary || '作者还没有填写摘要内容。' }}</p>
            <footer class="feed-stats">
              <span>阅读 {{ item.viewCount || 0 }}</span>
              <span>点赞 {{ item.likeCount || 0 }}</span>
              <span>收藏 {{ item.favoriteCount || 0 }}</span>
            </footer>
          </div>
          <div class="cover-shell">
            <img
              v-if="item.coverUrl"
              class="feed-cover"
              :src="item.coverUrl"
              alt="article cover"
            />
            <div v-else class="cover-placeholder">文章封面</div>
          </div>
        </article>
      </div>

      <footer class="pager">
        <button :disabled="page <= 0 || loading" @click="page -= 1">上一页</button>
        <span>第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
        <button
          :disabled="loading || (page + 1) * size >= total"
          @click="page += 1"
        >
          下一页
        </button>
      </footer>
    </main>

    <aside class="home-side right-side">
      <section class="side-card profile-card">
        <p class="eyebrow">创作入口</p>
        <strong>{{ authStore.isAuthenticated ? authStore.state.account : '游客模式' }}</strong>
        <p>
          {{ authStore.isAuthenticated ? '继续写作、管理草稿或查看个人主页。' : '登录后可以创作、点赞和发表评论。' }}
        </p>
        <div class="stack-actions">
          <button class="primary-btn" @click="router.push(authStore.isAuthenticated ? '/write' : '/login')">
            {{ authStore.isAuthenticated ? '写文章' : '去登录' }}
          </button>
          <button class="ghost-btn" @click="router.push(authStore.isAuthenticated ? '/me' : '/search')">
            {{ authStore.isAuthenticated ? '我的主页' : '浏览搜索' }}
          </button>
        </div>
      </section>

      <section class="side-card ranking-card">
        <div class="card-title-row">
          <h2>文章榜</h2>
          <span v-if="loadingHot">加载中</span>
        </div>
        <button
          v-for="(item, index) in hotRecords"
          :key="item.articleId"
          class="ranking-item"
          @click="toArticle(item.articleId)"
        >
          <span class="rank-index">{{ index + 1 }}</span>
          <span class="rank-copy">
            <strong>{{ item.title }}</strong>
            <small>{{ item.viewCount || 0 }} 次阅读</small>
          </span>
        </button>
        <p v-if="!loadingHot && hotRecords.length === 0" class="side-empty">暂无榜单数据</p>
      </section>
    </aside>
  </section>
</template>

<style scoped>
.home-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.home-side {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 92px;
}

.side-card,
.feed-head,
.panel,
.filter-banner,
.feed-card {
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: #fff;
}

.side-card,
.feed-head,
.panel {
  padding: 18px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--brand);
  font-size: 0.85rem;
  font-weight: 700;
}

.welcome-card h1 {
  margin: 0;
  color: var(--ink-strong);
  font-size: 1.35rem;
  line-height: 1.3;
}

.welcome-card p {
  color: var(--ink-muted);
  line-height: 1.7;
}

.side-actions,
.stack-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.primary-btn,
.ghost-btn,
.feed-tab,
.pager button,
.filter-banner button,
.author-link,
.ranking-item {
  border: none;
  background: transparent;
  font: inherit;
}

.primary-btn,
.ghost-btn,
.pager button,
.filter-banner button {
  border-radius: 12px;
  padding: 10px 14px;
  cursor: pointer;
}

.primary-btn {
  background: linear-gradient(135deg, #1e80ff, #5ea1ff);
  color: #fff;
}

.ghost-btn {
  background: #f4f7fb;
  color: var(--ink-main);
}

.section-card h2,
.ranking-card h2 {
  margin: 0 0 12px;
  color: var(--ink-strong);
  font-size: 1rem;
}

.section-item {
  width: 100%;
  display: grid;
  gap: 4px;
  border: none;
  border-radius: 14px;
  background: transparent;
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
}

.section-item strong {
  color: var(--ink-strong);
}

.section-item span {
  color: var(--ink-muted);
  font-size: 0.85rem;
}

.section-item.active {
  background: #f4f8ff;
  box-shadow: inset 0 0 0 1px rgba(30, 128, 255, 0.16);
}

.feed-column {
  display: grid;
  gap: 16px;
}

.feed-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.feed-tabs {
  display: flex;
  gap: 18px;
}

.feed-tab {
  position: relative;
  padding: 0 0 12px;
  color: var(--ink-muted);
  cursor: pointer;
}

.feed-tab.active {
  color: var(--ink-strong);
  font-weight: 700;
}

.feed-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  border-radius: 999px;
  background: var(--brand);
}

.feed-tip {
  margin: 12px 0 0;
  color: var(--ink-muted);
}

.filter-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  color: var(--brand-strong);
  background: #f4f8ff;
}

.filter-banner button {
  color: var(--brand);
  cursor: pointer;
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.feed-list {
  display: grid;
}

.feed-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px;
  gap: 18px;
  padding: 20px;
  cursor: pointer;
  border-radius: 0;
  border-left: none;
  border-right: none;
}

.feed-list .feed-card:first-child {
  border-top-left-radius: 16px;
  border-top-right-radius: 16px;
}

.feed-list .feed-card:last-child {
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
}

.feed-copy {
  display: grid;
  gap: 10px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ink-muted);
  font-size: 0.88rem;
}

.author-link {
  color: var(--ink-main);
  cursor: pointer;
  padding: 0;
}

.author-link:hover {
  color: var(--brand);
}

.feed-card h2 {
  margin: 0;
  color: var(--ink-strong);
  font-size: 1.15rem;
}

.feed-card p {
  margin: 0;
  color: var(--ink-main);
  line-height: 1.7;
}

.feed-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.cover-shell {
  display: flex;
  justify-content: flex-end;
}

.feed-cover,
.cover-placeholder {
  width: 160px;
  height: 108px;
  border-radius: 14px;
}

.feed-cover {
  object-fit: cover;
  border: 1px solid var(--line-soft);
}

.cover-placeholder {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #f6f8fb, #edf2f9);
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.profile-card strong {
  color: var(--ink-strong);
  font-size: 1.05rem;
}

.profile-card p {
  color: var(--ink-muted);
  line-height: 1.6;
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
}

.card-title-row span {
  color: var(--ink-muted);
  font-size: 0.85rem;
}

.ranking-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--line-soft);
  text-align: left;
  cursor: pointer;
}

.ranking-item:first-of-type {
  border-top: none;
}

.rank-index {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: #f4f7fb;
  color: var(--brand);
  display: grid;
  place-items: center;
  font-weight: 700;
  flex-shrink: 0;
}

.rank-copy {
  display: grid;
  gap: 4px;
}

.rank-copy strong {
  color: var(--ink-strong);
  line-height: 1.5;
}

.rank-copy small,
.side-empty {
  color: var(--ink-muted);
}

.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
}

.pager button {
  padding: 9px 14px;
  border-radius: 12px;
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--line-soft);
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.45;
  cursor: default;
}

@media (max-width: 1180px) {
  .home-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
  }

  .left-side {
    display: none;
  }
}

@media (max-width: 900px) {
  .home-layout {
    grid-template-columns: 1fr;
  }

  .home-side {
    position: static;
  }

  .feed-card {
    grid-template-columns: 1fr;
  }

  .cover-shell {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .feed-tabs {
    gap: 12px;
  }

  .filter-banner {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
