---
name: vue-admin-frontend-generator
description: 当用户要在 Vue 3、TypeScript、Vite、Element Plus 管理系统中生成或修改前端代码时使用。覆盖管理页、CRUD 页面、API 封装、路由、表格、表单、弹窗、权限按钮、中文提示和构建验证。
---

# Vue Admin 前端代码生成

## 使用场景

用户要求生成前端页面、管理页、CRUD 页面、接口调用、路由、菜单页面、表格筛选、弹窗表单或对接后端接口时使用本 skill。

## 技术栈

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Element Plus
- `@element-plus/icons-vue`
- Axios

## 项目路径

- API：`src/api`
- 组件：`src/components`
- 组合函数：`src/composables`
- 路由：`src/router/index.ts`
- 页面：`src/views`
- 类型：`src/types`
- HTTP 工具：`src/utils/http.ts`
- 样式变量：`src/styles/tokens.css`

## 页面生成规则

- 优先复用已有通用组件：
  - `CrudManagementPage.vue`
  - `AppTable.vue`
  - `AppTablePanel.vue`
  - `AppPagination.vue`
  - `AppDialog.vue`
  - `PermissionButton.vue`
  - `FormFieldRenderer.vue`
  - `ManagementTableCell.vue`
  - `PageBreadcrumb.vue`
- 标准后台管理页优先通过配置生成页面，而不是复制整套表格和弹窗逻辑。
- 系统管理类页面参考 `views/system` 下已有实现。
- 通用管理配置参考 `views/shared/managementTypes.ts` 和已有配置文件。
- 不主动重做整体视觉设计，不创建营销页。
- 文案、按钮、提示语使用中文。

## API 规则

- API 调用放到 `src/api`。
- 复用已有 HTTP 封装。
- 响应按 `code`、`message`、`data` 处理。
- 错误提示优先显示后端返回的 `message`。
- 不在页面里硬编码重复请求逻辑，已有通用 API 方法可复用时优先复用。

## 表格和表单规则

- 管理页默认包含：
  - 查询输入框
  - 查询按钮
  - 重置按钮
  - 新增按钮
  - 表格
  - 分页
  - 编辑操作
  - 删除操作
  - 新增/编辑弹窗
- 表单校验提示使用中文。
- 删除操作使用确认弹窗。
- 成功提示使用 `ElMessage.success`。
- 失败提示展示捕获到的错误 message。

## 权限规则

- 有权限码时，按钮使用 `PermissionButton`。
- 新增、编辑、删除分别配置权限。
- 不绕过已有前端权限控制。

## 样式规则

- 沿用现有后台风格和 CSS 变量。
- 不新增大面积渐变、营销式 hero、装饰性卡片。
- 后台页面以信息密度、可扫描性和稳定布局为主。
- 表格、工具栏、弹窗表单尺寸要稳定，避免文字溢出和布局跳动。
- 图标按钮优先使用 `@element-plus/icons-vue`。

## 生成流程

1. 先确认后端接口路径、字段、权限码和页面放置位置。
2. 检查是否能用已有 `CrudManagementPage` 配置生成。
3. 生成或修改 API 封装。
4. 生成页面配置或页面组件。
5. 补充路由和必要菜单关联代码。
6. 运行类型检查或构建。

## 验证

前端改动后优先运行：

```bash
npm run typecheck
```

影响构建或路由时运行：

```bash
npm run build
```

命令在前端工程目录下执行。
