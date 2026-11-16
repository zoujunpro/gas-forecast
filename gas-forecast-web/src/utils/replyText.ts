/** 历史消息的受控兼容解析；畸形协议不作为正文展示。 */
export const replyFallback = '这条回复的格式不完整，暂时无法展示。请重新提问，已生成的结果仍可查看。'
export function normalizeReply(source: string, depth = 0): string {
  if (depth >= 4) return replyFallback
  const text = source.trim()
  if (!text) return ''
  const candidate = text.replace(/^```json\s*|\s*```$/gi, '').trim()
  try {
    const parsed = JSON.parse(candidate)
    if (typeof parsed === 'string') return normalizeReply(parsed, depth + 1)
    if (parsed && !Array.isArray(parsed) && typeof parsed === 'object') {
      if (typeof parsed.message === 'string') return normalizeReply(parsed.message, depth + 1)
      return replyFallback
    }
    return replyFallback
  } catch { /* 普通自然语言无需JSON解析。 */ }
  if (/^\{/.test(candidate) || /["'](?:status|message|resultType|errorCode|success)["']\s*:/.test(candidate)
    || /["'}]{2,}\s*$/.test(candidate)
    || /\b(?:COMPLETED|RUNNING|PENDING|NEED_CLARIFICATION|NEED_INFORMATION|FAILED)\b/.test(candidate)) return replyFallback
  // 只兼容明显的多段自然语言，不触碰代码、路径和单个转义示例。
  if (!text.includes('`') && !text.includes('\\\\') && (text.match(/\\n/g) || []).length >= 2
    && /[。！？；]/.test(text)) return text.replace(/\\r\\n|\\n/g, '\n')
  return text
}
