<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getArticle, toggleFavoriteArticle, toggleLikeArticle } from '@/api/article'
import {
  createComment,
  deleteComment,
  listRootComments,
  listThreadComments,
  toggleCommentLike,
} from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import type { ArticleCommentVO, ArticleDetailVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'
import { renderMarkdownToSafeHtml } from '@/utils/markdown'

interface ThreadState {
  open: boolean
  loading: boolean
  errorText: string
  page: number
  size: number
  total: number
  records: ArticleCommentVO[]
  replyContent: string
  replyParentId: number | null
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const article = ref<ArticleDetailVO | null>(null)
const loadingArticle = ref(false)
const loadingRootComments = ref(false)
const interactionLoading = ref(false)
const errorText = ref('')
const commentErrorText = ref('')
const rootCommentContent = ref('')
const rootComments = ref<ArticleCommentVO[]>([])
const rootPage = ref(0)
const rootSize = ref(10)
const rootTotal = ref(0)
const threadMap = reactive<Record<number, ThreadState>>({})

const articleId = computed(() => {
  const parsed = Number(route.params.articleId)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
})

const renderHtml = computed(() => {
  return renderMarkdownToSafeHtml(article.value?.content || '')
})

function ensureThreadState(rootId: number) {
  if (!threadMap[rootId]) {
    threadMap[rootId] = {
      open: false,
      loading: false,
      errorText: '',
      page: 0,
      size: 20,
      total: 0,
      records: [],
      replyContent: '',
      replyParentId: null,
    }
  }
  return threadMap[rootId]
}

function requireLoginOrRedirect() {
  if (authStore.isAuthenticated) {
    return true
  }
  router.push({
    path: '/login',
    query: {
      redirect: route.fullPath,
    },
  })
  return false
}

async function loadArticle() {
  if (articleId.value === null) {
    errorText.value = 'Invalid article id'
    return
  }
  loadingArticle.value = true
  errorText.value = ''
  try {
    article.value = await getArticle(articleId.value)
  } catch (error) {
    article.value = null
    errorText.value = error instanceof Error ? error.message : 'Failed to load article'
  } finally {
    loadingArticle.value = false
  }
}

async function loadRootCommentPage() {
  if (articleId.value === null) {
    return
  }
  loadingRootComments.value = true
  commentErrorText.value = ''
  try {
    const data = await listRootComments(articleId.value, {
      page: rootPage.value,
      size: rootSize.value,
    })
    rootComments.value = data.records
    rootTotal.value = data.total
  } catch (error) {
    rootComments.value = []
    rootTotal.value = 0
    commentErrorText.value = error instanceof Error ? error.message : 'Failed to load comments'
  } finally {
    loadingRootComments.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadArticle(), loadRootCommentPage()])
}

async function handleToggleLikeArticle() {
  if (!requireLoginOrRedirect() || article.value === null || articleId.value === null) {
    return
  }
  interactionLoading.value = true
  errorText.value = ''
  try {
    const result = await toggleLikeArticle(articleId.value)
    article.value.liked = result.liked
    article.value.likeCount = result.likeCount
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to toggle like'
  } finally {
    interactionLoading.value = false
  }
}

async function handleToggleFavoriteArticle() {
  if (!requireLoginOrRedirect() || article.value === null || articleId.value === null) {
    return
  }
  interactionLoading.value = true
  errorText.value = ''
  try {
    const result = await toggleFavoriteArticle(articleId.value)
    article.value.favorited = result.favorited
    article.value.favoriteCount = result.favoriteCount
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to toggle favorite'
  } finally {
    interactionLoading.value = false
  }
}

async function submitRootComment() {
  if (!requireLoginOrRedirect() || articleId.value === null) {
    return
  }
  const content = rootCommentContent.value.trim()
  if (!content) {
    commentErrorText.value = 'Comment cannot be empty'
    return
  }
  commentErrorText.value = ''
  try {
    await createComment({
      articleId: articleId.value,
      content,
    })
    rootCommentContent.value = ''
    rootPage.value = 0
    await loadRootCommentPage()
  } catch (error) {
    commentErrorText.value = error instanceof Error ? error.message : 'Failed to create comment'
  }
}

async function loadThread(rootId: number) {
  if (articleId.value === null) {
    return
  }
  const state = ensureThreadState(rootId)
  state.loading = true
  state.errorText = ''
  try {
    const data = await listThreadComments(articleId.value, rootId, {
      page: state.page,
      size: state.size,
    })
    state.records = data.records.filter((item) => item.commentId !== rootId)
    state.total = data.total
  } catch (error) {
    state.records = []
    state.total = 0
    state.errorText = error instanceof Error ? error.message : 'Failed to load thread'
  } finally {
    state.loading = false
  }
}

async function toggleThread(rootId: number) {
  const state = ensureThreadState(rootId)
  state.open = !state.open
  if (state.open && state.records.length === 0) {
    await loadThread(rootId)
  }
}

function prepareReply(rootId: number, parentId: number) {
  const state = ensureThreadState(rootId)
  state.replyParentId = parentId
}

async function submitReply(rootId: number) {
  if (!requireLoginOrRedirect() || articleId.value === null) {
    return
  }
  const state = ensureThreadState(rootId)
  const content = state.replyContent.trim()
  if (!content) {
    state.errorText = 'Reply cannot be empty'
    return
  }
  if (state.replyParentId === null) {
    state.errorText = 'Reply target is required'
    return
  }
  state.errorText = ''
  try {
    await createComment({
      articleId: articleId.value,
      parentId: state.replyParentId,
      content,
    })
    state.replyContent = ''
    await Promise.all([loadRootCommentPage(), loadThread(rootId)])
  } catch (error) {
    state.errorText = error instanceof Error ? error.message : 'Failed to create reply'
  }
}

function patchCommentLiked(commentId: number, liked: boolean) {
  rootComments.value = rootComments.value.map((item) =>
    item.commentId === commentId ? { ...item, liked } : item,
  )
  Object.keys(threadMap).forEach((key) => {
    const rootId = Number(key)
    const state = threadMap[rootId]
    if (!state) {
      return
    }
    state.records = state.records.map((item) =>
      item.commentId === commentId ? { ...item, liked } : item,
    )
  })
}

async function handleToggleCommentLike(commentId: number) {
  if (!requireLoginOrRedirect()) {
    return
  }
  try {
    const data = await toggleCommentLike(commentId)
    patchCommentLiked(commentId, data.liked)
  } catch (error) {
    commentErrorText.value = error instanceof Error ? error.message : 'Failed to toggle comment like'
  }
}

async function handleDeleteComment(commentId: number, rootId: number) {
  if (!requireLoginOrRedirect()) {
    return
  }
  const confirmed = window.confirm(`Delete comment #${commentId}?`)
  if (!confirmed) {
    return
  }
  try {
    await deleteComment(commentId)
    if (commentId === rootId) {
      await loadRootCommentPage()
      return
    }
    await Promise.all([loadRootCommentPage(), loadThread(rootId)])
  } catch (error) {
    commentErrorText.value = error instanceof Error ? error.message : 'Failed to delete comment'
  }
}

watch(
  () => route.params.articleId,
  async () => {
    rootPage.value = 0
    Object.keys(threadMap).forEach((key) => {
      delete threadMap[Number(key)]
    })
    await refreshAll()
  },
  {
    immediate: true,
  },
)

watch(rootPage, loadRootCommentPage)
</script>

<template>
  <section class="detail-layout">
    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <article v-if="article" class="article-card">
      <img v-if="article.coverUrl" class="cover" :src="article.coverUrl" alt="cover" />
      <header class="article-head">
        <h1>{{ article.title }}</h1>
        <p>{{ article.summary || 'No summary.' }}</p>
        <div class="meta">
          <span>Author {{ article.userId }}</span>
          <span>Published {{ formatDateTime(article.publishedAt) }}</span>
          <span>Views {{ article.viewCount || 0 }}</span>
        </div>
        <div class="interactions">
          <button :disabled="interactionLoading" :class="{ active: article.liked }" @click="handleToggleLikeArticle">
            {{ article.liked ? 'Liked' : 'Like' }} ({{ article.likeCount || 0 }})
          </button>
          <button
            :disabled="interactionLoading"
            :class="{ active: article.favorited }"
            @click="handleToggleFavoriteArticle"
          >
            {{ article.favorited ? 'Favorited' : 'Favorite' }} ({{ article.favoriteCount || 0 }})
          </button>
        </div>
      </header>
      <div class="md-body" v-html="renderHtml" />
    </article>
    <div v-else-if="loadingArticle" class="panel">Loading article...</div>

    <section class="comment-panel">
      <header class="comment-head">
        <h2>Comments</h2>
        <span>Total {{ rootTotal }}</span>
      </header>

      <form class="comment-form" @submit.prevent="submitRootComment">
        <textarea
          v-model="rootCommentContent"
          rows="3"
          maxlength="3000"
          placeholder="Write your comment"
        />
        <button type="submit">Post Comment</button>
      </form>

      <p v-if="commentErrorText" class="error-text">{{ commentErrorText }}</p>
      <div v-if="loadingRootComments" class="panel">Loading comments...</div>
      <div v-else-if="rootComments.length === 0" class="panel">No comments yet.</div>
      <div v-else class="comment-list">
        <article v-for="root in rootComments" :key="root.commentId" class="comment-card">
          <header>
            <span>#{{ root.commentId }} by User {{ root.userId }}</span>
            <span>{{ formatDateTime(root.createdAt) }}</span>
          </header>
          <p>{{ root.content }}</p>
          <div class="comment-actions">
            <button :class="{ active: root.liked }" @click="handleToggleCommentLike(root.commentId)">
              {{ root.liked ? 'Unlike' : 'Like' }}
            </button>
            <button @click="prepareReply(root.commentId, root.commentId)">Reply</button>
            <button @click="toggleThread(root.commentId)">
              {{ ensureThreadState(root.commentId).open ? 'Hide replies' : 'Show replies' }}
            </button>
            <button
              v-if="authStore.state.userId === root.userId"
              class="danger"
              @click="handleDeleteComment(root.commentId, root.commentId)"
            >
              Delete
            </button>
          </div>

          <div v-if="ensureThreadState(root.commentId).open" class="thread-area">
            <p v-if="ensureThreadState(root.commentId).errorText" class="error-text">
              {{ ensureThreadState(root.commentId).errorText }}
            </p>
            <div v-if="ensureThreadState(root.commentId).loading">Loading replies...</div>
            <div v-else-if="ensureThreadState(root.commentId).records.length === 0" class="subtle">
              No replies yet.
            </div>
            <div v-else class="thread-list">
              <article
                v-for="reply in ensureThreadState(root.commentId).records"
                :key="reply.commentId"
                class="reply-card"
              >
                <header>
                  <span>#{{ reply.commentId }} by User {{ reply.userId }}</span>
                  <span>{{ formatDateTime(reply.createdAt) }}</span>
                </header>
                <p>{{ reply.content }}</p>
                <div class="comment-actions">
                  <button :class="{ active: reply.liked }" @click="handleToggleCommentLike(reply.commentId)">
                    {{ reply.liked ? 'Unlike' : 'Like' }}
                  </button>
                  <button @click="prepareReply(root.commentId, reply.commentId)">Reply</button>
                  <button
                    v-if="authStore.state.userId === reply.userId"
                    class="danger"
                    @click="handleDeleteComment(reply.commentId, root.commentId)"
                  >
                    Delete
                  </button>
                </div>
              </article>
            </div>

            <form class="reply-form" @submit.prevent="submitReply(root.commentId)">
              <textarea
                v-model="ensureThreadState(root.commentId).replyContent"
                rows="2"
                maxlength="3000"
                :placeholder="
                  ensureThreadState(root.commentId).replyParentId
                    ? `Reply to #${ensureThreadState(root.commentId).replyParentId}`
                    : 'Select a comment first'
                "
              />
              <button type="submit">Reply</button>
            </form>
          </div>
        </article>
      </div>

      <footer class="pager">
        <button :disabled="rootPage <= 0 || loadingRootComments" @click="rootPage -= 1">Previous</button>
        <span>Page {{ rootPage + 1 }} / {{ Math.max(1, Math.ceil(rootTotal / rootSize)) }}</span>
        <button :disabled="loadingRootComments || (rootPage + 1) * rootSize >= rootTotal" @click="rootPage += 1">
          Next
        </button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.detail-layout {
  display: grid;
  gap: 14px;
}

.article-card,
.comment-panel,
.panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
}

.article-card {
  overflow: hidden;
}

.cover {
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  border-bottom: 1px solid var(--line-soft);
}

.article-head {
  padding: 16px 18px 6px;
}

.article-head h1 {
  margin: 0;
  color: var(--ink-strong);
}

.article-head p {
  margin: 8px 0 0;
  color: var(--ink-main);
}

.meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.interactions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.interactions button,
.comment-actions button,
.comment-form button,
.reply-form button,
.pager button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 7px 12px;
  cursor: pointer;
}

.interactions button.active,
.comment-actions button.active {
  border-color: rgba(20, 88, 166, 0.45);
  color: var(--brand);
}

.comment-actions button.danger {
  border-color: rgba(195, 57, 42, 0.35);
  color: var(--danger);
}

.md-body {
  padding: 8px 18px 18px;
  color: var(--ink-main);
  line-height: 1.75;
}

.md-body :deep(pre) {
  background: #121821;
  color: #f2f8ff;
  border-radius: 10px;
  padding: 12px;
  overflow: auto;
}

.comment-panel {
  padding: 14px;
  display: grid;
  gap: 12px;
}

.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.comment-head h2 {
  margin: 0;
}

.comment-form,
.reply-form {
  display: grid;
  gap: 8px;
}

.comment-form textarea,
.reply-form textarea {
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 10px;
  resize: vertical;
}

.comment-list {
  display: grid;
  gap: 10px;
}

.comment-card {
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 10px 12px;
  display: grid;
  gap: 8px;
}

.comment-card > header,
.reply-card > header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  color: var(--ink-muted);
  font-size: 0.84rem;
}

.comment-card p,
.reply-card p {
  margin: 0;
  line-height: 1.6;
}

.comment-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.thread-area {
  border-top: 1px dashed var(--line-soft);
  padding-top: 10px;
  display: grid;
  gap: 8px;
}

.thread-list {
  display: grid;
  gap: 8px;
}

.reply-card {
  border: 1px solid rgba(20, 88, 166, 0.2);
  border-radius: 10px;
  background: rgba(20, 88, 166, 0.04);
  padding: 8px 10px;
  display: grid;
  gap: 8px;
}

.subtle {
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.panel {
  padding: 14px;
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
}

.pager button:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
