<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommentTreeNode from '@/components/comment/CommentTreeNode.vue'
import { getArticle, toggleFavoriteArticle, toggleLikeArticle } from '@/api/article'
import {
  createComment,
  deleteComment,
  listChildComments,
  listRootComments,
  toggleCommentLike,
} from '@/api/comment'
import { useAuthStore } from '@/stores/auth'
import type { ArticleCommentVO, ArticleDetailVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'
import { renderMarkdownToSafeHtml } from '@/utils/markdown'

interface CommentChildState {
  open: boolean
  loading: boolean
  loaded: boolean
  errorText: string
  page: number
  size: number
  total: number
  hasNext: boolean
}

interface RootReplyState {
  content: string
  parentId: number | null
  errorText: string
}

const COMMENT_CHILD_PAGE_SIZE = 5

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
const commentStateMap = reactive<Record<number, CommentChildState>>({})
const rootReplyMap = reactive<Record<number, RootReplyState>>({})

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
  if (!commentStateMap[rootId]) {
    commentStateMap[rootId] = {
      open: false,
      loading: false,
      loaded: false,
      errorText: '',
      page: -1,
      size: COMMENT_CHILD_PAGE_SIZE,
      total: 0,
      hasNext: false,
    }
  }
  return commentStateMap[rootId]
}

function ensureRootReplyState(rootId: number) {
  if (!rootReplyMap[rootId]) {
    rootReplyMap[rootId] = {
      content: '',
      parentId: null,
      errorText: '',
    }
  }
  return rootReplyMap[rootId]
}

function normalizeCommentTree(records: ArticleCommentVO[]): ArticleCommentVO[] {
  return records.map((item) => ({
    ...item,
    childCount: item.childCount ?? 0,
    children: normalizeCommentTree(item.children ?? []),
  }))
}

function resetCommentStateMaps() {
  Object.keys(commentStateMap).forEach((key) => {
    delete commentStateMap[Number(key)]
  })
  Object.keys(rootReplyMap).forEach((key) => {
    delete rootReplyMap[Number(key)]
  })
}

function seedLoadedChildStates(records: ArticleCommentVO[]) {
  records.forEach((item) => {
    const state = ensureThreadState(item.commentId)
    state.open = false
    state.loading = false
    state.loaded = false
    state.errorText = ''
    state.page = -1
    state.size = COMMENT_CHILD_PAGE_SIZE
    state.total = item.childCount
    state.hasNext = item.childCount > 0
    if (item.children.length > 0) {
      seedLoadedChildStates(item.children)
    }
  })
}

function seedRootStates(records: ArticleCommentVO[]) {
  resetCommentStateMaps()
  records.forEach((root) => {
    const state = ensureThreadState(root.commentId)
    state.open = root.children.length > 0
    state.loading = false
    state.loaded = root.children.length > 0 || root.childCount === 0
    state.errorText = ''
    state.page = root.children.length > 0 ? 0 : -1
    state.size = COMMENT_CHILD_PAGE_SIZE
    state.total = root.childCount
    state.hasNext = root.children.length < root.childCount
    ensureRootReplyState(root.commentId)
    seedLoadedChildStates(root.children)
  })
}

function findCommentById(records: ArticleCommentVO[], commentId: number): ArticleCommentVO | null {
  for (const item of records) {
    if (item.commentId === commentId) {
      return item
    }
    const found = findCommentById(item.children, commentId)
    if (found) {
      return found
    }
  }
  return null
}

function updateCommentById(
  records: ArticleCommentVO[],
  commentId: number,
  updater: (comment: ArticleCommentVO) => ArticleCommentVO,
): ArticleCommentVO[] {
  let changed = false
  const nextRecords = records.map((item) => {
    let nextItem = item
    if (item.commentId === commentId) {
      nextItem = updater(item)
      changed = true
    }
    if (nextItem.children.length > 0) {
      const nextChildren = updateCommentById(nextItem.children, commentId, updater)
      if (nextChildren !== nextItem.children) {
        nextItem = { ...nextItem, children: nextChildren }
        changed = true
      }
    }
    return nextItem
  })
  return changed ? nextRecords : records
}

function appendUniqueComments(existing: ArticleCommentVO[], incoming: ArticleCommentVO[]) {
  const merged = [...existing]
  const existingIds = new Set(existing.map((item) => item.commentId))
  incoming.forEach((item) => {
    if (!existingIds.has(item.commentId)) {
      merged.push(item)
    }
  })
  return merged
}

function shouldShowReplyComposer(rootId: number) {
  return ensureRootReplyState(rootId).parentId !== null
}

function clearReplyComposer(rootId: number) {
  const state = ensureRootReplyState(rootId)
  state.content = ''
  state.parentId = null
  state.errorText = ''
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
    rootComments.value = normalizeCommentTree(data.records)
    rootTotal.value = data.total
    seedRootStates(rootComments.value)
  } catch (error) {
    rootComments.value = []
    rootTotal.value = 0
    resetCommentStateMaps()
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

async function loadChildPage(comment: ArticleCommentVO, append = false) {
  if (articleId.value === null) {
    return
  }
  const state = ensureThreadState(comment.commentId)
  if (state.loading) {
    return
  }
  const nextPage = append && state.page >= 0 ? state.page + 1 : 0
  state.loading = true
  state.errorText = ''
  try {
    const data = await listChildComments(articleId.value, comment.commentId, {
      page: nextPage,
      size: state.size,
    })
    const loadedChildren = normalizeCommentTree(data.records)
    seedLoadedChildStates(loadedChildren)
    const currentComment = findCommentById(rootComments.value, comment.commentId)
    const nextChildren = append
      ? appendUniqueComments(currentComment?.children ?? [], loadedChildren)
      : loadedChildren
    rootComments.value = updateCommentById(rootComments.value, comment.commentId, (item) => ({
      ...item,
      childCount: data.total,
      children: nextChildren,
    }))
    state.open = true
    state.loaded = true
    state.page = data.page
    state.size = data.size
    state.total = data.total
    state.hasNext = data.hasNext
  } catch (error) {
    state.errorText = error instanceof Error ? error.message : 'Failed to load replies'
  } finally {
    state.loading = false
  }
}

async function toggleChildren(comment: ArticleCommentVO) {
  const state = ensureThreadState(comment.commentId)
  if (state.open) {
    state.open = false
    return
  }
  if (!state.loaded && comment.childCount > 0) {
    await loadChildPage(comment, false)
    return
  }
  state.open = true
}

async function loadMoreChildren(comment: ArticleCommentVO) {
  const state = ensureThreadState(comment.commentId)
  if (!state.hasNext || state.loading) {
    return
  }
  await loadChildPage(comment, true)
}

function prepareReply(payload: { rootId: number; parentId: number }) {
  const replyState = ensureRootReplyState(payload.rootId)
  replyState.parentId = payload.parentId
  replyState.errorText = ''
  ensureThreadState(payload.rootId).open = true
}

async function submitReply(root: ArticleCommentVO) {
  if (!requireLoginOrRedirect() || articleId.value === null) {
    return
  }
  const state = ensureRootReplyState(root.commentId)
  const content = state.content.trim()
  if (!content) {
    state.errorText = 'Reply cannot be empty'
    return
  }
  if (state.parentId === null) {
    state.errorText = 'Reply target is required'
    return
  }
  state.errorText = ''
  try {
    await createComment({
      articleId: articleId.value,
      parentId: state.parentId,
      content,
    })
    const replyParentId = state.parentId
    state.content = ''
    state.parentId = null
    rootComments.value = updateCommentById(rootComments.value, replyParentId, (item) => ({
      ...item,
      childCount: item.childCount + 1,
    }))
    const parentComment = findCommentById(rootComments.value, replyParentId)
    if (parentComment) {
      await loadChildPage(parentComment, false)
    } else {
      await loadRootCommentPage()
    }
  } catch (error) {
    state.errorText = error instanceof Error ? error.message : 'Failed to create reply'
  }
}

function patchCommentLiked(commentId: number, liked: boolean) {
  rootComments.value = updateCommentById(rootComments.value, commentId, (item) => ({
    ...item,
    liked,
  }))
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

async function handleDeleteComment(comment: ArticleCommentVO) {
  if (!requireLoginOrRedirect()) {
    return
  }
  const confirmed = window.confirm(`Delete comment #${comment.commentId}?`)
  if (!confirmed) {
    return
  }
  try {
    await deleteComment(comment.commentId)
    if (comment.parentId === null) {
      await loadRootCommentPage()
      return
    }
    rootComments.value = updateCommentById(rootComments.value, comment.parentId, (item) => ({
      ...item,
      childCount: Math.max(0, item.childCount - 1),
    }))
    const parentComment = findCommentById(rootComments.value, comment.parentId)
    if (parentComment) {
      await loadChildPage(parentComment, false)
      return
    }
    await loadRootCommentPage()
  } catch (error) {
    commentErrorText.value = error instanceof Error ? error.message : 'Failed to delete comment'
  }
}

watch(
  () => route.params.articleId,
  async () => {
    rootPage.value = 0
    resetCommentStateMaps()
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
        <article v-for="root in rootComments" :key="root.commentId" class="comment-thread">
          <CommentTreeNode
            :comment="root"
            :auth-user-id="authStore.state.userId"
            :comment-states="commentStateMap"
            @toggle-like="handleToggleCommentLike"
            @prepare-reply="prepareReply"
            @delete-comment="handleDeleteComment"
            @toggle-children="toggleChildren"
            @load-more-children="loadMoreChildren"
          />

          <form
            v-if="shouldShowReplyComposer(root.commentId)"
            class="reply-form root-reply-form"
            @submit.prevent="submitReply(root)"
          >
            <p v-if="ensureRootReplyState(root.commentId).errorText" class="error-text">
              {{ ensureRootReplyState(root.commentId).errorText }}
            </p>
            <textarea
              v-model="ensureRootReplyState(root.commentId).content"
              rows="2"
              maxlength="3000"
              :placeholder="
                ensureRootReplyState(root.commentId).parentId
                  ? `Reply to #${ensureRootReplyState(root.commentId).parentId}`
                  : 'Select a comment first'
              "
            />
            <div class="reply-form-actions">
              <button type="submit">Reply</button>
              <button type="button" class="secondary" @click="clearReplyComposer(root.commentId)">Cancel</button>
            </div>
          </form>
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

.comment-thread {
  display: grid;
  gap: 8px;
}

.subtle {
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.root-reply-form {
  margin-left: 16px;
}

.reply-form-actions {
  display: flex;
  gap: 8px;
}

.reply-form-actions .secondary {
  border-color: var(--line-soft);
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
