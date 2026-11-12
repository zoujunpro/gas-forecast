package com.gas.forecast.business.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gas.forecast.business.enums.AiChatAgentStatusEnum;
import com.gas.forecast.business.enums.AiChatContentTypeEnum;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Java返回给Vue的统一会话响应；不同展示内容统一放入blocks。 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatResponse {

    /** 会话编号。 */
    private String conversationNo;

    /** 本轮对话编号。 */
    private String turnId;

    /** 本轮Agent运行编号。 */
    private String runNo;

    /** 当前会话使用的智能体编码。 */
    private String agentCode;

    /** Agent本轮处理状态。 */
    private AiChatAgentStatusEnum status;

    /** Vue按顺序渲染的内容块。 */
    private List<AiChatContentBlock> blocks = List.of();

    /** 当前需要用户回答的澄清问题。 */
    private FrontendClarification clarification;

    /** 本轮失败时的稳定错误信息。 */
    private AiChatErrorInfo error;

    public AiChatResponse(String conversationNo, String turnId, String runNo, String agentCode, AiChatAgentStatusEnum status, List<AiChatContentBlock> blocks, FrontendClarification clarification, AiChatErrorInfo error) {
        this.conversationNo = conversationNo;
        this.turnId = turnId;
        this.runNo = runNo;
        this.agentCode = agentCode;
        this.status = status;
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
        this.clarification = clarification;
        this.error = error;
    }

    public void setBlocks(List<AiChatContentBlock> blocks) {
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** 提取流式接口需要逐段推送的Markdown正文。 */
    public String markdownText() {
        return blocks.stream().filter(block -> block.getType() == AiChatContentTypeEnum.MARKDOWN && block.getData() instanceof AiChatContentBlock.MarkdownContentData).map(block -> ((AiChatContentBlock.MarkdownContentData) block.getData()).getContent()).filter(text -> text != null && !text.isBlank()).reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
    }

    /** Java返回给Vue的澄清信息，不包含Python内部恢复状态。 */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class FrontendClarification {

        /** Java生成的待处理交互编号。 */
        private String interactionId;

        /** 当前需要用户补充的字段编码。 */
        private String field;

        /** 前端应使用的输入组件类型。 */
        private String inputType;

        /** 选择类澄清的候选项；自由输入时为空。 */
        private List<AiClarificationOption> options = List.of();

        public FrontendClarification(String interactionId, String field, String inputType, List<AiClarificationOption> options) {
            this.interactionId = interactionId;
            this.field = field;
            this.inputType = inputType;
            this.options = options == null ? List.of() : List.copyOf(options);
        }

        public void setOptions(List<AiClarificationOption> options) {
            this.options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** 消息关联的执行任务信息，不属于顶层Vue响应字段。 */
    public record Reference(String taskNo, String relationType, String title, String status) {
    }
}
