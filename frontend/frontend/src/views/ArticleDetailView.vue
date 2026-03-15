<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommentTreeNode from '@/components/comment/CommentTreeNode.vue'
import MarkdownRenderer from '@/components/common/MarkdownRenderer.vue'
import { getArticle, toggleFavoriteArticle, toggleLikeArticle } from '@/api/article'
import {
  createComment,
  deleteComment,
  listChildComments,
  listRootComments,
  toggleCommentLike,
} from '@/api/comment'
import { getUserProfile } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { ArticleCommentVO, ArticleDetailVO, UserPublicProfileVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'

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

interface OutlineItem {
  id: string
  text: string
  level: number
}

const COMMENT_CHILD_PAGE_SIZE = 5

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const article = ref<ArticleDetailVO | null>(null)
const authorProfile = ref<UserPublicProfileVO | null>(null)
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
const outlineItems = ref<OutlineItem[]>([])
const articleBodyRef = ref<HTMLElement | null>(null)
const commentPanelRef = ref<HTMLElement | null>(null)

const articleId = computed(() => {
  const parsed = Number(route.params.articleId)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
})

const authorAvatarLabel = computed(() => {
  const source = authorProfile.value?.name || String(article.value?.userId || '')
  return source.slice(0, 1).toUpperCase() || 'A'
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
    errorText.value = '无效的文章编号'
    return
  }
  loadingArticle.value = true
  errorText.value = ''
  try {
    article.value = await getArticle(articleId.value)
    await loadAuthorProfile()
  } catch (error) {
    article.value = null
    authorProfile.value = null
    errorText.value = error instanceof Error ? error.message : '加载文章失败'
  } finally {
    loadingArticle.value = false
  }
}

async function loadAuthorProfile() {
  if (!article.value) {
    authorProfile.value = null
    return
  }

  try {
    authorProfile.value = await getUserProfile(article.value.userId)
  } catch {
    authorProfile.value = null
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
    commentErrorText.value = error instanceof Error ? error.message : '加载评论失败'
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
    errorText.value = error instanceof Error ? error.message : '点赞失败'
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
    errorText.value = error instanceof Error ? error.message : '收藏失败'
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
    commentErrorText.value = '评论内容不能为空'
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
    commentErrorText.value = error instanceof Error ? error.message : '发表评论失败'
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
    state.errorText = error instanceof Error ? error.message : '加载回复失败'
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
    state.errorText = '回复内容不能为空'
    return
  }
  if (state.parentId === null) {
    state.errorText = '请选择要回复的评论'
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
    state.errorText = error instanceof Error ? error.message : '回复失败'
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
    commentErrorText.value = error instanceof Error ? error.message : '评论点赞失败'
  }
}

async function handleDeleteComment(comment: ArticleCommentVO) {
  if (!requireLoginOrRedirect()) {
    return
  }
  const confirmed = window.confirm(`删除评论 #${comment.commentId}？`)
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
    commentErrorText.value = error instanceof Error ? error.message : '删除评论失败'
  }
}

function scrollToComments() {
  commentPanelRef.value?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

function scrollToTop() {
  window.scrollTo({
    top: 0,
    behavior: 'smooth',
  })
}

function slugifyHeading(text: string) {
  return text
    .trim()
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fa5\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
}

async function rebuildOutline() {
  await nextTick()
  const body = articleBodyRef.value
  if (!body) {
    outlineItems.value = []
    return
  }

  const headingElements = Array.from(body.querySelectorAll('h1, h2, h3'))
  const duplicateCounter = new Map<string, number>()
  outlineItems.value = headingElements.map((element) => {
    const text = element.textContent?.trim() || '章节'
    const baseId = slugifyHeading(text) || 'section'
    const duplicate = duplicateCounter.get(baseId) ?? 0
    duplicateCounter.set(baseId, duplicate + 1)
    const id = duplicate === 0 ? baseId : `${baseId}-${duplicate}`
    element.id = id
    return {
      id,
      text,
      level: Number(element.tagName.replace('H', '')),
    }
  })
}

function scrollToOutline(item: OutlineItem) {
  document.getElementById(item.id)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

watch(
  () => route.params.articleId,
  async () => {
    rootPage.value = 0
    resetCommentStateMaps()
    outlineItems.value = []
    await refreshAll()
    await rebuildOutline()
  },
  {
    immediate: true,
  },
)

watch(rootPage, loadRootCommentPage)
watch(
  () => article.value?.content,
  () => {
    void rebuildOutline()
  },
)
</script>

<template>
  <section class="detail-layout">
    <p v-if="errorText" class="error-text">{{ errorText }}</p>

    <div class="detail-grid">
      <aside v-if="article" class="action-rail">
        <button
          class="rail-btn"
          :class="{ active: article.liked }"
          :disabled="interactionLoading"
          @click="handleToggleLikeArticle"
        >
          <strong>{{ article.likeCount || 0 }}</strong>
          <span>{{ article.liked ? '已赞' : '点赞' }}</span>
        </button>
        <button class="rail-btn" @click="scrollToComments">
          <strong>{{ rootTotal }}</strong>
          <span>评论</span>
        </button>
        <button
          class="rail-btn"
          :class="{ active: article.favorited }"
          :disabled="interactionLoading"
          @click="handleToggleFavoriteArticle"
        >
          <strong>{{ article.favoriteCount || 0 }}</strong>
          <span>{{ article.favorited ? '已藏' : '收藏' }}</span>
        </button>
        <button class="rail-btn" @click="scrollToTop">
          <strong>{{ article.viewCount || 0 }}</strong>
          <span>阅读</span>
        </button>
      </aside>

      <main class="article-main">
        <article v-if="article" class="article-card">
          <img v-if="article.coverUrl" class="cover" :src="article.coverUrl" alt="cover" />
          <header class="article-head">
            <div class="article-meta-line">
              <button class="author-jump" @click="router.push({ path: '/', query: { userId: String(article.userId) } })">
                作者 {{ article.userId }}
              </button>
              <span>{{ formatDateTime(article.publishedAt) }}</span>
              <span>{{ article.viewCount || 0 }} 次阅读</span>
            </div>
            <h1>{{ article.title }}</h1>
            <p class="summary">{{ article.summary || '作者没有填写文章摘要。' }}</p>
          </header>
          <div ref="articleBodyRef" class="article-body-shell">
            <MarkdownRenderer class="md-body" :source="article.content" />
          </div>
        </article>
        <div v-else-if="loadingArticle" class="panel">正在加载文章...</div>

        <section ref="commentPanelRef" class="comment-panel">
          <header class="comment-head">
            <div>
              <h2>评论 {{ rootTotal }}</h2>
              <p>支持楼层回复与评论点赞</p>
            </div>
          </header>

          <form class="comment-form" @submit.prevent="submitRootComment">
            <textarea
              v-model="rootCommentContent"
              rows="4"
              maxlength="3000"
              placeholder="平等表达，友善交流"
            />
            <div class="comment-form-foot">
              <span>{{ rootCommentContent.length }}/3000</span>
              <button type="submit">发表评论</button>
            </div>
          </form>

          <p v-if="commentErrorText" class="error-text">{{ commentErrorText }}</p>
          <div v-if="loadingRootComments" class="panel panel-muted">正在加载评论...</div>
          <div v-else-if="rootComments.length === 0" class="panel panel-muted">还没有评论，来抢沙发吧。</div>
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
                  rows="3"
                  maxlength="3000"
                  :placeholder="
                    ensureRootReplyState(root.commentId).parentId
                      ? `回复 #${ensureRootReplyState(root.commentId).parentId}`
                      : '选择一条评论开始回复'
                  "
                />
                <div class="reply-form-actions">
                  <button type="submit">回复</button>
                  <button type="button" class="secondary" @click="clearReplyComposer(root.commentId)">取消</button>
                </div>
              </form>
            </article>
          </div>

          <footer class="pager">
            <button :disabled="rootPage <= 0 || loadingRootComments" @click="rootPage -= 1">上一页</button>
            <span>第 {{ rootPage + 1 }} / {{ Math.max(1, Math.ceil(rootTotal / rootSize)) }} 页</span>
            <button :disabled="loadingRootComments || (rootPage + 1) * rootSize >= rootTotal" @click="rootPage += 1">
              下一页
            </button>
          </footer>
        </section>
      </main>

      <aside class="article-side">
        <section class="side-card author-card">
          <div class="author-row">
            <div class="author-avatar">
              <img v-if="authorProfile?.avatarUrl" :src="authorProfile.avatarUrl" alt="author avatar" />
              <span v-else>{{ authorAvatarLabel }}</span>
            </div>
            <div>
              <h3>{{ authorProfile?.name || `User ${article?.userId || ''}` }}</h3>
              <p>{{ authorProfile?.sign || '这个作者暂时还没有留下签名。' }}</p>
            </div>
          </div>
          <button class="side-primary" @click="router.push({ path: '/', query: { userId: String(article?.userId || '') } })">
            查看作者文章
          </button>
        </section>

        <section v-if="outlineItems.length > 0" class="side-card outline-card">
          <h3>目录</h3>
          <button
            v-for="item in outlineItems"
            :key="item.id"
            class="outline-item"
            :class="`level-${item.level}`"
            @click="scrollToOutline(item)"
          >
            {{ item.text }}
          </button>
        </section>

        <section class="side-card stats-card">
          <h3>文章信息</h3>
          <dl>
            <div>
              <dt>发布时间</dt>
              <dd>{{ article ? formatDateTime(article.publishedAt) : '-' }}</dd>
            </div>
            <div>
              <dt>点赞</dt>
              <dd>{{ article?.likeCount || 0 }}</dd>
            </div>
            <div>
              <dt>收藏</dt>
              <dd>{{ article?.favoriteCount || 0 }}</dd>
            </div>
            <div>
              <dt>评论</dt>
              <dd>{{ rootTotal }}</dd>
            </div>
          </dl>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.detail-layout {
  display: grid;
  gap: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.action-rail,
.article-side {
  position: sticky;
  top: 92px;
}

.action-rail {
  display: grid;
  gap: 12px;
}

.rail-btn {
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  background: #fff;
  color: var(--ink-muted);
  display: grid;
  gap: 2px;
  justify-items: center;
  padding: 12px 8px;
  cursor: pointer;
}

.rail-btn strong {
  color: var(--ink-strong);
}

.rail-btn.active {
  border-color: rgba(30, 128, 255, 0.24);
  color: var(--brand);
}

.article-main,
.article-side {
  display: grid;
  gap: 16px;
}

.article-card,
.comment-panel,
.side-card,
.panel {
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: #fff;
}

.article-card {
  overflow: hidden;
}

.cover {
  width: 100%;
  max-height: 360px;
  object-fit: cover;
  border-bottom: 1px solid var(--line-soft);
}

.article-head {
  padding: 24px 28px 12px;
}

.article-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.author-jump {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--brand);
  cursor: pointer;
}

.article-head h1 {
  margin: 14px 0 10px;
  color: var(--ink-strong);
  font-size: clamp(1.8rem, 1.3rem + 1.6vw, 2.55rem);
  line-height: 1.28;
}

.summary {
  margin: 0;
  color: var(--ink-main);
  line-height: 1.8;
  font-size: 1rem;
}

.article-body-shell {
  padding: 0 28px 28px;
}

.md-body {
  color: var(--ink-main);
  line-height: 1.9;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  color: var(--ink-strong);
  margin-top: 1.8em;
  scroll-margin-top: 96px;
}

.md-body :deep(img) {
  max-width: 100%;
  border-radius: 16px;
}

.md-body :deep(blockquote) {
  margin: 1.2rem 0;
  padding: 12px 16px;
  border-left: 4px solid var(--brand);
  background: #f7fbff;
  color: var(--ink-main);
}

.comment-panel {
  padding: 22px 24px;
  display: grid;
  gap: 18px;
}

.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: end;
}

.comment-head h2 {
  margin: 0;
  color: var(--ink-strong);
}

.comment-head p {
  margin: 6px 0 0;
  color: var(--ink-muted);
}

.comment-form,
.reply-form {
  display: grid;
  gap: 10px;
}

.comment-form textarea,
.reply-form textarea {
  width: 100%;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  background: #f7f8fa;
  padding: 14px 16px;
  resize: vertical;
  color: var(--ink-strong);
}

.comment-form textarea:focus,
.reply-form textarea:focus {
  outline: none;
  border-color: rgba(30, 128, 255, 0.3);
  background: #fff;
}

.comment-form-foot,
.reply-form-actions,
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-form-foot span {
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.comment-form button,
.reply-form button,
.pager button,
.side-primary,
.outline-item {
  border: none;
  background: transparent;
  font: inherit;
}

.comment-form button,
.reply-form button,
.pager button,
.side-primary {
  border-radius: 12px;
  padding: 10px 16px;
  cursor: pointer;
}

.comment-form button,
.reply-form button,
.side-primary {
  background: linear-gradient(135deg, #1e80ff, #5ea1ff);
  color: #fff;
}

.reply-form .secondary {
  background: #f4f7fb;
  color: var(--ink-main);
}

.comment-list {
  display: grid;
}

.root-reply-form {
  margin-left: 54px;
  margin-top: -4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line-soft);
}

.panel {
  padding: 18px;
}

.panel-muted {
  background: #fafafa;
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.side-card {
  padding: 18px;
  display: grid;
  gap: 14px;
}

.author-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.author-avatar {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  overflow: hidden;
  background: linear-gradient(135deg, #f7d79b, #f39db9 55%, #93c7ff);
  color: #24324a;
  display: grid;
  place-items: center;
  font-weight: 700;
  flex-shrink: 0;
}

.author-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-card h3,
.outline-card h3,
.stats-card h3 {
  margin: 0;
  color: var(--ink-strong);
}

.author-card p {
  margin: 6px 0 0;
  color: var(--ink-muted);
  line-height: 1.6;
}

.outline-item {
  display: block;
  width: 100%;
  text-align: left;
  color: var(--ink-main);
  padding: 4px 0;
  cursor: pointer;
}

.outline-item.level-2 {
  padding-left: 12px;
}

.outline-item.level-3 {
  padding-left: 24px;
  color: var(--ink-muted);
}

.stats-card dl {
  margin: 0;
  display: grid;
  gap: 12px;
}

.stats-card dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.stats-card dt {
  color: var(--ink-muted);
}

.stats-card dd {
  margin: 0;
  color: var(--ink-strong);
  font-weight: 600;
}

.pager {
  justify-content: center;
}

.pager button {
  background: #fff;
  box-shadow: inset 0 0 0 1px var(--line-soft);
}

.pager button:disabled,
.rail-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

@media (max-width: 1180px) {
  .detail-grid {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .article-side {
    display: none;
  }
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .action-rail {
    position: static;
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .article-body-shell,
  .article-head {
    padding-inline: 18px;
  }

  .comment-panel {
    padding: 18px;
  }
}

@media (max-width: 640px) {
  .comment-form-foot,
  .reply-form-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .root-reply-form {
    margin-left: 0;
  }
}
</style>
