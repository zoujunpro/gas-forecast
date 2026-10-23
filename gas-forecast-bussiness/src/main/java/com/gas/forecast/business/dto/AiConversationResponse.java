package com.gas.forecast.business.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import java.util.List;

/** 会话列表及详情响应，字段与新的会话、消息、Run、业务引用模型一致。 */
public record AiConversationResponse(
        /** 会话唯一编号。 */
        String conversationNo,
        /** 会话标题。 */
        String title,
        /** 会话绑定的智能体编码。 */
        String agentCode,
        /** 会话当前业务焦点；可随页面或用户指令变化，不是会话绑定条件。 */
        AiChatRequest.BusinessContext currentBusinessContext,
        /** 会话状态：ACTIVE 或 ARCHIVED。 */
        String status,
        /** 最近一条消息的创建时间。 */
        Date lastMessageAt,
        /** 会话最后更新时间。 */
        Date updatedAt,
        /** 会话详情中的历史消息；列表接口返回空列表。 */
        List<Message> messages) {

    public AiConversationResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /** 一条历史消息及其所属 Agent Run 和业务对象引用。 */
    public record Message(
            /** 消息数据库主键。 */
            Long id,
            /** 对话轮次编号，同轮用户和助手消息相同。 */
            String turnId,
            /** 前端展示角色，统一为小写 user、assistant、system 或 tool。 */
            String role,
            /** 消息正文。 */
            String content,
            /** 消息分类或来源 Skill 编码。 */
            String messageType,
            /** 该轮关联的 Agent Run 编号。 */
            String runNo,
            /** Agent Run 当前状态，不代表后台训练预测任务状态。 */
            String runStatus,
            /** 该轮等待补充信息时的结构化提示。 */
            AiChatResponse.Clarification clarification,
            /** 前端展示结构。 */
            JsonNode presentation,
            /** 本轮工具真实结果。 */
            JsonNode executionResult,
            /** 当前消息关联的一个或多个业务对象。 */
            List<AiChatResponse.Reference> references,
            /** 消息创建时间。 */
            Date createdAt) {

        public Message {
            references = references == null ? List.of() : List.copyOf(references);
        }
    }
}
