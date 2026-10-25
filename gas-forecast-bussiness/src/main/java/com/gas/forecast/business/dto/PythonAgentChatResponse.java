package com.gas.forecast.business.dto;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
        IntentData intentData,
        /** 本轮选中的 Skill 编码。 */
        String skillCode,
        /** Agent 编排结果摘要，仅供内部审计。 */
        ResultData resultData,
        /** 本轮会话记忆变更补丁，由 Java 校验、合并并持久化。 */
        MemoryPatch memoryPatch,
        /** 前端展示结构，例如列表或图表。 */
        Presentation presentation,
        /** 本轮工具真实结果；查询列表由前端据此渲染。 */
        ExecutionResult executionResult,
        /** Agent 失败时的稳定错误编码。 */
        String errorCode,
        /** Agent 失败时经过脱敏的错误说明。 */
        String errorMessage) {

    public PythonAgentChatResponse {
        references = references == null ? List.of() : List.copyOf(references);
    }

    /** 本轮意图解析摘要；参数值随业务工具变化，因此保留 Map。 */
    public record IntentData(Map<String, Object> parameters, Map<String, Object> entityResolution) {
        public IntentData {
            // Python 的可选语义字段会以 null 回传；Map.copyOf 不接受 null 值，
            // 会导致 Java 在读取 Agent 响应时抛出 NullPointerException。
            parameters = immutableMapAllowingNullValues(parameters);
            entityResolution = immutableMapAllowingNullValues(entityResolution);
        }
    }

    /** 本轮 Agent 编排摘要。 */
    public record ResultData(String executedTool, String toolStatus, String decisionReason,
                             List<AiChatResponse.Reference> references) {
        public ResultData { references = references == null ? List.of() : List.copyOf(references); }
    }

    /**
     * 当前未完成操作的替换快照。内部业务结构由 Python 明确建模，Java 负责原样存储。
     * pendingOperation 为 null 时清除操作；整个 memoryPatch 为 null 时保持原记忆。
     */
    public record MemoryPatch(Map<String, Object> pendingOperation) {
        public MemoryPatch {
            pendingOperation = pendingOperation == null
                    ? null : immutableMapAllowingNullValues(pendingOperation);
        }
    }

    /** 前端展示配置。 */
    public record Presentation(String type, String chartType, String title, String xField,
                               List<String> yFields, String dataSource) {
        public Presentation { yFields = yFields == null ? List.of() : List.copyOf(yFields); }
    }

    /** 工具执行结果的稳定信封；data 保存工具特有明细。 */
    public record ExecutionResult(Boolean success, String status, String resultType, String message,
                                  String errorCode, String errorType, Boolean retryable,
                                  Map<String, Object> data) {
        public ExecutionResult { data = immutableMapAllowingNullValues(data); }
    }

    /** 防御性复制，但保留 JSON 协议中合法的 null 值。 */
    private static Map<String, Object> immutableMapAllowingNullValues(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
