export interface AgentConfig {
  dataSource: string
  dateRange: [Date, Date]
  region: string[]
  model: string
  forecastDays: number
}

export interface PredictionResult {
  totalVolume: string
  mae: number
  accuracy: number
  chartData: {
    dates: string[]
    actual: number[]
    predicted: number[]
  }
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
}

export interface StreamEvent {
  type: 'progress' | 'result' | 'error'
  node?: string
  message?: string
  content?: string
  data?: any
}
