package com.gas.forecast.mcp;

/**
 * MCP业务工具的统一返回结构。协议层会同时把message作为文本内容返回，
 * 并把完整对象放入structuredContent，便于Python智能体按状态和错误码处理。
 *
 * @param success
 *            本次调用是否成功
 * @param status
 *            业务状态，例如COMPLETED、EXECUTING、NEED_INFORMATION
 * @param resultType
 *            通用结果类型，例如PAGE、ENTITY、TASK、PROGRESS、RESULT
 * @param errorCode
 *            稳定错误码；成功时为空
 * @param message
 *            可直接展示给用户的简短说明
 * @param retryable
 *            是否允许调用方自动重试
 * @param data
 *            结构化业务数据
 */
public record McpToolResult(boolean success, String status, String resultType, String errorCode, String message, boolean retryable, Object data) {
    /** 创建一个不允许自动重试的成功结果。 */
    public static McpToolResult success(String status, String message, Object data) {
        return success(status, "OBJECT", message, data);
    }

    public static McpToolResult success(String status, String resultType, String message, Object data) {
        return new McpToolResult(true, status, resultType, null, message, false, data);
    }

    public static McpToolResult failure(String status, String errorCode, String message, boolean retryable) {
        return new McpToolResult(false, status, "ERROR", errorCode, message, retryable, null);
    }

    /** 创建带结构化失败详情的结果，供大模型根据原因和建议动作决定下一步。 */
    public static McpToolResult failure(String status, String errorCode, String message, boolean retryable, Object data) {
        return new McpToolResult(false, status, "ERROR", errorCode, message, retryable, data);
    }
}
