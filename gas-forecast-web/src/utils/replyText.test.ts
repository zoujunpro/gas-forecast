import { describe, it, expect } from 'vitest'
import { normalizeReply, replyFallback } from './replyText'
import { renderMarkdown } from './markdown'
describe('reply presentation', () => {
  it('renders serialized replies as real paragraphs and lists', () => {
    const source = JSON.stringify({ status: 'COMPLETED', message: '结论。\n\n1. 数据依据\n2. 使用边界' })
    const html = renderMarkdown(source)
    expect(html).toContain('<ol>')
    expect(html).not.toContain('status')
    expect(html).not.toContain('\\n')
  })
  it('does not render corrupted historical JSON fragments', () => {
    expect(normalizeReply('结论。\\n\\n1. 建议。"}}')).toBe(replyFallback)
    expect(normalizeReply('{"message":"内容')).toBe(replyFallback)
  })
  it('decodes only repeated escaped paragraph separators in prose', () => {
    expect(normalizeReply('结论。\\n\\n1. 数据依据')).toBe('结论。\n\n1. 数据依据')
    const code = '示例：`\\n`。\\n\\n路径'
    expect(normalizeReply(code)).toBe(code)
  })
})
