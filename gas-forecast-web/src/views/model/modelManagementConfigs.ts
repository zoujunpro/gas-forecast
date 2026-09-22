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

const trainModeOptions = [
  { label: '最近时间', value: 'RECENT' },
  { label: '指定时间范围', value: 'RANGE' }
]

const trainTimeGranularityOptions = [
  { label: '日', value: 'DAY' },
  { label: '旬', value: 'TENDAY' },
  { label: '月', value: 'MONTH' }
]

export const modelFeatureDefinitionConfig: BaseDataPageConfig = {
  title: '特征定义管理',
  endpoint: '/model-feature-definition',
  searchPlaceholder: '搜索特征编号、名称、宽表字段、时间跨度',
  filterFields: [
    { prop: 'timeGranularity', label: '时间跨度', type: 'select', options: timeGranularityOptions, placeholder: '时间跨度', width: 130 }
  ],
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

export const modelTrainConfigConfig: BaseDataPageConfig = {
  title: '模型训练管理',
  endpoint: '/model-train-config',
  searchPlaceholder: '搜索配置编码、名称、智能体、模型、范围',
  filterFields: [
    { prop: 'agentCode', label: '智能体', type: 'select', options: modelAgentOptions, placeholder: '智能体', width: 190 },
    { prop: 'timeGranularity', label: '时间格式', type: 'select', options: trainTimeGranularityOptions, placeholder: '时间格式', width: 130 }
  ],
  tableFields: [
    { prop: 'trainCode', label: '训练配置编码', minWidth: 180 },
    { prop: 'trainName', label: '训练名称', minWidth: 190 },
    { prop: 'agentCode', label: '智能体', minWidth: 150, displayType: 'tag', enumMap: { 'winter-supply': '冬季保供', 'monthly-sales': '月度销量', 'short-term': '短期客户' }, tagTypeMap: { 'winter-supply': 'warning', 'monthly-sales': 'primary', 'short-term': 'success' } },
    { prop: 'modelName', label: '所属模型', minWidth: 180 },
    { prop: 'regionName', label: '训练区域', minWidth: 120 },
    { prop: 'industryName', label: '训练行业', minWidth: 120 },
    { prop: 'customerName', label: '训练客户', minWidth: 150 },
    { prop: 'trainMode', label: '训练方式', minWidth: 120, displayType: 'tag', enumMap: { RECENT: '最近时间', RANGE: '指定时间范围' }, tagTypeMap: { RECENT: 'success', RANGE: 'primary' } },
    { prop: 'timeGranularity', label: '时间格式', minWidth: 110, displayType: 'tag', enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月' }, tagTypeMap: { DAY: 'success', TENDAY: 'warning', MONTH: 'primary' } },
    { prop: 'recentPeriods', label: '最近数量', minWidth: 110, align: 'right' },
    { prop: 'trainStartDate', label: '开始日期', minWidth: 120 },
    { prop: 'trainEndDate', label: '结束日期', minWidth: 120 },
    { prop: 'enabled', label: '启用状态', minWidth: 110, displayType: 'enabled' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'trainName', label: '训练名称', required: true, maxLength: 128 },
    { prop: 'agentCode', label: '智能体', type: 'select', options: modelAgentOptions, required: true },
    {
      prop: 'modelId',
      label: '模型编码/名称',
      type: 'select',
      optionSource: { endpoint: '/model-config', valueProp: 'id', labelProp: 'configName', labelTemplate: 'nameWithCode' },
      fillProps: { modelCode: 'configCode', modelName: 'configName' }
    },
    {
      prop: 'regionCode',
      label: '训练区域编码/名称',
      type: 'select',
      maxLength: 64,
      optionSource: { endpoint: '/base-region', valueProp: 'regionCode', labelProp: 'regionName', labelTemplate: 'nameWithCode' },
      fillProps: { regionName: 'regionName' }
    },
    {
      prop: 'industryCode',
      label: '训练行业编码/名称',
      type: 'select',
      maxLength: 64,
      optionSource: { endpoint: '/base-industry', valueProp: 'industryCode', labelProp: 'industryName', labelTemplate: 'nameWithCode' },
      fillProps: { industryName: 'industryName' }
    },
    {
      prop: 'customerCode',
      label: '训练客户编码/名称',
      type: 'select',
      maxLength: 64,
      optionSource: { endpoint: '/base-customer', valueProp: 'customerCode', labelProp: 'customerName', labelTemplate: 'nameWithCode' },
      fillProps: { customerName: 'customerName' }
    },
    { prop: 'trainMode', label: '模型训练方式', type: 'radio', options: trainModeOptions, required: true },
    { prop: 'timeGranularity', label: '时间格式', type: 'select', options: trainTimeGranularityOptions, required: true },
    { prop: 'recentPeriods', label: '最近数量', type: 'number', min: 1, required: true, visibleWhen: { prop: 'trainMode', value: 'RECENT' } },
    { prop: 'trainStartDate', label: '训练开始日期', type: 'granularityDate', granularityProp: 'timeGranularity', maxLength: 32, required: true, visibleWhen: { prop: 'trainMode', value: 'RANGE' } },
    { prop: 'trainEndDate', label: '训练结束日期', type: 'granularityDate', granularityProp: 'timeGranularity', maxLength: 32, required: true, visibleWhen: { prop: 'trainMode', value: 'RANGE' } },
    { prop: 'enabled', label: '启用状态', type: 'select', options: [{ label: '启用', value: 1 }, { label: '停用', value: 0 }], required: true },
    { prop: 'remark', label: '备注', inputType: 'textarea', maxLength: 512 }
  ],
  emptyForm: {
    trainCode: '',
    trainName: '',
    agentCode: 'winter-supply',
    modelId: undefined,
    modelCode: '',
    modelName: '',
    regionCode: '',
    regionName: '',
    industryCode: '',
    industryName: '',
    customerCode: '',
    customerName: '',
    trainMode: 'RECENT',
    timeGranularity: 'MONTH',
    recentPeriods: 36,
    trainStartDate: '',
    trainEndDate: '',
    enabled: 1,
    remark: ''
  },
  permissions: { create: 'model:train-config:create', update: 'model:train-config:update', delete: 'model:train-config:delete', execute: 'config:train:execute' },
  trainExecution: true
}

export const modelTrainFeatureDataConfig: BaseDataPageConfig = {
  title: '训练数据管理',
  endpoint: '/model-train-feature-data',
  searchPlaceholder: '搜索日期、粒度、区域、客户、行业',
  readonly: true,
  featureDetailProp: 'featureValues',
  filterFields: [
    { prop: 'timeGranularity', label: '时间跨度', type: 'select', options: timeGranularityOptions, placeholder: '时间跨度', width: 130 },
    { prop: 'statDateRange', label: '统计日期范围', type: 'dateRange', startProp: 'statDateStart', endProp: 'statDateEnd', width: 260 }
  ],
  tableFields: [
    { prop: 'statDate', label: '统计日期', minWidth: 120, sortable: 'custom' },
    { prop: 'timeGranularity', label: '时间跨度', minWidth: 110, displayType: 'tag', enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月', YEAR: '年' }, tagTypeMap: { DAY: 'success', TENDAY: 'warning', MONTH: 'primary', YEAR: 'info' } },
    { prop: 'regionName', label: '区域', minWidth: 120 },
    { prop: 'customerName', label: '客户', minWidth: 160 },
    { prop: 'industryName', label: '行业', minWidth: 120 },
    { prop: 'gasSales', label: '天然气销量', minWidth: 130, align: 'right' },
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
    { prop: 'gasSales', label: '天然气销量', type: 'number', min: 0 }
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
    gasSales: 0
  },
  permissions: {}
}
