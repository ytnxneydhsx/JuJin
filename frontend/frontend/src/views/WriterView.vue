<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownRenderer from '@/components/common/MarkdownRenderer.vue'
import { createDraft, getDraft, publishDraft, updateDraft, type SaveDraftPayload } from '@/api/draft'
import { uploadImage } from '@/api/upload'

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
    errorText.value = error instanceof Error ? error.message : '加载草稿失败'
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
        query: { draftId: String(data.draftId) },
      })
      successText.value = `草稿 #${data.draftId} 已创建`
      return
    }
    await updateDraft(currentDraftId.value, payload)
    successText.value = `草稿 #${currentDraftId.value} 已保存`
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '保存草稿失败'
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
    errorText.value = error instanceof Error ? error.message : '发布失败'
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
    successText.value = '封面上传成功'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '封面上传失败'
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
    form.content += `\n![image](${uploaded.url})\n`
    successText.value = '图片已插入正文'
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '正文图片上传失败'
  } finally {
    uploadingContentImage.value = false
    input.value = ''
  }
}
</script>

<template>
  <section class="writer-layout">
    <p v-if="errorText" class="error-text">{{ errorText }}</p>
    <p v-if="successText" class="success-text">{{ successText }}</p>

    <header class="writer-head">
      <div class="writer-head-main">
        <input v-model.trim="form.title" class="title-input" maxlength="200" placeholder="输入文章标题..." />
        <div class="head-meta">
          <span>{{ currentDraftId ? `草稿 #${currentDraftId}` : '未保存草稿' }}</span>
          <span>支持 Markdown、公式和 Mermaid</span>
        </div>
      </div>
      <div class="head-actions">
        <button class="ghost-btn" @click="router.push('/drafts')">草稿箱</button>
        <button class="ghost-btn" :disabled="savingDraft || loadingDraft" @click="save">{{ savingDraft ? '保存中...' : '保存草稿' }}</button>
        <button class="primary-btn" :disabled="publishing || loadingDraft" @click="publish">{{ publishing ? '发布中...' : '发布' }}</button>
      </div>
    </header>

    <div v-if="loadingDraft" class="panel">正在加载草稿...</div>
    <div v-else class="editor-shell">
      <section class="editor-panel">
        <div class="editor-toolbar">
          <label class="toolbar-field">
            <span>摘要</span>
            <textarea v-model.trim="form.summary" maxlength="500" rows="3" placeholder="一句话概括文章内容" />
          </label>

          <label class="toolbar-field">
            <span>封面</span>
            <input v-model.trim="form.coverUrl" maxlength="512" placeholder="粘贴封面地址，或使用下方上传" />
            <input type="file" accept="image/*" :disabled="uploadingCover" @change="handleCoverFileChange" />
          </label>
        </div>

        <div class="editor-body">
          <textarea v-model="form.content" class="editor-textarea" placeholder="# 开始写作..." />
        </div>

        <div class="upload-row">
          <input type="file" accept="image/*" :disabled="uploadingContentImage" @change="handleContentImageFileChange" />
          <span>{{ uploadingContentImage ? '正在上传正文图片...' : '上传图片到正文' }}</span>
        </div>
      </section>

      <section class="preview-panel">
        <div class="preview-head">
          <p class="section-label">预览</p>
          <h2>{{ form.title || '未命名文章' }}</h2>
          <p>{{ form.summary || '这里会显示摘要预览。' }}</p>
        </div>
        <img v-if="form.coverUrl" class="cover-preview" :src="form.coverUrl" alt="cover preview" />
        <MarkdownRenderer class="md-preview" :source="form.content" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.writer-layout { display: grid; gap: 16px; }
.writer-head, .panel, .editor-panel, .preview-panel { border: 1px solid var(--line-soft); border-radius: 18px; background: #fff; }
.writer-head { padding: 18px 22px; display: flex; justify-content: space-between; gap: 16px; align-items: center; }
.writer-head-main { flex: 1; min-width: 0; }
.title-input { width: 100%; border: none; background: transparent; color: var(--ink-strong); font-size: clamp(1.4rem, 1.2rem + 0.9vw, 2rem); font-weight: 700; padding: 0; }
.title-input:focus { outline: none; }
.head-meta { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 12px; color: var(--ink-muted); font-size: 0.88rem; }
.head-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.ghost-btn, .primary-btn { border: none; border-radius: 12px; padding: 10px 14px; cursor: pointer; font: inherit; }
.ghost-btn { background: #f4f7fb; color: var(--ink-main); }
.primary-btn { background: linear-gradient(135deg, #1e80ff, #5ea1ff); color: #fff; }
.editor-shell { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }
.editor-panel, .preview-panel { padding: 18px; display: grid; gap: 16px; align-content: start; }
.editor-toolbar { display: grid; gap: 16px; }
.toolbar-field, .upload-row { display: grid; gap: 8px; }
.toolbar-field span, .section-label { color: var(--brand); font-size: 0.85rem; font-weight: 700; }
.toolbar-field input, .toolbar-field textarea, .editor-textarea { border: 1px solid var(--line-soft); border-radius: 12px; background: #f7f8fa; padding: 12px 14px; color: var(--ink-strong); }
.toolbar-field textarea, .editor-textarea { resize: vertical; }
.editor-body { min-height: 520px; }
.editor-textarea { width: 100%; min-height: 520px; height: 100%; font-family: "JetBrains Mono", "Consolas", monospace; line-height: 1.8; }
.preview-head h2 { margin: 8px 0 0; color: var(--ink-strong); }
.preview-head p:last-child { margin: 10px 0 0; color: var(--ink-muted); }
.cover-preview { width: 100%; max-height: 260px; border-radius: 16px; object-fit: cover; }
.md-preview { color: var(--ink-main); line-height: 1.9; }
.error-text, .success-text { margin: 0; }
.error-text { color: var(--danger); }
.success-text { color: var(--ok); }
.panel { padding: 18px; }
@media (max-width: 1040px) { .editor-shell { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .writer-head { flex-direction: column; align-items: stretch; } }
</style>
