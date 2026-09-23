export type SystemFieldType = 'text' | 'number' | 'select' | 'tree' | 'treeSelect' | 'radio' | 'switch'

export interface Option {
  label: string
  value: number | string
  raw?: Record<string, any>
}

export type FieldDisplayType =
  'text' | 'array' | 'enabled' | 'status' | 'permissionType' | 'hash' | 'tag' | 'date' | 'datetime'

export interface DisplayFieldConfig {
  displayType?: FieldDisplayType
  enumMap?: Record<string, string>
  tagTypeMap?: Record<string, string>
  align?: 'left' | 'center' | 'right'
  width?: number
}

export interface SystemFieldConfig {
  prop: string
  label: string
  type?: SystemFieldType
  min?: number
  minWidth?: number
  tooltip?: string
  multiple?: boolean
  optionKey?: string
  options?: Option[]
  inputType?: string
  labelKey?: string
  required?: boolean
  maxLength?: number
  placeholder?: string
  rows?: number
  showWordLimit?: boolean
  pattern?: string
  activeValue?: number | string | boolean
  inactiveValue?: number | string | boolean
  activeText?: string
  inactiveText?: string
  readonly?: boolean
  displayType?: FieldDisplayType
  enumMap?: Record<string, string>
  tagTypeMap?: Record<string, string>
  align?: 'left' | 'center' | 'right'
  width?: number
}

export interface SystemPageConfig {
  title: string
  mode: 'users' | 'roles' | 'departments' | 'permissions'
  endpoint: string
  placeholder: string
  paged: boolean
  tableFields: SystemFieldConfig[]
  formFields: SystemFieldConfig[]
  emptyForm: Record<string, any>
  permissions?: {
    create?: string
    update?: string
    delete?: string
    execute?: string
  }
  trainExecution?: boolean
}

export type BaseDataFieldType = 'text' | 'number' | 'select' | 'radio' | 'granularityDate'
export type BaseDataFilterType = 'text' | 'select' | 'dateRange'

export interface BaseDataFieldConfig {
  prop: string
  label: string
  type?: BaseDataFieldType
  inputType?: string
  min?: number
  minWidth?: number
  tooltip?: string
  options?: Option[]
  multiple?: boolean
  optionKey?: string
  labelKey?: string
  required?: boolean
  maxLength?: number
  placeholder?: string
  helperText?: string
  fullWidth?: boolean
  rows?: number
  showWordLimit?: boolean
  pattern?: string
  activeValue?: number | string | boolean
  inactiveValue?: number | string | boolean
  activeText?: string
  inactiveText?: string
  readonly?: boolean
  displayType?: FieldDisplayType
  enumMap?: Record<string, string>
  tagTypeMap?: Record<string, string>
  align?: 'left' | 'center' | 'right'
  width?: number
  sortable?: boolean | 'custom'
  optionSource?: {
    endpoint: string
    valueProp: string
    labelProp: string
    labelTemplate?: 'nameWithCode'
    size?: number
  }
  granularityProp?: string
  fillProps?: Record<string, string>
  visibleWhen?: {
    prop: string
    value: number | string | boolean
  }
}

export interface BaseDataFilterConfig {
  prop: string
  label: string
  type?: BaseDataFilterType
  placeholder?: string
  options?: Option[]
  width?: number
  startProp?: string
  endProp?: string
}

export interface BaseDataPageConfig {
  title: string
  pageDescription?: string
  endpoint: string
  searchPlaceholder: string
  readonly?: boolean
  trainExecution?: boolean
  dialogWidth?: string
  dialogDescription?: string
  dialogVariant?: 'feature-definition' | 'model-training'
  filterFields?: BaseDataFilterConfig[]
  featureDetailProp?: string
  tableFields: BaseDataFieldConfig[]
  formFields: BaseDataFieldConfig[]
  emptyForm: Record<string, any>
  permissions?: {
    create?: string
    update?: string
    delete?: string
    execute?: string
  }
}
