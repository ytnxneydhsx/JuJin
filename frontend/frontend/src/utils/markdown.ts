import DOMPurify from 'dompurify'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true,
})

function normalizeMarkdownSyntax(markdown: string) {
  return markdown
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    .replace(/^([*-])([^\s*-])/gm, '$1 $2')
    .replace(/^(\d+\.)([^\s])/gm, '$1 $2')
    .replace(/^(>)([^\s>])/gm, '$1 $2')
}

export function renderMarkdownToSafeHtml(markdown: string) {
  const normalized = normalizeMarkdownSyntax(markdown || '')
  const parsed = marked.parse(normalized, { async: false })
  const html = typeof parsed === 'string' ? parsed : ''
  return DOMPurify.sanitize(html)
}
