<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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

const avatarLabel = computed(() => {
  const source = profile.value?.name || authStore.state.account || 'J'
  return source.slice(0, 1).toUpperCase()
})

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
    errorText.value = error instanceof Error ? error.message : '加载个人资料失败'
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
    errorText.value = error instanceof Error ? error.message : '加载文章失败'
  } finally {
    loadingArticles.value = false
  }
}

async function handleDeleteArticle(articleId: number) {
  const confirmed = window.confirm(`删除文章 #${articleId}？`)
  if (!confirmed) {
    return
  }
  successText.value = ''
  errorText.value = ''
  try {
    await deleteMyArticle(articleId)
    successText.value = `文章 #${articleId} 已删除`
    if (records.value.length === 1 && page.value > 0) {
      page.value -= 1
    }
    await loadMyArticles()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除文章失败'
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

    <div v-if="loadingProfile" class="panel">正在加载个人主页...</div>
    <section v-else-if="profile" class="hero-card">
      <div class="hero-main">
        <div class="hero-avatar">
          <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="avatar" />
          <span v-else>{{ avatarLabel }}</span>
        </div>
        <div class="hero-copy">
          <div class="name-row">
            <h1>{{ profile.name || authStore.state.account }}</h1>
            <span class="account-pill">@{{ authStore.state.account }}</span>
          </div>
          <p>{{ profile.sign || '这里还没有个性签名，去设置页写一句吧。' }}</p>
          <div class="hero-actions">
            <button class="primary-btn" @click="router.push('/write')">写文章</button>
            <button class="ghost-btn" @click="router.push('/settings')">设置</button>
            <button class="ghost-btn" @click="router.push('/drafts')">草稿箱</button>
          </div>
        </div>
      </div>

      <div class="hero-stats">
        <div class="stat-card">
          <strong>{{ total }}</strong>
          <span>文章</span>
        </div>
        <div class="stat-card">
          <strong>{{ authStore.state.userId }}</strong>
          <span>用户编号</span>
        </div>
        <div class="stat-card">
          <strong>{{ records.reduce((sum, item) => sum + (item.viewCount || 0), 0) }}</strong>
          <span>阅读总量</span>
        </div>
      </div>
    </section>

    <section class="content-grid">
      <div class="content-main">
        <header class="section-head">
          <div>
            <p class="section-label">文章</p>
            <h2>我发布的内容</h2>
          </div>
          <button class="ghost-btn" @click="router.push('/write')">新建文章</button>
        </header>

        <div v-if="loadingArticles" class="panel">正在加载文章...</div>
        <div v-else-if="records.length === 0" class="panel">你还没有发布文章。</div>
        <div v-else class="article-list">
          <article v-for="item in records" :key="item.articleId" class="article-card">
            <div class="article-copy">
              <h3 @click="router.push(`/article/${item.articleId}`)">{{ item.title }}</h3>
              <p>{{ item.summary || '暂无摘要内容。' }}</p>
              <footer>
                <span>发布时间 {{ formatDateTime(item.publishedAt) }}</span>
                <span>阅读 {{ item.viewCount || 0 }}</span>
                <span>点赞 {{ item.likeCount || 0 }}</span>
              </footer>
            </div>
            <div class="article-actions">
              <button class="ghost-btn" @click="router.push(`/article/${item.articleId}`)">查看</button>
              <button class="danger-btn" @click="handleDeleteArticle(item.articleId)">删除</button>
            </div>
          </article>
        </div>

        <footer class="pager">
          <button :disabled="page <= 0 || loadingArticles" @click="page -= 1; loadMyArticles()">上一页</button>
          <span>第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span>
          <button
            :disabled="loadingArticles || (page + 1) * size >= total"
            @click="page += 1; loadMyArticles()"
          >
            下一页
          </button>
        </footer>
      </div>

      <aside class="content-side">
        <section class="side-card">
          <p class="section-label">个人简介</p>
          <h3>创作者信息</h3>
          <dl>
            <div>
              <dt>昵称</dt>
              <dd>{{ profile?.name || '-' }}</dd>
            </div>
            <div>
              <dt>账号</dt>
              <dd>{{ authStore.state.account }}</dd>
            </div>
            <div>
              <dt>签名</dt>
              <dd>{{ profile?.sign || '未设置' }}</dd>
            </div>
          </dl>
        </section>
      </aside>
    </section>
  </section>
</template>

<style scoped>
.profile-layout { display: grid; gap: 16px; }
.hero-card, .panel, .section-head, .article-card, .side-card { border: 1px solid var(--line-soft); border-radius: 18px; background: #fff; }
.panel, .side-card { padding: 18px; }
.hero-card { padding: 22px; display: grid; gap: 18px; }
.hero-main { display: flex; gap: 18px; align-items: center; }
.hero-avatar { width: 96px; height: 96px; border-radius: 28px; overflow: hidden; background: linear-gradient(135deg, #f7d79b, #f39db9 55%, #93c7ff); display: grid; place-items: center; color: #24324a; font-size: 1.8rem; font-weight: 700; flex-shrink: 0; }
.hero-avatar img { width: 100%; height: 100%; object-fit: cover; }
.hero-copy { flex: 1; min-width: 0; }
.name-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.name-row h1, .section-head h2, .side-card h3 { margin: 0; color: var(--ink-strong); }
.account-pill { border-radius: 999px; padding: 5px 10px; background: #f4f7fb; color: var(--ink-muted); font-size: 0.86rem; }
.hero-copy p { margin: 10px 0 0; color: var(--ink-main); line-height: 1.7; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.primary-btn, .ghost-btn, .danger-btn, .pager button { border: none; border-radius: 12px; padding: 10px 14px; cursor: pointer; font: inherit; }
.primary-btn { background: linear-gradient(135deg, #1e80ff, #5ea1ff); color: #fff; }
.ghost-btn, .pager button { background: #f4f7fb; color: var(--ink-main); }
.danger-btn { background: #fff2f0; color: var(--danger); }
.hero-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.stat-card { border-radius: 16px; background: #f7f8fa; padding: 16px; display: grid; gap: 4px; }
.stat-card strong { color: var(--ink-strong); font-size: 1.4rem; }
.stat-card span, .section-label { color: var(--brand); font-size: 0.85rem; font-weight: 700; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 18px; align-items: start; }
.content-main { display: grid; gap: 16px; }
.content-side { position: sticky; top: 92px; }
.section-head { padding: 18px 20px; display: flex; justify-content: space-between; align-items: center; }
.article-list { display: grid; gap: 14px; }
.article-card { padding: 18px 20px; display: flex; justify-content: space-between; gap: 16px; }
.article-copy { display: grid; gap: 8px; }
.article-copy h3 { margin: 0; color: var(--ink-strong); cursor: pointer; }
.article-copy p { margin: 0; color: var(--ink-main); line-height: 1.7; }
.article-copy footer { display: flex; flex-wrap: wrap; gap: 12px; color: var(--ink-muted); font-size: 0.86rem; }
.article-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.side-card dl { margin: 0; display: grid; gap: 12px; }
.side-card dl div { display: grid; gap: 4px; }
.side-card dt { color: var(--ink-muted); }
.side-card dd { margin: 0; color: var(--ink-strong); }
.pager { display: flex; justify-content: center; align-items: center; gap: 12px; }
.pager button:disabled { opacity: 0.5; cursor: default; }
.error-text, .success-text { margin: 0; }
.error-text { color: var(--danger); }
.success-text { color: var(--ok); }
@media (max-width: 980px) { .content-grid { grid-template-columns: 1fr; } .content-side { position: static; } .article-card { flex-direction: column; } }
@media (max-width: 720px) { .hero-main { flex-direction: column; align-items: flex-start; } .hero-stats { grid-template-columns: 1fr; } .section-head { flex-direction: column; align-items: flex-start; gap: 12px; } }
</style>
