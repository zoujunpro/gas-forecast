package com.gas.forecast.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * MCP使用的JSON-RPC 2.0请求。
 *
 * @param jsonrpc
 *            协议版本，必须是2.0
 * @param id
 *            请求标识，可为数字、字符串或空；通知消息通常没有id
 * @param method
 *            MCP方法，例如initialize、tools/list或tools/call
 * @param params
 *            方法参数，不同方法具有不同结构
 */
public record JsonRpcRequest(@NotBlank String jsonrpc, JsonNode id, @NotBlank String method, JsonNode params) {
    /** 是否是不要求JSON-RPC响应的通知消息。 */
    public boolean notification() {
        return id == null || id.isNull();
    }
}
