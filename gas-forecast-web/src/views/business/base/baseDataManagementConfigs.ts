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
  searchPlaceholder: '搜索区域编码、名称、备注',
  tableFields: [
    { prop: 'regionCode', label: '区域编码', minWidth: 180 },
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

export const customersDataConfig: BaseDataPageConfig = {
  title: '客户表管理',
  endpoint: '/base-customer',
  searchPlaceholder: '搜索客户、区域、行业',
  tableFields: [
    { prop: 'customerCode', label: '客户编码', minWidth: 180 },
    { prop: 'customerName', label: '客户名称', minWidth: 180 },
    { prop: 'regionCode', label: '区域编码', minWidth: 180 },
    { prop: 'regionName', label: '区域名称', minWidth: 130 },
    { prop: 'industryCode', label: '行业编码', minWidth: 180 },
    { prop: 'industryName', label: '行业名称', minWidth: 130 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'customerName', label: '客户名称', required: true, maxLength: 120 },
    { prop: 'regionName', label: '区域名称', required: true },
    { prop: 'industryName', label: '行业名称', required: true },
    { prop: 'rawRegionName', label: '原始区域' },
    { prop: 'rawIndustryName', label: '原始行业' }
  ],
  emptyForm: { customerName: '', regionName: '', industryName: '', rawRegionName: '', rawIndustryName: '' },
  permissions: { create: 'base:customer:create', update: 'base:customer:update', delete: 'base:customer:delete' }
}

export const industriesDataConfig: BaseDataPageConfig = {
  title: '行业表管理',
  endpoint: '/base-industry',
  searchPlaceholder: '搜索行业编码、名称',
  tableFields: [
    { prop: 'industryCode', label: '行业编码', minWidth: 180 },
    { prop: 'industryName', label: '行业名称', minWidth: 160 },
    { prop: 'createdAt', label: '创建时间', minWidth: 170, displayType: 'datetime' },
    { prop: 'updatedAt', label: '更新时间', minWidth: 170, displayType: 'datetime' }
  ],
  formFields: [
    { prop: 'industryName', label: '行业名称', required: true, maxLength: 120 }
  ],
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
    { prop: 'status', label: '处理状态', minWidth: 110, displayType: 'tag', enumMap: { UPLOADED: '已上传', PROCESSING: '处理中', SUCCESS: '处理成功', FAILED: '处理失败' }, tagTypeMap: { UPLOADED: 'info', PROCESSING: 'warning', SUCCESS: 'success', FAILED: 'danger' } },
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
    { prop: 'createdBy', label: '创建人账号' },
    { prop: 'createdByName', label: '创建人姓名' },
    { prop: 'errorMessage', label: '异常信息', inputType: 'textarea' }
  ],
  emptyForm: { fileName: '', objectKey: '', fileHash: '', status: 'UPLOADED', totalCount: 0, errorMessage: '', createdBy: '', createdByName: '' },
  permissions: { create: 'data:file:create', update: 'data:file:update', delete: 'data:file:delete' }
}
