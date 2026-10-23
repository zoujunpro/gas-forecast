package com.gas.forecast.business.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Python FastAPI 返回给 Java 的内部 Agent 执行结果。
 *
 * <p>Java 校验 conversationNo、turnId 和 runNo 后，使用该对象保存助手消息、
 * ai_message_ref_tb 引用并更新 agent_run_tb。resultData 仅作内部审计，不直接返回 Vue。</p>
 */
public record PythonAgentChatResponse(
        /** 原样回传的会话编号。 */
        String conversationNo,
        /** 原样回传的对话轮次编号。 */
        String turnId,
        /** 原样回传的 Agent Run 编号。 */
        String runNo,
        /** 本轮实际使用的智能体编码。 */
        String agentCode,
        /** Agent 本轮状态，不代表训练或预测任务状态。 */
        String status,
        /** 要展示给用户的自然语言回复。 */
        String message,
        /** WAITING 状态下需要用户补充或确认的信息。 */
        AiChatResponse.Clarification clarification,
        /** 当前消息创建、引用或展示的多个业务对象。 */
        List<AiChatResponse.Reference> references,
        /** Agent 识别的主意图编码。 */
        String intentCode,
        /** 已解析的意图、实体和业务参数摘要。 */
        JsonNode intentData,
        /** 本轮选中的 Skill 编码。 */
        String skillCode,
        /** Agent 编排结果摘要，仅供内部审计。 */
        JsonNode resultData,
        /** 本轮会话记忆变更补丁，由 Java 校验、合并并持久化。 */
        JsonNode memoryPatch,
        /** 前端展示结构，例如列表或图表。 */
        JsonNode presentation,
        /** 本轮工具真实结果；查询列表由前端据此渲染。 */
        JsonNode executionResult,
        /** Agent 失败时的稳定错误编码。 */
        String errorCode,
        /** Agent 失败时经过脱敏的错误说明。 */
        String errorMessage) {

    public PythonAgentChatResponse {
        references = references == null ? List.of() : List.copyOf(references);
    }
}
