<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createDraft, getDraft, publishDraft, updateDraft, type SaveDraftPayload } from '@/api/draft'
import { uploadImage } from '@/api/upload'
import { renderMarkdownToSafeHtml } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()

const loadingDraft = ref(false)
const savingDraft = ref(false)
const publishing = ref(false)
const uploadingCover = ref(false)
const uploadingContentImage = ref(false)
const errorText = ref('')
const successText = ref('')
const currentDraftId = ref<number | null>(null)

const form = reactive({
  title: '',
  summary: '',
  coverUrl: '',
  content: '',
})

function parseDraftId(raw: unknown) {
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function toPayload(): SaveDraftPayload {
  const title = form.title.trim()
  const summary = form.summary.trim()
  const coverUrl = form.coverUrl.trim()
  return {
    title: title || undefined,
    summary: summary || undefined,
    coverUrl: coverUrl || undefined,
    content: form.content || undefined,
  }
}

async function loadDraft(draftId: number) {
  loadingDraft.value = true
  errorText.value = ''
  try {
    const draft = await getDraft(draftId)
    currentDraftId.value = draftId
    form.title = draft.title || ''
    form.summary = draft.summary || ''
    form.coverUrl = draft.coverUrl || ''
    form.content = draft.content || ''
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to load draft'
  } finally {
    loadingDraft.value = false
  }
}

async function syncDraftFromRoute() {
  const draftId = parseDraftId(route.query.draftId)
  if (draftId === null) {
    currentDraftId.value = null
    return
  }
  await loadDraft(draftId)
}

watch(
  () => route.query.draftId,
  async () => {
    await syncDraftFromRoute()
  },
  {
    immediate: true,
  },
)

async function save() {
  savingDraft.value = true
  successText.value = ''
  errorText.value = ''
  try {
    const payload = toPayload()
    if (currentDraftId.value === null) {
      const data = await createDraft(payload)
      currentDraftId.value = data.draftId
      await router.replace({
        path: '/write',
        query: {
          draftId: String(data.draftId),
        },
      })
      successText.value = `Draft #${data.draftId} created`
      return
    }
    await updateDraft(currentDraftId.value, payload)
    successText.value = `Draft #${currentDraftId.value} saved`
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to save draft'
  } finally {
    savingDraft.value = false
  }
}

async function publish() {
  publishing.value = true
  successText.value = ''
  errorText.value = ''
  try {
    if (currentDraftId.value === null) {
      const payload = toPayload()
      const created = await createDraft(payload)
      currentDraftId.value = created.draftId
    } else {
      await updateDraft(currentDraftId.value, toPayload())
    }
    const data = await publishDraft(currentDraftId.value)
    await router.push(`/article/${data.articleId}`)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to publish draft'
  } finally {
    publishing.value = false
  }
}

async function handleCoverFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploadingCover.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const uploaded = await uploadImage('article_cover', file)
    form.coverUrl = uploaded.url
    successText.value = 'Cover uploaded'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to upload cover'
  } finally {
    uploadingCover.value = false
    input.value = ''
  }
}

async function handleContentImageFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploadingContentImage.value = true
  errorText.value = ''
  successText.value = ''
  try {
    const uploaded = await uploadImage('article_content', file)
    const markdownImage = `\n![image](${uploaded.url})\n`
    form.content += markdownImage
    successText.value = 'Image uploaded and inserted into markdown'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : 'Failed to upload content image'
  } finally {
    uploadingContentImage.value = false
    input.value = ''
  }
}

const renderHtml = computed(() => {
  return renderMarkdownToSafeHtml(form.content || '')
})
</script>

<template>
  <section class="writer-layout">
    <header class="writer-head">
      <div>
        <h1>Write Article</h1>
        <p>Draft first, publish after preview. Markdown is supported.</p>
      </div>
      <div class="head-actions">
        <button class="ghost-btn" @click="router.push('/drafts')">Draft Box</button>
        <button class="main-btn" :disabled="savingDraft || loadingDraft" @click="save">
          {{ savingDraft ? 'Saving...' : currentDraftId ? `Save #${currentDraftId}` : 'Save Draft' }}
        </button>
        <button class="accent-btn" :disabled="publishing || loadingDraft" @click="publish">
          {{ publishing ? 'Publishing...' : 'Publish' }}
        </button>
      </div>
    </header>

    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <div v-if="loadingDraft" class="panel">Loading draft...</div>
    <div v-else class="writer-grid">
      <form class="editor-form" @submit.prevent>
        <label class="field">
          <span>Title</span>
          <input v-model.trim="form.title" maxlength="200" placeholder="Input article title" />
        </label>

        <label class="field">
          <span>Summary</span>
          <textarea
            v-model.trim="form.summary"
            maxlength="500"
            rows="3"
            placeholder="Input summary, max 500 chars"
          />
        </label>

        <label class="field">
          <span>Cover URL</span>
          <input v-model.trim="form.coverUrl" maxlength="512" placeholder="https://..." />
          <div class="upload-row">
            <input
              type="file"
              accept="image/*"
              :disabled="uploadingCover"
              @change="handleCoverFileChange"
            />
            <span>{{ uploadingCover ? 'Uploading cover...' : 'Upload cover image' }}</span>
          </div>
        </label>

        <label class="field">
          <span>Content (Markdown)</span>
          <textarea
            v-model="form.content"
            rows="16"
            placeholder="# Start writing..."
          />
          <div class="upload-row">
            <input
              type="file"
              accept="image/*"
              :disabled="uploadingContentImage"
              @change="handleContentImageFileChange"
            />
            <span>{{ uploadingContentImage ? 'Uploading image...' : 'Upload image into content' }}</span>
          </div>
        </label>
      </form>

      <section class="preview-panel">
        <h2>Preview</h2>
        <img v-if="form.coverUrl" class="cover-preview" :src="form.coverUrl" alt="cover preview" />
        <h3>{{ form.title || 'Untitled article' }}</h3>
        <p class="preview-summary">{{ form.summary || 'No summary yet.' }}</p>
        <div class="md-preview" v-html="renderHtml" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.writer-layout {
  display: grid;
  gap: 12px;
}

.writer-head {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-lg);
  background: linear-gradient(128deg, #ffffff, #f4f8fd);
  padding: 20px 22px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.writer-head h1 {
  margin: 0;
  color: var(--ink-strong);
}

.writer-head p {
  margin: 6px 0 0;
  color: var(--ink-muted);
}

.head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ghost-btn,
.main-btn,
.accent-btn {
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  padding: 8px 14px;
  background: #fff;
  color: var(--ink-main);
  cursor: pointer;
}

.main-btn {
  border-color: rgba(20, 88, 166, 0.5);
  color: var(--brand);
}

.accent-btn {
  border-color: transparent;
  background: linear-gradient(140deg, #1458a6, #1d77d2);
  color: #fff;
}

.ghost-btn:disabled,
.main-btn:disabled,
.accent-btn:disabled {
  opacity: 0.6;
  cursor: default;
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

.writer-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.editor-form,
.preview-panel {
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-md);
  background: #fff;
  padding: 16px;
  display: grid;
  gap: 12px;
}

.preview-panel h2 {
  margin: 0;
  font-size: 1rem;
  color: var(--ink-muted);
}

.preview-panel h3 {
  margin: 0;
  color: var(--ink-strong);
}

.preview-summary {
  margin: 0;
  color: var(--ink-muted);
}

.field {
  display: grid;
  gap: 6px;
}

.field span {
  font-weight: 600;
  color: var(--ink-main);
}

.field input,
.field textarea {
  width: 100%;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  background: #fff;
  color: var(--ink-strong);
  padding: 10px 12px;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 3px rgba(20, 88, 166, 0.14);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-muted);
  font-size: 0.86rem;
}

.cover-preview {
  width: 100%;
  max-height: 240px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--line-soft);
}

.md-preview {
  color: var(--ink-main);
  line-height: 1.7;
  word-break: break-word;
}

.md-preview :deep(pre) {
  background: #121821;
  color: #f4f8ff;
  border-radius: 10px;
  padding: 12px;
  overflow: auto;
}

.md-preview :deep(code) {
  font-family: "Consolas", "JetBrains Mono", monospace;
}

@media (max-width: 980px) {
  .writer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
