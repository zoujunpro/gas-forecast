package com.gas.forecast.business.dto;

import java.util.List;

/**
 * Java 调用 Python FastAPI 的内部请求对象。
 *
 * <p>
 * 所有编号都已由 Java 创建。Python 只负责编排，不得再次创建会话、消息或 Run。
 * </p>
 */
public record PythonAgentChatRequest(
        /** Java 已创建或校验的会话编号。 */
        String conversationNo,
        /** Java 生成的本轮对话标识。 */
        String turnId,
        /** Java 生成的 Agent Run 编号。 */
        String runNo,
        /** 用户本轮输入的自然语言消息。 */
        String message,
        /** 普通文本或对待处理交互的结构化回复。 */
        String inputType,
        /** 结构化回复对应的交互编号。 */
        String interactionId,
        /** 结构化回复所回应的助手轮次编号。 */
        String replyToTurnId,
        /** 会话绑定的智能体编码。 */
        String agentCode,
        /** 本轮消息发生时的当前业务焦点；不是会话绑定条件。 */
        AiChatRequest.BusinessContext businessContext, /** Java 统一加载的会话上下文，Python 不直接查询会话数据库。 */
        ConversationContext conversationContext) {

    public PythonAgentChatRequest {
        inputType = inputType == null ? "TEXT" : inputType;
        conversationContext = conversationContext == null ? new ConversationContext(null, null, List.of(), List.of()) : conversationContext;
    }

    /**
     * 供 Agent 使用的会话上下文。
     *
     * <p>
     * summary、pendingOperation 和 recentMessages 用途不同：
     * </p>
     * <ul>
     * <li>summary：较早对话压缩后的文本摘要；</li>
     * <li>pendingOperation：当前待澄清交互的恢复快照；</li>
     * <li>recentMessages：最近若干条未压缩的原始消息。</li>
     * </ul>
     */
    public record ConversationContext(
            /** 较早会话内容的压缩摘要；尚未生成时为空。 */
            String summary,
            /** 当前待澄清交互的恢复快照；普通对话为空。 */
            AgentPendingOperation pendingOperation,
            /** 最近原始消息，按创建时间正序排列，不包含本轮用户消息。 */
            List<RecentMessage> recentMessages,
            /** 当前会话最近的预测任务，按任务时间从新到旧排列。 */
            List<RecentForecastTask> recentForecastTasks) {
        public ConversationContext {
            recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
            recentForecastTasks = recentForecastTasks == null ? List.of() : List.copyOf(recentForecastTasks);
        }
    }

    /** Java从数据库读取的一条最近原始消息。 */
    public record RecentMessage(String role, String content) {
    }

    /** 会话中一个预测任务及其成功产生的预测明细。 */
    public record RecentForecastTask(String taskNo, String status, List<ForecastDetail> details) {
        public RecentForecastTask {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

    /** 一个最小预测目标对应的结果编号。 */
    public record ForecastDetail(String detailNo, String forecastNo, String agentCode, String status) {
    }
}
