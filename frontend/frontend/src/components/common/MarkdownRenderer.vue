<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { renderMarkdownToSafeHtml } from '@/utils/markdown'

const props = defineProps<{
  source: string
}>()

type MermaidModule = typeof import('mermaid')
type MermaidApi = MermaidModule['default']

let mermaidInitialized = false
let mermaidModulePromise: Promise<MermaidModule> | null = null

const containerRef = ref<HTMLElement | null>(null)
const renderedHtml = computed(() => renderMarkdownToSafeHtml(props.source || ''))

let renderSequence = 0

function ensureMermaidInitialized(mermaid: MermaidApi) {
  if (mermaidInitialized) {
    return
  }

  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme: 'neutral',
  })
  mermaidInitialized = true
}

function loadMermaid() {
  if (!mermaidModulePromise) {
    mermaidModulePromise = import('mermaid')
  }
  return mermaidModulePromise
}

async function renderMermaidDiagrams() {
  renderSequence += 1
  const currentSequence = renderSequence

  await nextTick()
  const container = containerRef.value
  if (!container) {
    return
  }

  const mermaidBlocks = Array.from(container.querySelectorAll('pre code.language-mermaid'))
  if (mermaidBlocks.length === 0) {
    return
  }

  const mermaidModule = await loadMermaid()
  const mermaid = mermaidModule.default
  ensureMermaidInitialized(mermaid)

  await Promise.all(
    mermaidBlocks.map(async (codeBlock, index) => {
      if (currentSequence !== renderSequence) {
        return
      }

      const source = codeBlock.textContent?.trim() || ''
      const preElement = codeBlock.parentElement
      if (!preElement || !source) {
        return
      }

      const host = document.createElement('div')
      host.className = 'md-mermaid'
      preElement.replaceWith(host)

      try {
        const diagramId = `md-mermaid-${currentSequence}-${index}`
        const { svg } = await mermaid.render(diagramId, source)
        if (currentSequence !== renderSequence) {
          return
        }
        host.innerHTML = svg
      } catch {
        host.classList.add('md-mermaid-error')
        host.innerHTML = `
          <p>Mermaid render failed.</p>
          <pre class="md-mermaid-source"></pre>
        `
        const sourceElement = host.querySelector('.md-mermaid-source')
        if (sourceElement) {
          sourceElement.textContent = source
        }
      }
    }),
  )
}

watch(renderedHtml, () => {
  void renderMermaidDiagrams()
})

onMounted(() => {
  void renderMermaidDiagrams()
})
</script>

<template>
  <div ref="containerRef" class="markdown-renderer" v-html="renderedHtml" />
</template>

<style scoped>
.markdown-renderer {
  color: inherit;
  line-height: inherit;
  word-break: break-word;
}

.markdown-renderer :deep(.katex-display) {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.2rem 0;
}

.markdown-renderer :deep(.md-mermaid) {
  display: grid;
  place-items: center;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: linear-gradient(180deg, #fff, #f8fafc);
  overflow-x: auto;
}

.markdown-renderer :deep(.md-mermaid svg) {
  max-width: 100%;
  height: auto;
}

.markdown-renderer :deep(.md-mermaid-error) {
  justify-items: stretch;
  gap: 10px;
  color: var(--danger);
}

.markdown-renderer :deep(.md-mermaid-error p) {
  margin: 0;
  font-weight: 600;
}

.markdown-renderer :deep(.md-mermaid-source),
.markdown-renderer :deep(.md-math-fallback) {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: #121821;
  color: #f4f8ff;
  overflow-x: auto;
}
</style>
