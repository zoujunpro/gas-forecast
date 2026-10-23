package com.gas.forecast.business.ai.dto;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 调用 Python FastAPI 的内部请求对象。
 *
 * <p>所有编号都已由 Java 创建。Python 只负责编排，不得再次创建会话、消息或 Run。</p>
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
        /** 结构化澄清答案；选择类字段使用不透明候选 token。 */
        Map<String, Object> answers,
        /** 会话绑定的智能体编码。 */
        String agentCode,
        /** 本轮消息发生时的当前业务焦点；不是会话绑定条件。 */
        AiChatRequest.BusinessContext businessContext,
        /** Java 统一加载的会话上下文，Python 不直接查询会话数据库。 */
        ConversationContext conversationContext) {

    public PythonAgentChatRequest {
        inputType = inputType == null ? "TEXT" : inputType;
        answers = answers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
        conversationContext = conversationContext == null
                ? new ConversationContext(null, Map.of(), List.of())
                : conversationContext;
    }

    /**
     * 供 Agent 使用的会话上下文。
     *
     * <p>summary、memory 和 recentMessages 用途不同，不能混成历史消息：</p>
     * <ul>
     *     <li>summary：较早对话压缩后的文本摘要；</li>
     *     <li>memory：跨轮次保留的结构化业务参数和待处理状态；</li>
     *     <li>recentMessages：最近若干条未压缩的原始消息。</li>
     * </ul>
     */
    public record ConversationContext(
            /** 较早会话内容的压缩摘要；尚未生成时为空。 */
            String summary,
            /** 会话长期记忆，如已确认的客户、预测周期和待确认动作。 */
            Map<String, Object> memory,
            /** 最近原始消息，按创建时间正序排列，不包含本轮用户消息。 */
            List<RecentMessage> recentMessages) {
        public ConversationContext {
            memory = memory == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(memory));
            recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        }
    }

    /** Java 从数据库读取的一条最近原始消息及其业务引用。 */
    public record RecentMessage(
            /** 消息数据库主键。 */
            Long messageId,
            /** 消息所属对话轮次。 */
            String turnId,
            /** 消息角色：USER、ASSISTANT、SYSTEM 或 TOOL。 */
            String role,
            /** 消息正文。 */
            String content,
            /** 消息分类或来源 Skill 编码。 */
            String messageType,
            /** 消息创建时间。 */
            Date createdAt,
            /** 该消息关联的一个或多个业务对象。 */
            List<AiChatResponse.Reference> references) {
        public RecentMessage {
            references = references == null ? List.of() : List.copyOf(references);
        }
    }
}
