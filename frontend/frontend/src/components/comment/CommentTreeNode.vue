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
    return 'Hide replies'
  }
  return `Show replies (${props.comment.childCount})`
})
</script>

<template>
  <article class="comment-node" :class="{ nested: level > 0 }">
    <header>
      <span>#{{ comment.commentId }} by User {{ comment.userId }}</span>
      <span>{{ formatDateTime(comment.createdAt) }}</span>
    </header>

    <p>{{ comment.content }}</p>

    <div class="comment-actions">
      <button type="button" :class="{ active: comment.liked }" @click="emit('toggle-like', comment.commentId)">
        {{ comment.liked ? 'Unlike' : 'Like' }}
      </button>
      <button
        type="button"
        @click="emit('prepare-reply', { rootId: comment.rootId, parentId: comment.commentId })"
      >
        Reply
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
        Delete
      </button>
    </div>

    <p v-if="currentState?.errorText" class="error-text">{{ currentState.errorText }}</p>

    <div v-if="isOpen" class="comment-children">
      <div v-if="currentState?.loading && comment.children.length === 0" class="subtle">Loading replies...</div>
      <div v-else-if="comment.children.length === 0" class="subtle">No replies yet.</div>
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

      <div v-if="currentState?.loading && comment.children.length > 0" class="subtle">Loading more replies...</div>
      <button v-if="showLoadMore" type="button" class="load-more" @click="emit('load-more-children', comment)">
        Load more replies
      </button>
    </div>
  </article>
</template>

<style scoped>
.comment-node {
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
  display: grid;
  gap: 8px;
}

.comment-node.nested {
  border-color: rgba(20, 88, 166, 0.2);
  background: rgba(20, 88, 166, 0.04);
}

.comment-node > header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  color: var(--ink-muted);
  font-size: 0.84rem;
}

.comment-node p {
  margin: 0;
  line-height: 1.6;
}

.comment-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.comment-actions button,
.load-more {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 7px 12px;
  cursor: pointer;
}

.comment-actions button.active {
  border-color: rgba(20, 88, 166, 0.45);
  color: var(--brand);
}

.comment-actions button.danger {
  border-color: rgba(195, 57, 42, 0.35);
  color: var(--danger);
}

.comment-children {
  border-top: 1px dashed var(--line-soft);
  padding-top: 10px;
  display: grid;
  gap: 8px;
}

.child-list {
  display: grid;
  gap: 8px;
  padding-left: 14px;
}

.subtle {
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.error-text {
  margin: 0;
  color: var(--danger);
}
</style>
