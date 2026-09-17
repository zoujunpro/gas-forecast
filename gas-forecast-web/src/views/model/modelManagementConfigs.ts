import type { BaseDataFieldConfig } from '@/views/shared/managementTypes'

export const modelAgentOptions = [
  { label: '冬季保供预测智能体', value: 'winter-supply', sceneCode: 'WINTER_SUPPLY' },
  { label: '月度销量预测智能体', value: 'monthly-sales', sceneCode: 'MONTHLY_SALES' },
  { label: '短期客户预测智能体', value: 'short-term', sceneCode: 'SHORT_CUSTOMER' }
]

export const modelSceneLabels: Record<string, string> = {
  WINTER_SUPPLY: '冬季保供',
  MONTHLY_SALES: '月度销量',
  SHORT_CUSTOMER: '短期客户'
}

export const modelStrategyLabels: Record<string, string> = {
  MULTI_SELECT: '多选',
  SINGLE: '单选'
}

export const modelConfigTableFields: BaseDataFieldConfig[] = [
  { prop: 'configCode', label: '模型编码', minWidth: 150 },
  { prop: 'configName', label: '模型名称', minWidth: 180 },
  { prop: 'agentName', label: '所属智能体', minWidth: 190 },
  { prop: 'regionNames', label: '区域', minWidth: 160, displayType: 'array' },
  { prop: 'industryNames', label: '行业', minWidth: 140, displayType: 'array' },
  { prop: 'customerNames', label: '客户', minWidth: 170, displayType: 'array' },
  { prop: 'description', label: '描述', minWidth: 220 },
  { prop: 'updateTime', label: '更新时间', minWidth: 170, displayType: 'datetime' }
]
