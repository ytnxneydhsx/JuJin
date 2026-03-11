<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteDraft, listDrafts, publishDraft } from '@/api/draft'
import type { ArticleDraftVO } from '@/types/models'
import { formatDateTime } from '@/utils/date'

const router = useRouter()

const loading = ref(false)
const errorText = ref('')
const successText = ref('')
const page = ref(0)
const size = ref(10)
const total = ref(0)
const records = ref<ArticleDraftVO[]>([])

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await listDrafts(page.value, size.value)
    records.value = data.records
    total.value = data.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorText.value = error instanceof Error ? error.message : 'Failed to load drafts'
  } finally {
    loading.value = false
  }
}

async function handlePublish(draftId: number) {
  successText.value = ''
  errorText.value = ''
  try {
    const data = await publishDraft(draftId)
    successText.value = `Draft #${draftId} published as article #${data.articleId}`
    await load()
    await router.push(`/article/${data.articleId}`)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to publish draft'
  }
}

async function handleDelete(draftId: number) {
  const confirmed = window.confirm(`Delete draft #${draftId}?`)
  if (!confirmed) {
    return
  }
  successText.value = ''
  errorText.value = ''
  try {
    await deleteDraft(draftId)
    successText.value = `Draft #${draftId} deleted`
    if (records.value.length === 1 && page.value > 0) {
      page.value -= 1
    }
    await load()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to delete draft'
  }
}

function editDraft(draftId: number) {
  router.push({
    path: '/write',
    query: {
      draftId: String(draftId),
    },
  })
}

onMounted(load)
</script>

<template>
  <section class="draft-layout">
    <header class="draft-head">
      <div>
        <h1>Draft Box</h1>
        <p>Manage your unpublished drafts and continue writing anytime.</p>
      </div>
      <button class="new-btn" @click="router.push('/write')">New Draft</button>
    </header>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div v-if="loading" class="panel">Loading drafts...</div>
    <div v-else-if="records.length === 0" class="panel">No drafts found.</div>
    <div v-else class="draft-list">
      <article v-for="item in records" :key="item.draftId" class="draft-card">
        <header>
          <h2>{{ item.title || `Untitled Draft #${item.draftId}` }}</h2>
          <span>#{{ item.draftId }}</span>
        </header>
        <p>{{ item.summary || 'No summary provided.' }}</p>
        <footer class="meta">
          <span>Updated {{ formatDateTime(item.updatedAt) }}</span>
          <span>Created {{ formatDateTime(item.createdAt) }}</span>
        </footer>
        <div class="actions">
          <button @click="editDraft(item.draftId)">Edit</button>
          <button class="accent" @click="handlePublish(item.draftId)">Publish</button>
          <button class="danger" @click="handleDelete(item.draftId)">Delete</button>
        </div>
      </article>
    </div>

    <footer class="pager">
      <button :disabled="page <= 0 || loading" @click="page -= 1; load()">Previous</button>
      <span>Page {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
      <button :disabled="loading || (page + 1) * size >= total" @click="page += 1; load()">
        Next
      </button>
    </footer>
  </section>
</template>

<style scoped>
.draft-layout {
  display: grid;
  gap: 12px;
}

.draft-head {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: linear-gradient(130deg, #fff, #f5f8fd);
  padding: 20px 22px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.draft-head h1 {
  margin: 0;
  color: var(--ink-strong);
}

.draft-head p {
  margin: 6px 0 0;
  color: var(--ink-muted);
}

.new-btn {
  border: none;
  border-radius: 999px;
  padding: 9px 15px;
  background: linear-gradient(140deg, #1458a6, #1d77d2);
  color: #fff;
  cursor: pointer;
}

.error-text {
  margin: 0;
  color: var(--danger);
}

.success-text {
  margin: 0;
  color: var(--ok);
}

.panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 16px;
}

.draft-list {
  display: grid;
  gap: 10px;
}

.draft-card {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 14px 16px;
  display: grid;
  gap: 8px;
}

.draft-card header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.draft-card h2 {
  margin: 0;
  font-size: 1.08rem;
  color: var(--ink-strong);
}

.draft-card p {
  margin: 0;
  color: var(--ink-main);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--ink-muted);
  font-size: 0.85rem;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.actions button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  color: var(--ink-main);
  padding: 7px 12px;
  cursor: pointer;
}

.actions .accent {
  border-color: rgba(20, 88, 166, 0.4);
  color: var(--brand);
}

.actions .danger {
  border-color: rgba(195, 57, 42, 0.35);
  color: var(--danger);
}

.pager {
  display: flex;
  gap: 10px;
  justify-content: center;
  align-items: center;
}

.pager button {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: #fff;
  padding: 7px 12px;
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>

