import type { SystemPageConfig } from '../shared/managementTypes'

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 }
]

const permissionTypeOptions = [
  { label: '目录', value: 'DIRECTORY' },
  { label: '菜单', value: 'MENU' },
  { label: '按钮', value: 'BUTTON' }
]

export const usersSystemConfig: SystemPageConfig = {
  title: '用户管理',
  mode: 'users',
  endpoint: '/system/users',
  placeholder: '搜索用户名、姓名、手机',
  paged: true,
  tableFields: [
    { prop: 'username', label: '用户名', minWidth: 120 },
    { prop: 'realName', label: '姓名', minWidth: 120 },
    { prop: 'roleNames', label: '角色', minWidth: 160, displayType: 'array' },
    { prop: 'departmentNames', label: '部门', minWidth: 160, displayType: 'array' },
    { prop: 'phone', label: '手机', minWidth: 130 },
    { prop: 'status', label: '状态', minWidth: 90, displayType: 'status' }
  ],
  formFields: [
    { prop: 'username', label: '用户名', required: true, maxLength: 50 },
    { prop: 'password', label: '密码', inputType: 'password' },
    { prop: 'realName', label: '姓名', required: true, maxLength: 50 },
    { prop: 'phone', label: '手机' },
    { prop: 'email', label: '邮箱' },
    { prop: 'orgCode', label: '机构编码' },
    { prop: 'roleIds', label: '角色', type: 'select', multiple: true, optionKey: 'roles' },
    { prop: 'departmentIds', label: '部门', type: 'select', multiple: true, optionKey: 'departments' },
    { prop: 'status', label: '状态', type: 'select', options: statusOptions }
  ],
  emptyForm: {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    orgCode: 'GAS',
    roleIds: [],
    departmentIds: [],
    status: 1
  },
  permissions: { create: 'system:user:create', update: 'system:user:update', delete: 'system:user:delete' }
}

export const rolesSystemConfig: SystemPageConfig = {
  title: '角色权限',
  mode: 'roles',
  endpoint: '/system/roles',
  placeholder: '搜索角色编码、名称',
  paged: true,
  tableFields: [
    { prop: 'roleCode', label: '角色编码', minWidth: 140 },
    { prop: 'roleName', label: '角色名称', minWidth: 140 },
    { prop: 'description', label: '描述', minWidth: 220 }
  ],
  formFields: [
    { prop: 'roleCode', label: '角色编码', required: true, maxLength: 64 },
    { prop: 'roleName', label: '角色名称', required: true, maxLength: 80 },
    { prop: 'description', label: '描述' },
    { prop: 'permissionIds', label: '菜单权限', type: 'tree' }
  ],
  emptyForm: { roleCode: '', roleName: '', description: '', permissionIds: [] },
  permissions: { create: 'sys:role:save', update: 'sys:role:save', delete: 'sys:role:delete' }
}

export const departmentsSystemConfig: SystemPageConfig = {
  title: '部门管理',
  mode: 'departments',
  endpoint: '/system/departments',
  placeholder: '搜索部门名称、机构编码',
  paged: false,
  tableFields: [
    { prop: 'departmentName', label: '部门名称', minWidth: 180 },
    { prop: 'orgCode', label: '机构编码', minWidth: 130 },
    { prop: 'status', label: '状态', minWidth: 90, displayType: 'status' }
  ],
  formFields: [
    { prop: 'departmentName', label: '部门名称', required: true, maxLength: 80 },
    { prop: 'orgCode', label: '机构编码', required: true, maxLength: 64 },
    { prop: 'parentId', label: '上级部门', type: 'treeSelect', optionKey: 'departments', labelKey: 'departmentName' },
    { prop: 'sortNo', label: '排序', type: 'number' },
    { prop: 'status', label: '状态', type: 'select', options: statusOptions }
  ],
  emptyForm: { departmentName: '', orgCode: '', parentId: undefined, sortNo: 0, status: 1 },
  permissions: { create: 'sys:department:save', update: 'sys:department:save', delete: 'sys:department:delete' }
}

export const permissionsSystemConfig: SystemPageConfig = {
  title: '菜单管理',
  mode: 'permissions',
  endpoint: '/system/permissions',
  placeholder: '请输入菜单名称',
  paged: false,
  tableFields: [
    { prop: 'permissionName', label: '菜单名称', minWidth: 210 },
    { prop: 'permissionType', label: '类型', minWidth: 90, displayType: 'permissionType' },
    { prop: 'path', label: '路由路径', minWidth: 170 },
    { prop: 'component', label: '组件', minWidth: 140 },
    { prop: 'perms', label: '权限码', minWidth: 180 },
    { prop: 'buttonCode', label: '按钮标识', minWidth: 130 },
    { prop: 'status', label: '状态', minWidth: 90, displayType: 'status' },
    { prop: 'sortNo', label: '排序', minWidth: 80, align: 'right' }
  ],
  formFields: [
    { prop: 'permissionName', label: '名称', required: true, maxLength: 80 },
    { prop: 'uiPermissionKind', label: '类型', type: 'radio', options: permissionTypeOptions },
    { prop: 'parentId', label: '上级菜单', type: 'treeSelect', optionKey: 'permissions', labelKey: 'permissionName' },
    { prop: 'path', label: '路由路径' },
    { prop: 'component', label: '组件' },
    { prop: 'perms', label: '权限码' },
    { prop: 'buttonCode', label: '按钮标识' },
    { prop: 'icon', label: '图标' },
    { prop: 'sortNo', label: '排序', type: 'number' },
    {
      prop: 'hidden',
      label: '侧边栏显示',
      type: 'switch',
      activeValue: 0,
      inactiveValue: 1,
      activeText: '显示',
      inactiveText: '隐藏'
    },
    {
      prop: 'status',
      label: '启用状态',
      type: 'switch',
      activeValue: 1,
      inactiveValue: 0,
      activeText: '启用',
      inactiveText: '停用'
    }
  ],
  emptyForm: {
    permissionName: '',
    uiPermissionKind: 'DIRECTORY',
    permissionType: 'DIRECTORY',
    parentId: undefined,
    path: '',
    component: '',
    perms: '',
    buttonCode: '',
    icon: '',
    sortNo: 0,
    hidden: 0,
    status: 1
  },
  permissions: { create: 'sys:permission:save', update: 'sys:permission:save', delete: 'sys:permission:delete' }
}
