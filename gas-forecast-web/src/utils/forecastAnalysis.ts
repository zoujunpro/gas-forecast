/** 解读限定在约 100 个汉字，避免同一结论在页面两处铺成长文。 */
export const compactForecastAnalysis = (value: unknown, limit = 110) => {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (text.length <= limit) return text
  const clipped = text.slice(0, limit)
  const sentenceEnd = Math.max(clipped.lastIndexOf('。'), clipped.lastIndexOf('！'), clipped.lastIndexOf('？'))
  return (sentenceEnd >= Math.floor(limit * 0.6) ? clipped.slice(0, sentenceEnd + 1) : clipped).trim()
}

/** 只展示本次预测由 AI 生成并落库的三个追问；无有效结果时不伪造固定问题。 */
export const forecastAnalysisQuestions = (value?: unknown): string[] => {
  try {
    const parsed = Array.isArray(value) ? value : JSON.parse(String(value || '[]'))
    if (!Array.isArray(parsed)) return []
    const questions = parsed
      .map((question: unknown) => typeof question === 'string' ? question : String((question as Record<string, unknown>)?.text || ''))
      .map((question: string) => question.replace(/\s+/g, ' ').trim())
      .filter(Boolean)
    if (questions.length !== 3 || new Set(questions).size !== 3 || questions.some((question) => question.length > 30)) {
      return []
    }
    return questions
  } catch {
    return []
  }
}
