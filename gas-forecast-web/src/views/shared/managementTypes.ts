export type SystemFieldType = 'text' | 'number' | 'select' | 'tree' | 'treeSelect' | 'radio' | 'switch'

export interface Option {
  label: string
  value: number | string
}

export type FieldDisplayType = 'text' | 'array' | 'enabled' | 'status' | 'permissionType' | 'hash' | 'tag' | 'date' | 'datetime'

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
  multiple?: boolean
  optionKey?: string
  options?: Option[]
  inputType?: string
  labelKey?: string
  required?: boolean
  maxLength?: number
  pattern?: string
  activeValue?: number | string | boolean
  inactiveValue?: number | string | boolean
  activeText?: string
  inactiveText?: string
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
  }
}

export type BaseDataFieldType = 'text' | 'number' | 'select'

export interface BaseDataFieldConfig {
  prop: string
  label: string
  type?: BaseDataFieldType
  inputType?: string
  min?: number
  minWidth?: number
  options?: Option[]
  multiple?: boolean
  optionKey?: string
  labelKey?: string
  required?: boolean
  maxLength?: number
  pattern?: string
  activeValue?: number | string | boolean
  inactiveValue?: number | string | boolean
  activeText?: string
  inactiveText?: string
  displayType?: FieldDisplayType
  enumMap?: Record<string, string>
  tagTypeMap?: Record<string, string>
  align?: 'left' | 'center' | 'right'
  width?: number
}

export interface BaseDataPageConfig {
  title: string
  endpoint: string
  searchPlaceholder: string
  tableFields: BaseDataFieldConfig[]
  formFields: BaseDataFieldConfig[]
  emptyForm: Record<string, any>
  permissions?: {
    create?: string
    update?: string
    delete?: string
  }
}
