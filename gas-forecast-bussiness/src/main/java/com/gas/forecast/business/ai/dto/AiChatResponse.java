package com.gas.forecast.business.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Java 返回给 Vue 的会话响应。
 *
 * <p>status 仅表示本轮 Agent 是否成功处理或提交任务，不表示训练、预测任务已经完成。
 * 后台任务的最新状态和每一步业务细节应使用 references 中的对象编号查询。</p>
 */
public record AiChatResponse(
        /** 会话编号；首次发送消息后由 Java 生成并返回。 */
        String conversationNo,

        /** Java 生成的对话轮次编号，同一轮用户消息和助手消息共用。 */
        String turnId,

        /** Java 生成的 Agent 执行编号，对应 agent_run_tb.run_no。 */
        String runNo,

        /** 当前会话绑定的智能体编码。 */
        String agentCode,

        /** Agent 本轮状态：PENDING、RUNNING、WAITING、SUCCESS、FAILED、CANCELLED。 */
        String status,

        /** 展示给用户的助手回复。 */
        String message,

        /** 需要用户补充或确认的信息；非 WAITING 状态通常为空。 */
        Clarification clarification,

        /** 前端展示结构，例如列表或图表。 */
        JsonNode presentation,

        /** 本轮工具真实结果；查询列表由前端据此渲染。 */
        JsonNode executionResult,

        /** 当前助手消息创建、引用或展示的多个业务对象。 */
        List<Reference> references) {

    public AiChatResponse {
        references = references == null ? List.of() : List.copyOf(references);
    }

    /** Agent 等待用户补充信息或批准执行时的结构化提示。 */
    public record Clarification(
            String interactionId,
            String interactionType,
            String replyToTurnId,
            String requestId,
            String reason,
            String resumeFrom,
            String prompt,
            List<ClarificationField> fields) {
        public Clarification {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /** 一个需要补充或确认的业务参数。 */
    public record ClarificationField(String parameterCode, String parameterName, String reason, String inputType,
                                     boolean required, List<ClarificationOption> options) {
        public ClarificationField {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** 选择类补充参数的一个候选项。 */
    public record ClarificationOption(String value, String label) {
    }

    /**
     * 当前消息关联的业务对象轻量引用。
     *
     * @param referenceType 对象类型：EXECUTION_BATCH、EXECUTION_TASK、TRAIN_BATCH、FORECAST_BATCH
     * @param referenceNo 对象编号，根据 referenceType 查询对应业务表
     * @param relationType 关系：CREATED、REFERENCED、DISPLAYED
     * @param title 前端展示标题，可为空
     * @param status 返回时的状态快照，不应代替任务详情接口的最新状态
     */
    public record Reference(String referenceType, String referenceNo, String relationType,
                            String title, String status) {
    }
}
