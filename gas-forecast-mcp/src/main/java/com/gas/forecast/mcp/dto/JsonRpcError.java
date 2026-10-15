package com.gas.forecast.mcp.dto;

/**
 * JSON-RPC协议错误。
 *
 * @param code
 *            JSON-RPC标准错误码
 * @param message
 *            协议层错误说明
 * @param data
 *            项目统一业务错误对象等补充信息
 */
public record JsonRpcError(int code, String message, Object data) {
}
