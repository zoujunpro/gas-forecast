---
name: spring-vue-admin-scaffold
description: 当用户要新建、复制、重建或快速搭建 Java Spring Boot 多模块后端和 Vue 前端管理系统脚手架时使用。覆盖 Maven 模块、基础包结构、公共响应、异常处理、安全、DAO、启动模块和前端工程骨架。
---

# Spring Vue Admin 项目脚手架

## 使用场景

用户要求搭建新项目、初始化项目骨架、复制 Spring Boot + Vue 管理系统架构、生成基础模块、生成前后端基础工程时使用本 skill。

## 技术栈

- 后端使用 Java 21、Spring Boot 3.2.x、Maven 多模块。
- ORM 使用 MyBatis Plus。
- 数据库默认 MySQL。
- 前端使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios。
- 项目默认使用 UTF-8。

## 推荐模块结构

后端根工程使用 `pom` 聚合，模块按职责拆分：

- `*-common`：公共能力聚合模块。
- `*-common-core`：统一响应、异常、分页、上下文、通用工具。
- `*-common-web`：全局异常处理、日志切面、Web 通用配置。
- `*-common-security`：认证拦截、权限判断、安全配置。
- `*-common-cache`：缓存能力。
- `*-dao`：实体、Mapper、MyBatis Plus 配置、SQL 资源。
- `*-system`：登录、用户、角色、部门、权限等系统管理。
- `*-bussiness` 或 `*-business`：业务功能模块。新项目优先使用拼写正确的 `business`。
- `*-gateway`：网关或统一入口相关能力。
- `*-startup`：Spring Boot 启动模块，负责装配依赖和运行应用。
- `*-web`：前端工程。

## 后端基础能力

脚手架应包含：

- `ResponseCode` 接口。
- `BusinessResponseCode` 枚举。
- `ResponseResult<T>` 统一响应。
- `BusinessException` 业务异常。
- `GlobalExceptionHandler` 全局异常处理。
- `PageInfoDTO<T>` 分页响应。
- `WebLog` 注解和日志切面。
- `ThreadLocalUtil` 或等价登录上下文工具。
- MyBatis Plus 分页插件配置。
- Spring Boot 启动类。

## 异常码规则

- `0000` 表示成功。
- 普通手动抛出的业务异常使用 `BusinessResponseCode.SYSTEM_ERROR`，并传入自定义中文信息。
- 登录、认证、安全类异常可以保留专用业务码或 HTTP 风格码：
  - 登录失败：`4000`
  - 登录加密相关：`4001-4003`
  - 未登录：`401`
  - 无权限、用户禁用：`403`
- 参数绑定、JSON 解析、请求方法不支持等框架异常由全局异常处理器转换。
- 不主动新增字符串形式的 `"400"`、`"404"` 业务异常码，除非用户明确要求。

示例：

```java
throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "权限不存在");
```

## 前端基础骨架

前端脚手架应包含：

- `src/main.ts`
- `src/App.vue`
- `src/router/index.ts`
- `src/api`
- `src/components`
- `src/composables`
- `src/views`
- `src/utils/http.ts`
- `src/types`
- 全局样式和设计变量文件，例如 `src/styles/tokens.css`

接口响应按后端 `code`、`message`、`data` 处理。错误提示优先展示后端返回的 `message`。

## 生成流程

1. 先确认项目名、包名、数据库类型、是否需要登录权限模块。
2. 生成 Maven 根工程和后端模块。
3. 生成公共响应、异常、日志、安全、DAO 基础代码。
4. 生成启动模块和配置文件。
5. 生成前端 Vite 工程和基础布局。
6. 运行最小可验证命令。

## 验证命令

后端优先运行：

```bash
mvn compile
```

前端优先运行：

```bash
npm run typecheck
npm run build
```

如果只改某个后端模块，优先运行：

```bash
mvn -pl 模块名 -am compile
```
