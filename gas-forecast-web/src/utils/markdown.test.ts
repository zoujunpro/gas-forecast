import { describe, expect, it } from 'vitest'
import { renderMarkdown, sanitizeHtml } from './markdown'

describe('markdown security', () => {
  it('renders supported markdown', () => {
    expect(renderMarkdown('**安全内容**')).toContain('<strong>安全内容</strong>')
  })

  it('removes executable HTML', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)"><script>alert(2)</script>')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('<script')
  })

  it('removes forbidden embeds and inline styles', () => {
    const html = sanitizeHtml('<iframe src="https://example.com"></iframe><p style="color:red">text</p>')
    expect(html).toBe('<p>text</p>')
  })
})
