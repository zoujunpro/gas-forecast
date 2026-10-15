package com.gas.forecast.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0响应信封。result和error互斥，且必须位于MCP响应最外层。
 *
 * @param jsonrpc
 *            固定为2.0
 * @param id
 *            原样返回客户端的请求标识
 * @param result
 *            成功结果
 * @param error
 *            协议错误
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse<T>(String jsonrpc, JsonNode id, T result, JsonRpcError error) {
    public static <T> JsonRpcResponse<T> success(JsonNode id, T result) {
        return new JsonRpcResponse<>("2.0", id, result, null);
    }

    public static JsonRpcResponse<Void> failure(JsonNode id, JsonRpcError error) {
        return new JsonRpcResponse<>("2.0", id, null, error);
    }
}
