package com.gas.forecast.business.dto;

import com.gas.forecast.business.enums.AiChatAgentStatusEnum;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python FastAPI 返回给 Java 的内部 Agent 执行结果。
 *
 * <p>
 * Java 校验 conversationNo、turnId 和 runNo 后，保存内容块与澄清状态并更新 Agent Run。
 * 任务关联直接从 TASK_CARD 内容块提取，本对象不再维护重复的 references 和审计字段。
 * </p>
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
        AiChatAgentStatusEnum status,
        /** Java保存并返回Vue的有序展示内容块。 */
        List<AiChatContentBlock> blocks,
        /** WAITING状态下当前单字段问题及恢复快照。 */
        PythonClarification clarification,
        /** FAILED状态下的稳定错误信息。 */
        AiChatErrorInfo error) {

    public PythonAgentChatResponse {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** Python只描述当前问题；正式interactionId由Java生成。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PythonClarification {

        /** 当前需要用户补充的字段。 */
        private String field;

        /** 触发澄清的原因编码。 */
        private String reason;

        /** 前端应使用的输入方式。 */
        private String inputType;

        /** 可供用户选择的候选项。 */
        private List<AiClarificationOption> options = List.of();

        /** 多轮澄清之间需要持久化和恢复的未完成操作。 */
        private AgentPendingOperation pendingOperation;

        public void setOptions(List<AiClarificationOption> options) {
            this.options = options == null ? List.of() : List.copyOf(options);
        }
    }

}
