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
  /** 本轮智能体运行编号；用于恢复和展示真实过程事件。 */
  runNo?: string
  runStatus?: string
  runStartedAt?: string
  runFinishedAt?: string
  executionEvents?: AgentRunEvent[]
  processExpanded?: boolean
  /** 异步预测完成后单独展示的结论，不能与“已收到任务”确认文案混在同一气泡。 */
  completionSummary?: string
}

export interface AgentRunEvent {
  id: number
  runNo: string
  eventCode: string
  message: string
  eventDataJson?: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'CANCELLED' | string
  forecastNo?: string
  taskDetailNo?: string
  errorCode?: string
  errorMessage?: string
  createdAt?: string
  updatedAt?: string
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
