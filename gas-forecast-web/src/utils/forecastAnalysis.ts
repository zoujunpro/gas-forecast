/** 预测页面和任务结果卡共用同一份已落库的研判与追问展示规则。 */
export const DEFAULT_FORECAST_ANALYSIS_QUESTIONS = [
  '本次用气量是升还是降？',
  '造成变化的主要原因？',
  '本次预测准确吗？'
]

/** 解读限定在约 100 个汉字，避免同一结论在页面两处铺成长文。 */
export const compactForecastAnalysis = (value: unknown, limit = 110) => {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (text.length <= limit) return text
  const clipped = text.slice(0, limit)
  const sentenceEnd = Math.max(clipped.lastIndexOf('。'), clipped.lastIndexOf('！'), clipped.lastIndexOf('？'))
  return (sentenceEnd >= Math.floor(limit * 0.6) ? clipped.slice(0, sentenceEnd + 1) : clipped).trim()
}

/**
 * 追问是固定的三个业务入口，避免模型把具体数值、日期和很长的解释塞进按钮。
 * 参数保留是为了兼容历史记录调用；页面展示不再依赖其中的自由文本。
 */
export const forecastAnalysisQuestions = (_value?: unknown) => DEFAULT_FORECAST_ANALYSIS_QUESTIONS
