package com.gas.forecast.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.mcp.dto.JsonRpcError;
import com.gas.forecast.mcp.dto.JsonRpcRequest;
import com.gas.forecast.mcp.dto.JsonRpcResponse;
import com.gas.forecast.mcp.dto.McpToolCallRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gas.forecast.mcp", name = "enabled", havingValue = "true")
@RequirePermission("ai:assistant:use")
/**
 * 天然气预测MCP的HTTP入口。处理JSON-RPC初始化、心跳、工具发现和工具调用。
 * 端点沿用系统现有认证拦截器，并要求ai:assistant:use权限。
 */
public class McpServerController {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpServerController.class);
    /** 当前服务声明支持的MCP协议版本。 */
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private final McpToolCatalog catalog;
    private final McpToolDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    /**
     * MCP统一入口。通知消息返回202；请求消息返回带相同id的JSON-RPC响应。
     *
     * @param request
     *            JSON-RPC请求体
     * @param protocolVersion
     *            客户端声明的MCP协议版本
     */
    @PostMapping(path = "${gas.forecast.mcp.endpoint:/mcp}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcResponse<?>> handle(
            @Valid @RequestBody JsonRpcRequest request,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(value = "x-user", required = false) String userId,
            @RequestHeader(value = "x-traceId", required = false) String traceId) {
        JsonNode id = request.id();
        String method = request.method();
        String effectiveTraceId = TextUtils.hasText(traceId) ? traceId : MDC.get("x-traceId");
        LOGGER.info("event=mcp_request_started user_id={} trace_id={} method={} request_id={}",
                TextUtils.hasText(userId) ? userId : "-", TextUtils.hasText(effectiveTraceId) ? effectiveTraceId : "-", method, id);
        if (!"2.0".equals(request.jsonrpc()))
            return jsonRpcError(id, -32600, "INVALID_REQUEST", "JSON-RPC版本必须为2.0");
        if (method.startsWith("notifications/"))
            return ResponseEntity.accepted().body(null);
        if (!"initialize".equals(method) && protocolVersion != null && !PROTOCOL_VERSION.equals(protocolVersion))
            return jsonRpcError(id, -32602, "UNSUPPORTED_PROTOCOL_VERSION", "不支持的MCP协议版本：" + protocolVersion);
        ResponseEntity<JsonRpcResponse<?>> response = switch (method) {
            case "initialize" -> initialize(id);
            case "ping" -> jsonRpcResult(id, Map.of());
            case "tools/list" -> jsonRpcResult(id, Map.of("tools", catalog.list()));
            case "tools/call" -> callTool(id, request.params());
            default -> jsonRpcError(id, -32601, "METHOD_NOT_FOUND", "不支持的方法：" + method);
        };
        LOGGER.info("event=mcp_request_completed user_id={} trace_id={} method={} request_id={} status={}",
                TextUtils.hasText(userId) ? userId : "-", TextUtils.hasText(effectiveTraceId) ? effectiveTraceId : "-", method, id, response.getStatusCode().value());
        return response;
    }

    /** 保留协议单元测试和内部调用使用的简化入口。 */
    ResponseEntity<JsonRpcResponse<?>> handle(JsonRpcRequest request, String protocolVersion) {
        return handle(request, protocolVersion, null, null);
    }

    /** 完成MCP能力协商，并为客户端生成会话标识。 */
    private ResponseEntity<JsonRpcResponse<?>> initialize(JsonNode id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of("name", "gas-forecast-business-server", "version", "1.0.0"));
        result.put("instructions", "天然气预测业务MCP服务。查询可直接调用；批量训练和批量预测必须先取得用户确认。工具失败时必须读取structuredContent中的errorCode、data.reason、data.nextAction和retryable决定下一步，不得编造成功结果或盲目重试。");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Mcp-Session-Id", UUID.randomUUID().toString());
        return new ResponseEntity<>(JsonRpcResponse.success(id, result), headers, HttpStatus.OK);
    }

    /** 校验工具名称与参数，执行工具并同时返回文本和结构化结果。 */
    private ResponseEntity<JsonRpcResponse<?>> callTool(JsonNode id, JsonNode params) {
        if (params == null || params.isNull() || !params.isObject())
            return jsonRpcError(id, -32602, "INVALID_ARGUMENT", "tools/call缺少params或params不是对象");
        McpToolCallRequest request;
        try {
            request = objectMapper.treeToValue(params, McpToolCallRequest.class);
        } catch (JsonProcessingException exception) {
            return jsonRpcError(id, -32602, "INVALID_ARGUMENT", "tools/call参数格式错误");
        }
        String name = request.name();
        if (!TextUtils.hasText(name))
            return jsonRpcError(id, -32602, "INVALID_ARGUMENT", "tools/call缺少工具名称");
        try {
            catalog.require(name);
        } catch (IllegalArgumentException exception) {
            return jsonRpcError(id, -32602, "TOOL_NOT_FOUND", exception.getMessage());
        }
        Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
        McpToolResult toolResult = dispatcher.invoke(name, arguments);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", toolResult.message())));
        result.put("isError", !toolResult.success());
        result.put("structuredContent", toolResult);
        return jsonRpcResult(id, result);
    }

    /** 构造成功的JSON-RPC 2.0响应。 */
    private ResponseEntity<JsonRpcResponse<?>> jsonRpcResult(JsonNode id, Object result) {
        return ResponseEntity.ok(JsonRpcResponse.success(id, result));
    }

    /** 构造协议级错误；ResponseResult仅作为error.data，不能包裹JSON-RPC最外层。 */
    private ResponseEntity<JsonRpcResponse<?>> jsonRpcError(JsonNode id, int rpcCode, String businessCode, String message) {
        ResponseResult<Void> businessError = ResponseResult.error(businessCode, message);
        JsonRpcError error = new JsonRpcError(rpcCode, message, businessError);
        return ResponseEntity.badRequest().body(JsonRpcResponse.failure(id, error));
    }
}
