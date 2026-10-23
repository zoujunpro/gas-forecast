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
  clarification?: ChatClarification
  clarificationResolved?: boolean
  /** Agent 返回的展示元数据。 */
  presentation?: Record<string, any>
  /** 工具执行的原始结果；查询列表从其中的 records 渲染。 */
  executionResult?: Record<string, any>
  /** 统一执行任务编号；页面据此展示任务明细和步骤。 */
  taskNo?: string
}

export interface ChatClarificationOption {
  value: string
  label: string
}

export interface ChatClarificationField {
  parameterCode: string
  parameterName: string
  reason: string
  inputType: 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT' | 'MULTI_SELECT' | 'CONFIRM'
  required: boolean
  options: ChatClarificationOption[]
}

export interface ChatClarification {
  interactionId: string
  interactionType: 'CLARIFICATION' | 'CONFIRMATION'
  replyToTurnId: string
  requestId?: string
  reason: string
  resumeFrom: string
  prompt: string
  fields: ChatClarificationField[]
}

export interface StreamEvent {
  type: 'progress' | 'result' | 'error'
  node?: string
  message?: string
  content?: string
  data?: any
}
