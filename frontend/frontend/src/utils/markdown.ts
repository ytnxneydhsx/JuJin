import DOMPurify from 'dompurify'
import katex from 'katex'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true,
})

const CODE_SEGMENT_TOKEN_PREFIX = '%%MD_CODE_SEGMENT_'

interface MathSegment {
  displayMode: boolean
  expression: string
}

function normalizeMarkdownSyntax(markdown: string) {
  return markdown
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    .replace(/^([*-])([^\s*-])/gm, '$1 $2')
    .replace(/^(\d+\.)([^\s])/gm, '$1 $2')
    .replace(/^(>)([^\s>])/gm, '$1 $2')
}

function protectCodeSegments(markdown: string) {
  const segments: string[] = []
  let protectedMarkdown = markdown.replace(
    /(^|\n)(```[\s\S]*?```|~~~[\s\S]*?~~~)(?=\n|$)/g,
    (_match, leading: string, block: string) => {
      const token = `${CODE_SEGMENT_TOKEN_PREFIX}${segments.length}%%`
      segments.push(block)
      return `${leading}${token}`
    },
  )

  protectedMarkdown = protectedMarkdown.replace(/`[^`\n]+`/g, (inlineCode) => {
    const token = `${CODE_SEGMENT_TOKEN_PREFIX}${segments.length}%%`
    segments.push(inlineCode)
    return token
  })

  return {
    protectedMarkdown,
    segments,
  }
}

function restoreCodeSegments(markdown: string, segments: string[]) {
  return markdown.replace(/%%MD_CODE_SEGMENT_(\d+)%%/g, (_match, indexText: string) => {
    const index = Number(indexText)
    return segments[index] ?? ''
  })
}

function protectMathSegments(markdown: string) {
  const segments: MathSegment[] = []
  let protectedMarkdown = markdown.replace(
    /(?<!\\)\$\$([\s\S]+?)(?<!\\)\$\$/g,
    (_match, expression: string) => {
      const trimmed = expression.trim()
      if (!trimmed) {
        return _match
      }
      const index = segments.length
      segments.push({
        displayMode: true,
        expression: trimmed,
      })
      return `\n<div class="md-katex-placeholder" data-md-math="${index}" data-display-mode="block"></div>\n`
    },
  )

  protectedMarkdown = replaceInlineMath(protectedMarkdown, segments)
  return {
    protectedMarkdown,
    segments,
  }
}

function replaceInlineMath(markdown: string, segments: MathSegment[]) {
  let result = ''
  let index = 0

  while (index < markdown.length) {
    const char = markdown[index]
    if (char === '$' && markdown[index - 1] !== '\\') {
      let closingIndex = index + 1
      while (closingIndex < markdown.length) {
        if (markdown[closingIndex] === '\n') {
          closingIndex = -1
          break
        }
        if (markdown[closingIndex] === '$' && markdown[closingIndex - 1] !== '\\') {
          break
        }
        closingIndex += 1
      }

      const isClosed = closingIndex > index && closingIndex < markdown.length
      if (isClosed) {
        const expression = markdown.slice(index + 1, closingIndex).trim()
        if (
          expression
          && !expression.startsWith(' ')
          && !expression.endsWith(' ')
        ) {
          const segmentIndex = segments.length
          segments.push({
            displayMode: false,
            expression,
          })
          result += `<span class="md-katex-placeholder" data-md-math="${segmentIndex}" data-display-mode="inline"></span>`
          index = closingIndex + 1
          continue
        }
      }
    }

    result += char
    index += 1
  }

  return result
}

function renderMathSegments(html: string, segments: MathSegment[]) {
  return html.replace(
    /<(span|div) class="md-katex-placeholder" data-md-math="(\d+)" data-display-mode="(inline|block)"><\/\1>/g,
    (_match, _tagName: string, indexText: string, displayModeText: string) => {
      const segment = segments[Number(indexText)]
      if (!segment) {
        return ''
      }

      try {
        return katex.renderToString(segment.expression, {
          displayMode: displayModeText === 'block',
          throwOnError: false,
        })
      } catch {
        const escapedExpression = escapeHtml(segment.expression)
        if (segment.displayMode) {
          return `<pre class="md-math-fallback">${escapedExpression}</pre>`
        }
        return `<code class="md-math-fallback">${escapedExpression}</code>`
      }
    },
  )
}

function escapeHtml(text: string) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function renderMarkdownToSafeHtml(markdown: string) {
  const normalized = normalizeMarkdownSyntax(markdown || '')
  const { protectedMarkdown: codeProtectedMarkdown, segments: codeSegments } = protectCodeSegments(normalized)
  const { protectedMarkdown: mathProtectedMarkdown, segments: mathSegments } = protectMathSegments(codeProtectedMarkdown)
  const restoredMarkdown = restoreCodeSegments(mathProtectedMarkdown, codeSegments)
  const parsed = marked.parse(restoredMarkdown, { async: false })
  const html = typeof parsed === 'string' ? parsed : ''
  const renderedHtml = renderMathSegments(html, mathSegments)

  return DOMPurify.sanitize(renderedHtml, {
    USE_PROFILES: {
      html: true,
      mathMl: true,
      svg: true,
    },
  })
}
