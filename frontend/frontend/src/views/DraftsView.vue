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
    errorText.value = error instanceof Error ? error.message : '加载草稿失败'
  } finally {
    loading.value = false
  }
}

async function handlePublish(draftId: number) {
  successText.value = ''
  errorText.value = ''
  try {
    const data = await publishDraft(draftId)
    successText.value = `草稿 #${draftId} 已发布`
    await load()
    await router.push(`/article/${data.articleId}`)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '发布草稿失败'
  }
}

async function handleDelete(draftId: number) {
  const confirmed = window.confirm(`删除草稿 #${draftId}？`)
  if (!confirmed) {
    return
  }
  successText.value = ''
  errorText.value = ''
  try {
    await deleteDraft(draftId)
    successText.value = `草稿 #${draftId} 已删除`
    if (records.value.length === 1 && page.value > 0) {
      page.value -= 1
    }
    await load()
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '删除草稿失败'
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
        <p class="section-label">创作台</p>
        <h1>草稿箱</h1>
        <p class="subtitle">未发布内容都会保存在这里，方便你随时回来继续完善。</p>
      </div>
      <button class="primary-btn" @click="router.push('/write')">新建草稿</button>
    </header>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div v-if="loading" class="panel">正在加载草稿...</div>
    <div v-else-if="records.length === 0" class="panel">还没有草稿，去写第一篇吧。</div>
    <div v-else class="draft-list">
      <article v-for="item in records" :key="item.draftId" class="draft-card">
        <div class="draft-copy">
          <div class="draft-title-row">
            <h2>{{ item.title || `未命名草稿 #${item.draftId}` }}</h2>
            <span>#{{ item.draftId }}</span>
          </div>
          <p>{{ item.summary || '这篇草稿还没有摘要。' }}</p>
          <footer>
            <span>更新时间 {{ formatDateTime(item.updatedAt) }}</span>
            <span>创建时间 {{ formatDateTime(item.createdAt) }}</span>
          </footer>
        </div>
        <div class="draft-actions">
          <button class="ghost-btn" @click="editDraft(item.draftId)">继续编辑</button>
          <button class="primary-btn" @click="handlePublish(item.draftId)">发布</button>
          <button class="danger-btn" @click="handleDelete(item.draftId)">删除</button>
        </div>
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
.draft-layout { display: grid; gap: 16px; }
.draft-head, .panel, .draft-card { border: 1px solid var(--line-soft); border-radius: 18px; background: #fff; }
.draft-head { padding: 22px; display: flex; justify-content: space-between; gap: 16px; align-items: center; }
.section-label { margin: 0 0 8px; color: var(--brand); font-size: 0.85rem; font-weight: 700; }
.draft-head h1 { margin: 0; color: var(--ink-strong); }
.subtitle { margin: 8px 0 0; color: var(--ink-muted); }
.draft-list { display: grid; gap: 14px; }
.draft-card { padding: 18px 20px; display: flex; justify-content: space-between; gap: 16px; }
.draft-copy { display: grid; gap: 8px; }
.draft-title-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.draft-title-row h2 { margin: 0; color: var(--ink-strong); }
.draft-copy p, .draft-copy footer { margin: 0; }
.draft-copy p { color: var(--ink-main); line-height: 1.7; }
.draft-copy footer { display: flex; flex-wrap: wrap; gap: 12px; color: var(--ink-muted); font-size: 0.86rem; }
.draft-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: flex-end; }
.primary-btn, .ghost-btn, .danger-btn, .pager button { border: none; border-radius: 12px; padding: 10px 14px; cursor: pointer; font: inherit; }
.primary-btn { background: linear-gradient(135deg, #1e80ff, #5ea1ff); color: #fff; }
.ghost-btn, .pager button { background: #f4f7fb; color: var(--ink-main); }
.danger-btn { background: #fff2f0; color: var(--danger); }
.panel { padding: 18px; }
.pager { display: flex; justify-content: center; align-items: center; gap: 12px; }
.pager button:disabled { opacity: 0.5; cursor: default; }
.error-text, .success-text { margin: 0; }
.error-text { color: var(--danger); }
.success-text { color: var(--ok); }
@media (max-width: 900px) { .draft-head, .draft-card { flex-direction: column; align-items: flex-start; } }
</style>
