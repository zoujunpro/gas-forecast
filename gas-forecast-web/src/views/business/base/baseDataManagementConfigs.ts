import type { BaseDataPageConfig } from '../../shared/managementTypes'

const fileStatusOptions = [
  { label: '已上传', value: 'UPLOADED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '处理成功', value: 'SUCCESS' },
  { label: '处理失败', value: 'FAILED' }
]

export const regionsDataConfig: BaseDataPageConfig = {
  title: '区域表管理',
  endpoint: '/base-region',
  searchPlaceholder: '搜索区域名称、备注',
  tableFields: [
    { prop: 'regionName', label: '区域名称', minWidth: 140 },
    { prop: 'remark', label: '备注', minWidth: 180 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'regionName', label: '区域名称', required: true, maxLength: 100 },
    { prop: 'remark', label: '备注' }
  ],
  emptyForm: { regionName: '', remark: '' },
  permissions: { create: 'base:region:create', update: 'base:region:update', delete: 'base:region:delete' }
}

const weatherTypeOptions = [
  { label: '实际天气', value: 'ACTUAL' },
  { label: '预报天气', value: 'FORECAST' }
]

const weatherQualityOptions = [
  { label: '有效', value: 'VALID' },
  { label: '缺失', value: 'MISSING' },
  { label: '异常', value: 'ABNORMAL' }
]

export const dailyWeatherDataConfig: BaseDataPageConfig = {
  title: '日级天气管理',
  pageDescription: '统一维护省级每日实际天气和未来预报天气；旬、月天气由日级数据聚合产生。',
  endpoint: '/data-daily-weather',
  dialogWidth: 'min(1080px, calc(100vw - 32px))',
  searchPlaceholder: '搜索地区、天气版本或数据来源',
  filterFields: [
    {
      prop: 'regionCode', label: '地区', type: 'select', width: 160,
      optionSource: { endpoint: '/base-region', valueProp: 'regionCode', labelProp: 'regionName', labelTemplate: 'nameWithCode' }
    },
    { prop: 'weatherType', label: '天气类型', type: 'select', width: 130, options: weatherTypeOptions },
    { prop: 'qualityStatus', label: '数据质量', type: 'select', width: 130, options: weatherQualityOptions },
    { prop: 'statDateRange', label: '天气日期', type: 'dateRange', startProp: 'statDateStart', endProp: 'statDateEnd', width: 260 }
  ],
  tableFields: [
    { prop: 'statDate', label: '天气日期', minWidth: 115, displayType: 'date', sortable: 'custom' },
    { prop: 'statMonth', label: '所属月份', minWidth: 105 },
    { prop: 'statTenday', label: '所属旬', minWidth: 110 },
    { prop: 'regionName', label: '地区', minWidth: 110 },
    { prop: 'weatherType', label: '天气类型', minWidth: 105, displayType: 'tag', enumMap: { ACTUAL: '实际', FORECAST: '预报' }, tagTypeMap: { ACTUAL: 'success', FORECAST: 'primary' } },
    { prop: 'tempMax', label: '最高温', minWidth: 95, align: 'right' },
    { prop: 'tempMin', label: '最低温', minWidth: 95, align: 'right' },
    { prop: 'tempAvg', label: '平均温', minWidth: 95, align: 'right' },
    { prop: 'feelsLikeMax', label: '最高体感温度', minWidth: 125, align: 'right' },
    { prop: 'feelsLikeMin', label: '最低体感温度', minWidth: 125, align: 'right' },
    { prop: 'feelsLikeAvg', label: '平均体感温度', minWidth: 125, align: 'right' },
    { prop: 'dewPoint', label: '露点温度', minWidth: 100, align: 'right' },
    { prop: 'humidity', label: '湿度', minWidth: 90, align: 'right' },
    { prop: 'precipitation', label: '降水量', minWidth: 100, align: 'right' },
    { prop: 'windGust', label: '阵风风速', minWidth: 100, align: 'right' },
    { prop: 'windSpeed', label: '风速', minWidth: 90, align: 'right' },
    { prop: 'seaLevelPressure', label: '海平面气压', minWidth: 115, align: 'right' },
    { prop: 'cloudCover', label: '云量', minWidth: 90, align: 'right' },
    { prop: 'visibility', label: '能见度', minWidth: 95, align: 'right' },
    { prop: 'solarRadiation', label: '太阳辐射', minWidth: 105, align: 'right' },
    { prop: 'solarEnergy', label: '太阳能量', minWidth: 105, align: 'right' },
    { prop: 'uvIndex', label: '紫外线指数', minWidth: 110, align: 'right' },
    { prop: 'hdd', label: '采暖度日(HDD)', minWidth: 125, align: 'right' },
    { prop: 'weatherVersion', label: '天气版本', minWidth: 150 },
    { prop: 'forecastIssueTime', label: '预报发布时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'sourceCode', label: '数据来源', minWidth: 110 },
    { prop: 'qualityStatus', label: '数据质量', minWidth: 105, displayType: 'tag', enumMap: { VALID: '有效', MISSING: '缺失', ABNORMAL: '异常' }, tagTypeMap: { VALID: 'success', MISSING: 'warning', ABNORMAL: 'danger' } },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    {
      prop: 'regionCode', label: '地区', type: 'select', required: true,
      optionSource: { endpoint: '/base-region', valueProp: 'regionCode', labelProp: 'regionName', labelTemplate: 'nameWithCode' },
      fillProps: { regionName: 'regionName' }
    },
    { prop: 'statDate', label: '天气日期', type: 'granularityDate', granularityProp: 'timeGranularity', required: true },
    { prop: 'weatherType', label: '天气类型', type: 'radio', options: weatherTypeOptions, required: true },
    { prop: 'forecastIssueTime', label: '预报发布时间', placeholder: 'yyyy-MM-dd HH:mm:ss', visibleWhen: { prop: 'weatherType', value: 'FORECAST' }, required: true },
    { prop: 'weatherVersion', label: '天气版本', maxLength: 32, helperText: '实际天气自动使用ACTUAL；预报天气留空时按发布时间生成。' },
    { prop: 'tempMax', label: '最高气温', type: 'number' },
    { prop: 'tempMin', label: '最低气温', type: 'number' },
    { prop: 'tempAvg', label: '平均气温', type: 'number' },
    { prop: 'feelsLikeMax', label: '最高体感温度', type: 'number' },
    { prop: 'feelsLikeMin', label: '最低体感温度', type: 'number' },
    { prop: 'feelsLikeAvg', label: '平均体感温度', type: 'number' },
    { prop: 'dewPoint', label: '露点温度', type: 'number' },
    { prop: 'humidity', label: '湿度', type: 'number' },
    { prop: 'precipitation', label: '降水量', type: 'number' },
    { prop: 'windGust', label: '阵风风速', type: 'number' },
    { prop: 'windSpeed', label: '风速', type: 'number' },
    { prop: 'seaLevelPressure', label: '海平面气压', type: 'number' },
    { prop: 'cloudCover', label: '云量', type: 'number' },
    { prop: 'visibility', label: '能见度', type: 'number' },
    { prop: 'solarRadiation', label: '太阳辐射', type: 'number' },
    { prop: 'solarEnergy', label: '太阳能量', type: 'number' },
    { prop: 'uvIndex', label: '紫外线指数', type: 'number' },
    { prop: 'hdd', label: '采暖度日(HDD)', type: 'number', helperText: '留空时按 max(18 - 日平均温度, 0) 自动计算。' },
    { prop: 'sourceCode', label: '数据来源', required: true, maxLength: 32 },
    { prop: 'qualityStatus', label: '数据质量', type: 'select', options: weatherQualityOptions, required: true }
  ],
  emptyForm: {
    regionCode: '', regionName: '', statDate: '', timeGranularity: 'DAY', weatherType: 'ACTUAL', weatherVersion: 'ACTUAL', forecastIssueTime: '',
    tempMax: null, tempMin: null, tempAvg: null, feelsLikeMax: null, feelsLikeMin: null, feelsLikeAvg: null,
    dewPoint: null, humidity: null, precipitation: null, windGust: null, windSpeed: null, seaLevelPressure: null,
    cloudCover: null, visibility: null, solarRadiation: null, solarEnergy: null, uvIndex: null, hdd: null,
    sourceCode: 'MANUAL', qualityStatus: 'VALID'
  },
  permissions: { create: 'data:daily-weather:create', update: 'data:daily-weather:update', delete: 'data:daily-weather:delete' }
}

export const customersDataConfig: BaseDataPageConfig = {
  title: '客户表管理',
  endpoint: '/base-customer',
  searchPlaceholder: '搜索客户名称',
  filterFields: [
    {
      prop: 'regionCode',
      label: '所属地区',
      type: 'select',
      placeholder: '请选择地区',
      optionSource: {
        endpoint: '/base-region',
        valueProp: 'regionCode',
        labelProp: 'regionName'
      },
      width: 160
    },
    {
      prop: 'industryCode',
      label: '所属行业',
      type: 'select',
      placeholder: '请选择行业',
      optionSource: {
        endpoint: '/base-industry',
        valueProp: 'industryCode',
        labelProp: 'industryName'
      },
      width: 160
    }
  ],
  tableFields: [
    { prop: 'customerName', label: '客户名称', minWidth: 180 },
    { prop: 'regionName', label: '区域名称', minWidth: 130 },
    { prop: 'industryName', label: '行业名称', minWidth: 130 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'customerName', label: '客户名称', required: true, maxLength: 120 },
    {
      prop: 'regionCode',
      label: '所属区域',
      type: 'select',
      required: true,
      optionSource: {
        endpoint: '/base-region',
        valueProp: 'regionCode',
        labelProp: 'regionName'
      },
      fillProps: { regionName: 'regionName' }
    },
    {
      prop: 'industryCode',
      label: '所属行业',
      type: 'select',
      required: true,
      optionSource: {
        endpoint: '/base-industry',
        valueProp: 'industryCode',
        labelProp: 'industryName'
      },
      fillProps: { industryName: 'industryName' }
    },
    { prop: 'rawRegionName', label: '原始区域' },
    { prop: 'rawIndustryName', label: '原始行业' }
  ],
  emptyForm: {
    customerName: '',
    regionCode: '',
    regionName: '',
    industryCode: '',
    industryName: '',
    rawRegionName: '',
    rawIndustryName: ''
  },
  permissions: { create: 'base:customer:create', update: 'base:customer:update', delete: 'base:customer:delete' }
}

export const industriesDataConfig: BaseDataPageConfig = {
  title: '行业表管理',
  endpoint: '/base-industry',
  searchPlaceholder: '搜索行业名称',
  tableFields: [
    { prop: 'industryName', label: '行业名称', minWidth: 160 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [{ prop: 'industryName', label: '行业名称', required: true, maxLength: 120 }],
  emptyForm: { industryName: '' },
  permissions: { create: 'base:industry:create', update: 'base:industry:update', delete: 'base:industry:delete' }
}

export const fileInfoDataConfig: BaseDataPageConfig = {
  title: '原始数据文件管理',
  endpoint: '/data-file-info',
  searchPlaceholder: '搜索文件编码、文件名、存储路径、Hash、状态、创建人',
  tableFields: [
    { prop: 'fileCode', label: '文件编码', minWidth: 130 },
    { prop: 'fileName', label: '文件名称', minWidth: 220 },
    {
      prop: 'status',
      label: '处理状态',
      minWidth: 110,
      displayType: 'tag',
      enumMap: { UPLOADED: '已上传', PROCESSING: '处理中', SUCCESS: '处理成功', FAILED: '处理失败' },
      tagTypeMap: { UPLOADED: 'info', PROCESSING: 'warning', SUCCESS: 'success', FAILED: 'danger' }
    },
    { prop: 'totalCount', label: '数据条数', minWidth: 110 },
    { prop: 'objectKey', label: '存储路径', minWidth: 260 },
    { prop: 'fileHash', label: '文件Hash', minWidth: 180, displayType: 'hash' },
    { prop: 'errorMessage', label: '异常信息', minWidth: 220 },
    { prop: 'createdByName', label: '创建人', minWidth: 120 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'fileName', label: '文件名称', required: true },
    { prop: 'objectKey', label: '存储路径', required: true },
    { prop: 'fileHash', label: '文件Hash', required: true },
    { prop: 'status', label: '处理状态', type: 'select', options: fileStatusOptions, required: true },
    { prop: 'totalCount', label: '数据条数', type: 'number', min: 0 },
    { prop: 'errorMessage', label: '异常信息', inputType: 'textarea' }
  ],
  emptyForm: {
    fileName: '',
    objectKey: '',
    fileHash: '',
    status: 'UPLOADED',
    totalCount: 0,
    errorMessage: ''
  },
  permissions: { create: 'data:file-info:create', update: 'data:file-info:update', delete: 'data:file-info:delete' }
}
