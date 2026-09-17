---
name: spring-admin-backend-generator
description: 当用户要在 Spring Boot 多模块管理系统中生成或修改后端 Java 代码时使用。覆盖 Controller、Service、ServiceImpl、DTO、Entity、Mapper、CRUD、分页、异常码、中文提示、MyBatis Plus 和 Maven 验证方式。
---

# Spring Admin 后端代码生成

## 使用场景

用户要求生成后端接口、CRUD、业务模块、DTO、Service、Mapper、实体、分页查询、删除接口、系统管理接口或业务管理接口时使用本 skill。

## 包和模块约定

- 系统管理相关代码放在 `*-system` 模块。
- 业务功能相关代码放在 `*-business` 模块。兼容已有项目中的 `*-bussiness` 拼写。
- 数据库实体和 Mapper 放在 `*-dao` 模块。
- 公共类型、异常、分页、工具放在 `*-common` 下对应子模块。
- Java 包名沿用目标项目已有根包名。

## 常见代码路径

- Controller：`src/main/java/com/gas/forecast/*/controller`
- 请求 DTO：`src/main/java/com/gas/forecast/*/dto/req`
- 响应 DTO：`src/main/java/com/gas/forecast/*/dto/resp`
- Service 接口：`src/main/java/com/gas/forecast/*/service`
- Service 实现：`src/main/java/com/gas/forecast/*/service/impl`
- Entity：`*-dao/src/main/java/.../dao/domain`
- Mapper：`*-dao/src/main/java/.../dao/mapper`

## Controller 规则

- 使用 `@RestController`。
- 使用模块级 `@RequestMapping`。
- 方法返回 `ResponseResult<T>`。
- 成功响应使用 `ResponseResult.success(data)`。
- 用户可见接口名和日志信息使用中文。
- 如果当前模块已有 `@WebLog`，新增接口也保持使用。

示例：

```java
@WebLog("分页查询客户")
@PostMapping("/page")
public ResponseResult<PageInfoDTO<CustomerRespDTO>> page(@Valid @RequestBody CustomerPageReqDTO reqDTO) {
    return ResponseResult.success(customerService.page(reqDTO));
}
```

## Service 规则

- 默认生成接口和 `impl` 实现。
- 实现类使用 `@Service`。
- 涉及多表写入、增删改时使用 `@Transactional`。
- Mapper 通过构造器注入。
- 查询条件优先使用 MyBatis Plus `Wrappers.lambdaQuery()`。
- 分页响应使用项目已有 `PageInfoDTO` 或业务模块已有分页工具。
- 修改已有文件时，优先沿用该文件已有私有辅助方法和写法。

## DTO 规则

- 请求对象放 `dto.req`。
- 响应对象放 `dto.resp`。
- 字段命名使用 Java 驼峰。
- 前后端交互字段保持语义清晰，不暴露数据库无关内部细节。
- 校验注解使用 Jakarta Validation，例如 `@NotBlank`、`@NotNull`、`@Size`。
- 校验 message 使用中文。

## Entity 和 Mapper 规则

- Entity 类名通常以 `Tb` 结尾，与当前项目保持一致。
- Mapper 类名通常以 `TbMapper` 结尾。
- Mapper 继承 `BaseMapper<Entity>`。
- Entity 注解和字段风格沿用相邻已有实体。
- 不手写 SQL，除非当前需求或已有模式明确需要。

## 异常规则

- 普通手动抛出的业务异常统一使用 `BusinessResponseCode.SYSTEM_ERROR`，并传入自定义中文信息。
- 不新增 `"400"`、`"404"` 这类字符串异常码。
- 登录、认证、安全相关代码保持已有专用码：
  - 登录失败使用 `BusinessResponseCode.LOGIN_FAILED`。
  - 登录加密相关使用 `4001-4003` 对应枚举。
  - 未登录可以使用 `401`。
  - 无权限或禁用可以使用 `403`。

示例：

```java
throw new BusinessException(BusinessResponseCode.SYSTEM_ERROR, "客户不存在");
```

## CRUD 生成流程

1. 明确表名、实体名、接口路径、字段列表和是否需要逻辑删除。
2. 生成或补齐 Entity、Mapper。
3. 生成请求 DTO 和响应 DTO。
4. 生成 Service 接口。
5. 生成 ServiceImpl。
6. 生成 Controller。
7. 如果前端也需要，交给前端生成规则继续处理。
8. 运行相关模块编译。

## 验证

只改业务模块时优先运行：

```bash
mvn -pl 业务模块名 -am compile
```

只改系统模块时优先运行：

```bash
mvn -pl 系统模块名 -am compile
```

改 DAO 或公共模块时，根据依赖范围运行包含调用方的 `-pl ... -am compile`。
