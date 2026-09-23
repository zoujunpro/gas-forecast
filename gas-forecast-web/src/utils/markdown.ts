import DOMPurify from 'dompurify'
import { marked } from 'marked'

export const sanitizeHtml = (source: string): string =>
  DOMPurify.sanitize(source, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'iframe', 'object', 'embed', 'form'],
    FORBID_ATTR: ['style']
  })

export const renderMarkdown = (source: string): string => sanitizeHtml(marked(source, { async: false }))
