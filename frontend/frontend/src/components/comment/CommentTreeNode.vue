<script setup lang="ts">
import { computed } from 'vue'
import type { ArticleCommentVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'

defineOptions({
  name: 'CommentTreeNode',
})

interface CommentChildState {
  open: boolean
  loading: boolean
  loaded: boolean
  page: number
  size: number
  total: number
  hasNext: boolean
  errorText: string
}

const props = withDefaults(
  defineProps<{
    comment: ArticleCommentVO
    level?: number
    authUserId?: number | null
    commentStates: Record<number, CommentChildState>
  }>(),
  {
    level: 0,
    authUserId: null,
  },
)

const emit = defineEmits<{
  (e: 'toggle-like', commentId: number): void
  (e: 'prepare-reply', payload: { rootId: number; parentId: number }): void
  (e: 'delete-comment', comment: ArticleCommentVO): void
  (e: 'toggle-children', comment: ArticleCommentVO): void
  (e: 'load-more-children', comment: ArticleCommentVO): void
}>()

const currentState = computed(() => props.commentStates[props.comment.commentId])
const canExpand = computed(() => props.comment.childCount > 0)
const isOpen = computed(() => currentState.value?.open === true)
const showLoadMore = computed(() => isOpen.value && currentState.value?.hasNext === true)
const toggleText = computed(() => {
  if (!canExpand.value) {
    return ''
  }
  if (isOpen.value) {
    return '收起回复'
  }
  return `查看回复 ${props.comment.childCount}`
})
const avatarLabel = computed(() => `U${String(props.comment.userId).slice(-2)}`)
</script>

<template>
  <article class="comment-node" :class="{ nested: level > 0 }">
    <div class="comment-main">
      <div class="avatar">{{ avatarLabel }}</div>

      <div class="comment-body">
        <header class="comment-header">
          <div class="comment-userline">
            <strong>User {{ comment.userId }}</strong>
            <span v-if="level === 0" class="author-tag">评论</span>
            <span v-if="comment.replyToUserId" class="reply-tag">
              回复 User {{ comment.replyToUserId }}
            </span>
          </div>
          <span class="comment-time">{{ formatDateTime(comment.createdAt) }}</span>
        </header>

        <p class="comment-content">{{ comment.content }}</p>

        <div class="comment-actions">
          <button type="button" :class="{ active: comment.liked }" @click="emit('toggle-like', comment.commentId)">
            {{ comment.liked ? '已点赞' : '点赞' }}
          </button>
          <button
            type="button"
            @click="emit('prepare-reply', { rootId: comment.rootId, parentId: comment.commentId })"
          >
            回复
          </button>
          <button v-if="canExpand" type="button" @click="emit('toggle-children', comment)">
            {{ toggleText }}
          </button>
          <button
            v-if="authUserId === comment.userId"
            type="button"
            class="danger"
            @click="emit('delete-comment', comment)"
          >
            删除
          </button>
        </div>

        <p v-if="currentState?.errorText" class="error-text">{{ currentState.errorText }}</p>

        <div v-if="isOpen" class="comment-children">
          <div v-if="currentState?.loading && comment.children.length === 0" class="subtle">正在加载回复...</div>
          <div v-else-if="comment.children.length === 0" class="subtle">暂时还没有回复</div>
          <div v-else class="child-list">
            <CommentTreeNode
              v-for="child in comment.children"
              :key="child.commentId"
              :comment="child"
              :level="level + 1"
              :auth-user-id="authUserId"
              :comment-states="commentStates"
              @toggle-like="emit('toggle-like', $event)"
              @prepare-reply="emit('prepare-reply', $event)"
              @delete-comment="emit('delete-comment', $event)"
              @toggle-children="emit('toggle-children', $event)"
              @load-more-children="emit('load-more-children', $event)"
            />
          </div>

          <div v-if="currentState?.loading && comment.children.length > 0" class="subtle">正在加载更多回复...</div>
          <button v-if="showLoadMore" type="button" class="load-more" @click="emit('load-more-children', comment)">
            加载更多回复
          </button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.comment-node {
  padding: 18px 0;
  border-bottom: 1px solid var(--line-soft);
}

.comment-node.nested {
  border-bottom: none;
  padding: 14px 0 0;
}

.comment-main {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1d4ed8;
  display: grid;
  place-items: center;
  font-size: 0.82rem;
  font-weight: 700;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-userline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.comment-userline strong {
  color: var(--ink-strong);
}

.author-tag,
.reply-tag {
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 0.78rem;
}

.author-tag {
  background: #eef5ff;
  color: var(--brand);
}

.reply-tag {
  background: #f7f8fa;
  color: var(--ink-muted);
}

.comment-time,
.subtle {
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.comment-content {
  margin: 0;
  color: var(--ink-main);
  line-height: 1.8;
  white-space: pre-wrap;
}

.comment-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.comment-actions button,
.load-more {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--ink-muted);
  cursor: pointer;
}

.comment-actions button.active {
  color: var(--brand);
}

.comment-actions button.danger {
  color: var(--danger);
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.comment-children {
  margin-top: 4px;
  padding-left: 18px;
  border-left: 2px solid #eef1f5;
  display: grid;
  gap: 4px;
}

.child-list {
  display: grid;
}

.load-more {
  margin-top: 6px;
  color: var(--brand);
  text-align: left;
}

@media (max-width: 640px) {
  .comment-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
