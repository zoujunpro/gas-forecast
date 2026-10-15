package com.gas.forecast.mcp.dto;

import java.util.Map;

/**
 * MCP tools/call方法的参数。
 *
 * @param name
 *            tools/list公布的工具名称
 * @param arguments
 *            具体工具的业务参数
 */
public record McpToolCallRequest(String name, Map<String, Object> arguments) {
}
