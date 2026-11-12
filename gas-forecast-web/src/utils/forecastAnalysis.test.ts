import { describe, expect, it } from 'vitest'
import { DEFAULT_FORECAST_ANALYSIS_QUESTIONS, forecastAnalysisQuestions } from './forecastAnalysis'

describe('forecastAnalysisQuestions', () => {
  it('uses the three persisted questions generated for this forecast', () => {
    const value = JSON.stringify([
      { code: 'FORECAST_CHANGE_RATE', category: 'CHANGE_RATE', text: '未来10天用气量下降多少？' },
      { code: 'FORECAST_CAUSE', category: 'CAUSE', text: '用气量下降受哪些因素影响？' },
      { code: 'FORECAST_ACCURACY', category: 'ACCURACY', text: '12.8%的MAPE意味着什么？' }
    ])
    expect(forecastAnalysisQuestions(value)).toEqual([
      '未来10天用气量下降多少？',
      '用气量下降受哪些因素影响？',
      '12.8%的MAPE意味着什么？'
    ])
  })

  it('keeps compatibility with historical string arrays', () => {
    expect(forecastAnalysisQuestions('["趋势如何？","原因是什么？","是否可信？"]')).toEqual(['趋势如何？', '原因是什么？', '是否可信？'])
  })

  it('falls back when persisted questions are invalid', () => {
    expect(forecastAnalysisQuestions('["重复问题？","重复问题？"]')).toEqual(DEFAULT_FORECAST_ANALYSIS_QUESTIONS)
    expect(forecastAnalysisQuestions('invalid json')).toEqual(DEFAULT_FORECAST_ANALYSIS_QUESTIONS)
  })
})
