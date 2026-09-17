---
name: fullstack-crud-generator
description: 当用户要一次性生成 Spring Boot + Vue 管理系统风格的完整业务功能、全栈 CRUD、管理模块或从字段描述生成数据库、后端和前端代码时使用。负责协调脚手架、后端生成、前端生成、验证和交付总结。
---

# 全栈 CRUD 功能生成

## 使用场景

用户要求“生成一个模块”“生成完整管理功能”“从字段生成前后端 CRUD”“快速做一个业务功能”时使用本 skill。

本 skill 是流程协调 skill。具体后端代码遵守 `spring-admin-backend-generator`，具体前端代码遵守 `vue-admin-frontend-generator`，新项目骨架遵守 `spring-vue-admin-scaffold`。

## 输入信息

开始生成前尽量确认：

- 模块名称，例如客户管理、系统参数、合同管理。
- 所属模块：系统管理或业务模块。
- 表名。
- 字段列表。
- 哪些字段必填。
- 哪些字段可搜索。
- 哪些字段在表格展示。
- 是否需要分页。
- 是否需要新增、编辑、删除。
- 是否需要逻辑删除。
- 是否需要权限码。
- 是否需要前端页面。

如果用户已经给了足够字段信息，可以直接生成；缺少的信息按当前项目默认风格补齐。

## 默认生成内容

完整 CRUD 默认包含：

- 数据库实体 `*Tb`
- Mapper `*TbMapper`
- 创建请求 DTO
- 更新请求 DTO
- 删除请求 DTO 或按 ID 删除
- 分页请求 DTO
- 响应 DTO
- Service 接口
- ServiceImpl 实现
- Controller
- 前端 API
- 前端管理页面或配置
- 路由入口
- 必要的权限按钮配置

## 后端生成约定

- Controller 返回 `ResponseResult`。
- Service 使用接口和实现类。
- Mapper 使用 MyBatis Plus。
- 增删改使用 `@Transactional`。
- 普通业务异常使用：

```java
throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "自定义异常信息");
```

- 中文异常信息要具体，例如“客户不存在”“客户编码已存在”。
- 不新增 `"400"`、`"404"` 字符串异常码。

## 前端生成约定

- 管理页优先复用 `CrudManagementPage` 和配置化模式。
- API 封装放在 `src/api`。
- 表格、查询、分页、弹窗表单、删除确认要完整。
- 错误提示优先展示后端 `message`。
- 使用中文页面文案。
- 权限按钮使用 `PermissionButton`。

## 字段推导规则

如果用户只给中文字段，生成时按以下方式推导：

- 名称：`name`
- 编码：`code`
- 状态：`status`
- 备注：`remark`
- 排序：`sortNo`
- 创建时间：`createdAt`
- 更新时间：`updatedAt`
- 删除标识：`deleted`
- 联系人：`contactName`
- 联系电话：`contactPhone`
- 区域：`region`
- 行业：`industry`

推导字段前应结合已有实体命名，优先沿用当前项目已出现的字段名。

## 生成流程

1. 快速读取相邻模块的 Controller、Service、DTO、Entity、Mapper 和前端页面模式。
2. 明确生成文件清单。
3. 先生成 DAO 层。
4. 再生成后端 DTO、Service、Controller。
5. 再生成前端 API、页面配置、路由。
6. 搜索是否有漏掉的 import、路径、权限码或接口路径。
7. 运行后端编译。
8. 运行前端 typecheck 或 build。
9. 汇总改动文件、接口路径和验证结果。

## 验证命令

后端业务模块：

```bash
mvn -pl 业务模块名 -am compile
```

后端系统模块：

```bash
mvn -pl 系统模块名 -am compile
```

前端：

```bash
npm run typecheck
```

必要时：

```bash
npm run build
```

## 交付格式

完成后简要说明：

- 新增或修改了哪些文件。
- 新增了哪些接口。
- 前端页面入口在哪里。
- 跑了哪些验证命令，结果如何。
- 如果有未验证项，明确说明原因。
