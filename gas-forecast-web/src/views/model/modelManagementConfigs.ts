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
  { prop: 'enabled', label: '启用状态', minWidth: 100, displayType: 'enabled' },
  { prop: 'agentName', label: '所属智能体', minWidth: 190 },
  { prop: 'regionNames', label: '支持地区', minWidth: 160, displayType: 'array' },
  { prop: 'industryNames', label: '支持行业', minWidth: 160, displayType: 'array' },
  { prop: 'customerNames', label: '支持客户', minWidth: 180, displayType: 'array' },
  { prop: 'timeGranularity', label: '时间粒度', minWidth: 100, displayType: 'tag', enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月' } },
  { prop: 'minimumTrainingRows', label: '最短训练量', minWidth: 110 },
  { prop: 'recommendedTrainingPeriods', label: '推荐训练周期', minWidth: 125 },
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
  { label: '全部历史数据', value: 'ALL' },
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
  dialogWidth: '820px',
  dialogDescription: '定义模型可用的特征信息，用于特征管理与训练配置',
  dialogVariant: 'feature-definition',
  searchPlaceholder: '搜索特征编号、名称、取值来源、来源字段或状态',
  filterFields: [
    {
      prop: 'sourceType',
      label: '数据来源',
      type: 'select',
      options: [
        { label: '数据库', value: 'DATABASE' },
        { label: '外部接口', value: 'API' },
        { label: '研报大模型', value: 'REPORT_LLM' }
      ],
      placeholder: '数据来源',
      width: 140
    },
    {
      prop: 'status',
      label: '特征状态',
      type: 'select',
      options: [
        { label: '候选', value: 'CANDIDATE' },
        { label: '生效', value: 'ACTIVE' },
        { label: '停用', value: 'DISABLED' }
      ],
      placeholder: '特征状态',
      width: 130
    }
  ],
  tableFields: [
    { prop: 'featureCode', label: '特征编号', minWidth: 150 },
    { prop: 'featureName', label: '特征名称', minWidth: 180 },
    { prop: 'valueSource', label: '取值来源', minWidth: 145, displayType: 'tag', enumMap: {
      WEATHER_DAILY: '日级天气', SALES_DAILY: '日级销量', FEATURE_VALUE: '特征值库', CALCULATED: '实时计算', MODEL_INTERNAL: '模型内部'
    } },
    { prop: 'sourceField', label: '来源字段', minWidth: 150 },
    { prop: 'calculationType', label: '计算类型', minWidth: 125 },
    { prop: 'aggregationMethod', label: '聚合方式', minWidth: 105, displayType: 'tag', enumMap: { AVG: '平均', SUM: '求和', MAX: '最大', MIN: '最小', COUNT: '计数', LAST: '期末值' } },
    { prop: 'missingValueStrategy', label: '空值处理', minWidth: 120, displayType: 'tag', enumMap: { ERROR: '空值报错', IGNORE: '允许空值', ZERO: '补零', FORWARD_FILL: '向前填充', INTERPOLATE: '插值' } },
    {
      prop: 'sourceType',
      label: '采集方式',
      minWidth: 130,
      displayType: 'tag',
      enumMap: { DATABASE: '数据库', API: '外部接口', REPORT_LLM: '研报大模型' },
      tagTypeMap: { DATABASE: 'success', API: 'primary', REPORT_LLM: 'warning' }
    },
    {
      prop: 'status',
      label: '特征状态',
      minWidth: 120,
      displayType: 'tag',
      enumMap: { CANDIDATE: '候选', ACTIVE: '生效', DISABLED: '停用' },
      tagTypeMap: { CANDIDATE: 'warning', ACTIVE: 'success', DISABLED: 'danger' }
    },
    { prop: 'enabled', label: '启用状态', minWidth: 110, displayType: 'enabled' },
    { prop: 'description', label: '描述', minWidth: 220 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedByName', label: '更新人', minWidth: 120 }
  ],
  formFields: [
    {
      prop: 'featureCode',
      label: '特征编号',
      required: true,
      maxLength: 64,
      placeholder: '请输入特征编号',
      helperText: '用于唯一标识该特征'
    },
    {
      prop: 'featureName',
      label: '特征名称',
      required: true,
      maxLength: 128,
      placeholder: '请输入特征名称',
      helperText: '用于在页面中展示的特征名称'
    },
    {
      prop: 'sourceType',
      label: '数据来源',
      type: 'select',
      options: [
        { label: '数据库', value: 'DATABASE' },
        { label: '外部接口', value: 'API' },
        { label: '研报大模型', value: 'REPORT_LLM' }
      ],
      required: true,
      helperText: '标识数据通过数据库、接口或研报大模型进入系统'
    },
    {
      prop: 'valueSource',
      label: '取值来源',
      type: 'select',
      options: [
        { label: '日级天气表', value: 'WEATHER_DAILY' },
        { label: '日级销量表', value: 'SALES_DAILY' },
        { label: '通用特征值库', value: 'FEATURE_VALUE' },
        { label: '实时计算', value: 'CALCULATED' },
        { label: '模型内部生成', value: 'MODEL_INTERNAL' }
      ],
      required: true,
      helperText: '训练组装器根据该字段选择取值逻辑，不允许页面配置任意SQL。'
    },
    {
      prop: 'sourceField',
      label: '来源字段',
      maxLength: 64,
      placeholder: '例如 temp_avg、gas_sales',
      helperText: '日级天气、日级销量或计算特征的输入字段。'
    },
    {
      prop: 'calculationType',
      label: '计算类型',
      type: 'select',
      options: [
        { label: '采暖度日 HDD', value: 'HDD' },
        { label: '制冷度日 CDD', value: 'CDD' },
        { label: '极寒天数', value: 'EXTREME_COLD_DAYS' },
        { label: '日期日历特征', value: 'CALENDAR' }
      ],
      helperText: '仅“实时计算”来源需要配置。'
    },
    {
      prop: 'aggregationMethod',
      label: '日转旬/月聚合',
      type: 'select',
      options: [
        { label: '平均 AVG', value: 'AVG' }, { label: '求和 SUM', value: 'SUM' },
        { label: '最大 MAX', value: 'MAX' }, { label: '最小 MIN', value: 'MIN' },
        { label: '计数 COUNT', value: 'COUNT' }, { label: '期末值 LAST', value: 'LAST' }
      ]
    },
    {
      prop: 'missingValueStrategy',
      label: '空值处理',
      type: 'select',
      options: [
        { label: '允许空值（推荐）', value: 'IGNORE' }, { label: '空值时报错', value: 'ERROR' },
        { label: '补零', value: 'ZERO' }, { label: '向前填充', value: 'FORWARD_FILL' },
        { label: '插值', value: 'INTERPOLATE' }
      ],
      required: true
    },
    {
      prop: 'calculationParams',
      label: '计算参数（JSON）',
      inputType: 'textarea',
      maxLength: 1000,
      rows: 3,
      fullWidth: true,
      placeholder: '例如 {"base_temperature":18}',
      helperText: 'HDD、CDD等计算特征可配置阈值参数。'
    },
    {
      prop: 'status',
      label: '特征状态',
      type: 'select',
      options: [
        { label: '候选', value: 'CANDIDATE' },
        { label: '生效', value: 'ACTIVE' },
        { label: '停用', value: 'DISABLED' }
      ],
      required: true,
      helperText: '候选特征通过审核或验证后再设为生效'
    },
    {
      prop: 'enabled',
      label: '启用状态',
      type: 'select',
      options: [
        { label: '启用', value: 1 },
        { label: '停用', value: 0 }
      ],
      required: true,
      helperText: '控制该特征是否在模型中可用'
    },
    {
      prop: 'description',
      label: '描述',
      inputType: 'textarea',
      maxLength: 500,
      rows: 4,
      showWordLimit: true,
      fullWidth: true,
      placeholder: '请输入特征的功能描述、计算方式或其他说明…',
      helperText: '建议说明特征的业务含义、数据来源、使用场景等信息'
    }
  ],
  emptyForm: {
    featureCode: '', featureName: '', sourceType: 'DATABASE', valueSource: 'WEATHER_DAILY', sourceField: '',
    calculationType: '', calculationParams: '', aggregationMethod: 'AVG', missingValueStrategy: 'IGNORE',
    status: 'ACTIVE', enabled: 1, description: ''
  },
  permissions: {
    create: 'model:feature-definition:create',
    update: 'model:feature-definition:update',
    delete: 'model:feature-definition:delete'
  }
}

export const modelTrainConfigConfig: BaseDataPageConfig = {
  title: '模型训练管理',
  pageDescription: '管理模型训练任务、执行记录及训练结果',
  endpoint: '/model-train-config',
  dialogWidth: 'min(920px, calc(100vw - 32px))',
  dialogDescription: '维护模型训练的基本信息及执行时间配置',
  dialogVariant: 'model-training',
  searchPlaceholder: '搜索配置编码、名称、智能体、模型、范围',
  filterFields: [
    {
      prop: 'agentCode',
      label: '智能体',
      type: 'select',
      options: modelAgentOptions,
      placeholder: '智能体',
      width: 190
    },
    {
      prop: 'timeGranularity',
      label: '时间格式',
      type: 'select',
      options: trainTimeGranularityOptions,
      placeholder: '时间格式',
      width: 130
    }
  ],
  tableFields: [
    { prop: 'trainCode', label: '训练配置编码', minWidth: 180 },
    { prop: 'trainName', label: '训练名称', minWidth: 190 },
    {
      prop: 'agentCode',
      label: '智能体',
      minWidth: 150,
      displayType: 'tag',
      enumMap: { 'winter-supply': '冬季保供', 'monthly-sales': '月度销量', 'short-term': '短期客户' },
      tagTypeMap: { 'winter-supply': 'warning', 'monthly-sales': 'primary', 'short-term': 'success' }
    },
    { prop: 'modelName', label: '所属模型', minWidth: 180 },
    { prop: 'regionName', label: '训练区域', minWidth: 120 },
    { prop: 'industryName', label: '训练行业', minWidth: 120 },
    { prop: 'customerName', label: '训练客户', minWidth: 150 },
    {
      prop: 'trainMode',
      label: '训练方式',
      minWidth: 120,
      displayType: 'tag',
      enumMap: { ALL: '全部历史数据', RECENT: '最近时间', RANGE: '指定时间范围' },
      tagTypeMap: { ALL: 'warning', RECENT: 'success', RANGE: 'primary' }
    },
    {
      prop: 'timeGranularity',
      label: '时间格式',
      minWidth: 110,
      displayType: 'tag',
      enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月' },
      tagTypeMap: { DAY: 'success', TENDAY: 'warning', MONTH: 'primary' }
    },
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
      optionSource: {
        endpoint: '/model-config',
        valueProp: 'id',
        labelProp: 'configName',
        labelTemplate: 'nameWithCode'
      },
      fillProps: { modelCode: 'configCode', modelName: 'configName', timeGranularity: 'timeGranularity' }
    },
    {
      prop: 'regionCode',
      label: '训练区域编码/名称',
      type: 'select',
      maxLength: 64,
      optionSource: {
        endpoint: '/base-region',
        valueProp: 'regionCode',
        labelProp: 'regionName',
        labelTemplate: 'nameWithCode'
      },
      fillProps: { regionName: 'regionName' }
    },
    {
      prop: 'industryCode',
      label: '训练行业编码/名称',
      type: 'select',
      maxLength: 64,
      optionSource: {
        endpoint: '/base-industry',
        valueProp: 'industryCode',
        labelProp: 'industryName',
        labelTemplate: 'nameWithCode'
      },
      fillProps: { industryName: 'industryName' }
    },
    {
      prop: 'customerCode',
      label: '训练客户编码/名称',
      type: 'select',
      maxLength: 64,
      optionSource: {
        endpoint: '/base-customer',
        valueProp: 'customerCode',
        labelProp: 'customerName',
        labelTemplate: 'nameWithCode'
      },
      fillProps: {
        customerName: 'customerName',
        regionCode: 'regionCode',
        regionName: 'regionName',
        industryCode: 'industryCode',
        industryName: 'industryName'
      }
    },
    { prop: 'trainMode', label: '模型训练方式', type: 'radio', options: trainModeOptions, required: true },
    {
      prop: 'timeGranularity',
      label: '时间格式',
      type: 'select',
      options: trainTimeGranularityOptions,
      required: true,
      disabled: true,
      helperText: '由所选模型自动带入，不需要手工选择。'
    },
    {
      prop: 'recentPeriods',
      label: '最近数量',
      type: 'number',
      min: 1,
      required: true,
      visibleWhen: { prop: 'trainMode', value: 'RECENT' }
    },
    {
      prop: 'trainStartDate',
      label: '训练开始日期',
      type: 'granularityDate',
      granularityProp: 'timeGranularity',
      maxLength: 32,
      required: true,
      visibleWhen: { prop: 'trainMode', value: 'RANGE' }
    },
    {
      prop: 'trainEndDate',
      label: '训练结束日期',
      type: 'granularityDate',
      granularityProp: 'timeGranularity',
      maxLength: 32,
      required: true,
      visibleWhen: { prop: 'trainMode', value: 'RANGE' }
    },
    {
      prop: 'enabled',
      label: '启用状态',
      type: 'select',
      options: [
        { label: '启用', value: 1 },
        { label: '停用', value: 0 }
      ],
      required: true
    },
    {
      prop: 'remark',
      label: '备注',
      inputType: 'textarea',
      maxLength: 512,
      rows: 3,
      showWordLimit: true,
      fullWidth: true,
      placeholder: '请输入模型训练配置的补充说明'
    }
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
    timeGranularity: '',
    recentPeriods: null,
    trainStartDate: '',
    trainEndDate: '',
    enabled: 1,
    remark: ''
  },
  permissions: {
    create: 'model:train-config:create',
    update: 'model:train-config:update',
    delete: 'model:train-config:delete',
    execute: 'config:train:execute'
  },
  trainExecution: true
}

export const agentStrategyConfig: BaseDataPageConfig = {
  title: '智能体策略管理',
  endpoint: '/agent-strategy-config',
  searchPlaceholder: '搜索智能体编码、名称或策略说明',
  tableFields: [
    { prop: 'agentName', label: '智能体', minWidth: 180 },
    { prop: 'trainScopeType', label: '训练主体', minWidth: 120, displayType: 'tag', enumMap: { CUSTOMER: '客户', REGION: '地区', INDUSTRY: '行业', REGION_INDUSTRY: '地区+行业' } },
    { prop: 'triggerType', label: '触发方式', minWidth: 130, displayType: 'tag', enumMap: { NEW_DATA: '新数据', SCHEDULE: '定时', BEFORE_FORECAST: '预测前', HYBRID: '综合触发' } },
    { prop: 'checkCron', label: '巡检规则', minWidth: 150 },
    { prop: 'modelValidityPeriods', label: '模型有效期', minWidth: 115, align: 'right' },
    { prop: 'autoTrainEnabled', label: '自动训练', minWidth: 105, displayType: 'enabled' },
    { prop: 'autoForecastEnabled', label: '自动预测', minWidth: 105, displayType: 'enabled' },
    { prop: 'enabled', label: '策略状态', minWidth: 105, displayType: 'enabled' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'agentCode', label: '智能体', type: 'select', options: modelAgentOptions, required: true, disabled: true },
    { prop: 'agentName', label: '智能体名称', required: true, maxLength: 128, readonly: true },
    { prop: 'trainScopeType', label: '训练主体', type: 'select', required: true, options: [
      { label: '客户', value: 'CUSTOMER' }, { label: '地区', value: 'REGION' }, { label: '行业', value: 'INDUSTRY' }, { label: '地区+行业', value: 'REGION_INDUSTRY' }
    ] },
    { prop: 'triggerType', label: '触发方式', type: 'select', required: true, options: [
      { label: '新数据触发', value: 'NEW_DATA' }, { label: '定时触发', value: 'SCHEDULE' }, { label: '预测前触发', value: 'BEFORE_FORECAST' }, { label: '综合触发', value: 'HYBRID' }
    ] },
    { prop: 'checkCron', label: '巡检 Cron', required: true, maxLength: 64, helperText: '定时任务只做条件巡检，不代表每次都执行训练。' },
    { prop: 'dataDelayPeriods', label: '数据延迟周期数', type: 'number', min: 0, required: true },
    { prop: 'modelValidityPeriods', label: '模型有效周期数', type: 'number', min: 1, required: true },
    { prop: 'maxRetryCount', label: '最大重试次数', type: 'number', min: 0, required: true },
    { prop: 'retryIntervalMinutes', label: '重试间隔（分钟）', type: 'number', min: 1, required: true },
    { prop: 'forecastLeadPeriods', label: '预测提前周期数', type: 'number', min: 0, required: true },
    { prop: 'autoTrainEnabled', label: '自动训练', type: 'select', required: true, options: [{ label: '开启', value: 1 }, { label: '关闭', value: 0 }] },
    { prop: 'autoForecastEnabled', label: '自动预测', type: 'select', required: true, options: [{ label: '开启', value: 1 }, { label: '关闭', value: 0 }] },
    { prop: 'enabled', label: '策略状态', type: 'select', required: true, options: [{ label: '启用', value: 1 }, { label: '停用', value: 0 }] },
    { prop: 'description', label: '策略说明', inputType: 'textarea', rows: 3, maxLength: 500, showWordLimit: true, fullWidth: true }
  ],
  emptyForm: {
    agentCode: 'short-term', agentName: '短期客户预测智能体', trainScopeType: 'CUSTOMER', triggerType: 'HYBRID',
    checkCron: '0 0 2 * * ?', dataDelayPeriods: 1, modelValidityPeriods: 7,
    maxRetryCount: 3, retryIntervalMinutes: 30, autoTrainEnabled: 1, autoForecastEnabled: 0,
    forecastLeadPeriods: 1, enabled: 1, description: ''
  },
  permissions: { update: 'agent:strategy:update' }
}

export const modelTrainFeatureDataConfig: BaseDataPageConfig = {
  title: '训练数据管理',
  endpoint: '/model-train-feature-data',
  searchPlaceholder: '搜索日期、粒度、区域、客户、行业',
  readonly: true,
  featureDetailProp: 'featureValues',
  filterFields: [
    {
      prop: 'timeGranularity',
      label: '时间跨度',
      type: 'select',
      options: timeGranularityOptions,
      placeholder: '时间跨度',
      width: 130
    },
    {
      prop: 'statDateRange',
      label: '统计日期范围',
      type: 'dateRange',
      startProp: 'statDateStart',
      endProp: 'statDateEnd',
      width: 260
    },
    {
      prop: 'customerCode',
      label: '客户',
      type: 'select',
      placeholder: '客户',
      width: 180,
      optionSource: {
        endpoint: '/base-customer',
        valueProp: 'customerCode',
        labelProp: 'customerName',
        labelTemplate: 'nameWithCode'
      }
    },
    {
      prop: 'regionCode',
      label: '地区',
      type: 'select',
      placeholder: '地区',
      width: 160,
      optionSource: {
        endpoint: '/base-region',
        valueProp: 'regionCode',
        labelProp: 'regionName',
        labelTemplate: 'nameWithCode'
      }
    },
    {
      prop: 'industryCode',
      label: '行业',
      type: 'select',
      placeholder: '行业',
      width: 160,
      optionSource: {
        endpoint: '/base-industry',
        valueProp: 'industryCode',
        labelProp: 'industryName',
        labelTemplate: 'nameWithCode'
      }
    }
  ],
  tableFields: [
    { prop: 'statDate', label: '统计日期', minWidth: 120, sortable: 'custom' },
    {
      prop: 'timeGranularity',
      label: '时间跨度',
      minWidth: 110,
      displayType: 'tag',
      enumMap: { DAY: '日', TENDAY: '旬', MONTH: '月', YEAR: '年' },
      tagTypeMap: { DAY: 'success', TENDAY: 'warning', MONTH: 'primary', YEAR: 'info' }
    },
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
