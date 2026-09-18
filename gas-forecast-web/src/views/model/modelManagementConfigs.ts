import type { BaseDataFieldConfig, BaseDataPageConfig } from '@/views/shared/managementTypes'

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
  { prop: 'modelVersion', label: '模型版本', minWidth: 110 },
  { prop: 'agentName', label: '所属智能体', minWidth: 190 },
  { prop: 'regionNames', label: '区域', minWidth: 160, displayType: 'array' },
  { prop: 'description', label: '描述', minWidth: 220 },
  { prop: 'updateTime', label: '更新时间', minWidth: 170, displayType: 'datetime' }
]

const timeGranularityOptions = [
  { label: '日', value: 'DAY' },
  { label: '旬', value: 'TENDAY' },
  { label: '月', value: 'MONTH' },
  { label: '年', value: 'YEAR' }
]

export const modelFeatureDefinitionConfig: BaseDataPageConfig = {
  title: '特征定义管理',
  endpoint: '/model-feature-definition',
  searchPlaceholder: '搜索特征编号、名称、宽表字段、时间跨度',
  tableFields: [
    { prop: 'featureCode', label: '特征编号', minWidth: 150 },
    { prop: 'featureName', label: '特征名称', minWidth: 180 },
    { prop: 'featureColumn', label: '宽表字段', minWidth: 150 },
    { prop: 'timeGranularity', label: '时间跨度', minWidth: 110, displayType: 'tag', enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月', YEAR: '年' }, tagTypeMap: { DAY: 'success', TENDAY: 'warning', MONTH: 'primary', YEAR: 'info' } },
    { prop: 'enabled', label: '启用状态', minWidth: 110, displayType: 'enabled' },
    { prop: 'description', label: '描述', minWidth: 220 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedByName', label: '更新人', minWidth: 120 }
  ],
  formFields: [
    { prop: 'featureCode', label: '特征编号', required: true, maxLength: 64 },
    { prop: 'featureName', label: '特征名称', required: true, maxLength: 128 },
    { prop: 'timeGranularity', label: '时间跨度', type: 'select', options: timeGranularityOptions, required: true },
    { prop: 'enabled', label: '启用状态', type: 'select', options: [{ label: '启用', value: 1 }, { label: '停用', value: 0 }], required: true },
    { prop: 'description', label: '描述', inputType: 'textarea', maxLength: 500 }
  ],
  emptyForm: { featureCode: '', featureName: '', timeGranularity: 'DAY', enabled: 1, description: '' },
  permissions: { create: 'model:feature-definition:create', update: 'model:feature-definition:update', delete: 'model:feature-definition:delete' }
}

export const modelTrainFeatureDataConfig: BaseDataPageConfig = {
  title: '训练数据管理',
  endpoint: '/model-train-feature-data',
  searchPlaceholder: '搜索日期、粒度、区域、客户、行业',
  readonly: true,
  tableFields: [
    { prop: 'statDate', label: '统计日期', minWidth: 120 },
    { prop: 'timeGranularity', label: '时间跨度', minWidth: 110, displayType: 'tag', enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月', YEAR: '年' }, tagTypeMap: { DAY: 'success', TENDAY: 'warning', MONTH: 'primary', YEAR: 'info' } },
    { prop: 'regionName', label: '区域', minWidth: 120 },
    { prop: 'customerName', label: '客户', minWidth: 160 },
    { prop: 'industryName', label: '行业', minWidth: 120 },
    { prop: 'gasSales', label: '天然气销量', minWidth: 130, align: 'right' },
    { prop: 'feature001', label: '特征001', minWidth: 110, align: 'right', tooltip: '平均气温 | avg_temp | 时间跨度：旬' },
    { prop: 'feature002', label: '特征002', minWidth: 110, align: 'right', tooltip: '最高气温 | max_temp | 时间跨度：旬' },
    { prop: 'feature003', label: '特征003', minWidth: 110, align: 'right', tooltip: '最低气温 | min_temp | 时间跨度：旬' },
    { prop: 'feature004', label: '特征004', minWidth: 110, align: 'right', tooltip: '采暖度日 | hdd | 时间跨度：旬' },
    { prop: 'feature005', label: '特征005', minWidth: 110, align: 'right', tooltip: '极寒天数 | extreme_cold_days | 时间跨度：旬' },
    { prop: 'updateTime', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'statDate', label: '统计日期', required: true, maxLength: 32 },
    { prop: 'timeGranularity', label: '时间跨度', type: 'select', options: timeGranularityOptions, required: true },
    { prop: 'regionCode', label: '区域编码', maxLength: 64 },
    { prop: 'regionName', label: '区域名称', maxLength: 128 },
    { prop: 'customerCode', label: '客户编码', maxLength: 64 },
    { prop: 'customerName', label: '客户名称', maxLength: 128 },
    { prop: 'industryCode', label: '行业编码', maxLength: 64 },
    { prop: 'industryName', label: '行业名称', maxLength: 128 },
    { prop: 'gasSales', label: '天然气销量', type: 'number', min: 0 },
    { prop: 'feature001', label: '特征001', type: 'number' },
    { prop: 'feature002', label: '特征002', type: 'number' },
    { prop: 'feature003', label: '特征003', type: 'number' },
    { prop: 'feature004', label: '特征004', type: 'number' },
    { prop: 'feature005', label: '特征005', type: 'number' },
    { prop: 'feature006', label: '特征006', type: 'number' },
    { prop: 'feature007', label: '特征007', type: 'number' },
    { prop: 'feature008', label: '特征008', type: 'number' },
    { prop: 'feature009', label: '特征009', type: 'number' },
    { prop: 'feature010', label: '特征010', type: 'number' }
  ],
  emptyForm: {
    statDate: '',
    timeGranularity: 'DAY',
    regionCode: '',
    regionName: '',
    customerCode: '',
    customerName: '',
    industryCode: '',
    industryName: '',
    gasSales: 0,
    feature001: 0,
    feature002: 0,
    feature003: 0,
    feature004: 0,
    feature005: 0,
    feature006: 0,
    feature007: 0,
    feature008: 0,
    feature009: 0,
    feature010: 0
  },
  permissions: {}
}
