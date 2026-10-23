package com.gas.forecast.business.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vue 调用 Java 会话接口的请求对象。
 *
 * <p>前端只提交用户可控信息。conversationNo、turnId 和 runNo 中，只有已有会话的
 * conversationNo 可以由前端传入；turnId 和 runNo 必须由 Java 服务端生成。</p>
 */
public record AiChatRequest(
        /** 已有会话编号；首次发起会话时为空，由 Java 创建后通过响应返回。 */
        @Size(max = 64, message = "会话编号长度不能超过64个字符")
        String conversationNo,

        /** 前端为一次发送生成的请求号；重试同一发送时必须复用，用于防止重复创建任务。 */
        @Size(max = 64, message = "请求编号长度不能超过64个字符")
        String clientRequestId,

        /** 用户本轮输入的自然语言消息。 */
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 4000, message = "消息内容长度不能超过4000个字符")
        String message,

        /** 普通文本，或对上一轮结构化澄清/确认的回复。 */
        InputType inputType,

        /** Python 返回的待处理交互编号；结构化回复必须原样携带。 */
        @Size(max = 64, message = "交互编号长度不能超过64个字符")
        String interactionId,

        /** 本次回复所对应的助手轮次编号。 */
        @Size(max = 64, message = "回复轮次编号长度不能超过64个字符")
        String replyToTurnId,

        /** 结构化字段答案；候选选择只提交服务端生成的不透明选项值。 */
        Map<String, Object> answers,

        /** 新建会话使用的智能体编码；已有会话不能在本轮临时切换。 */
        @Size(max = 64, message = "智能体编码长度不能超过64个字符")
        String agentCode,

        /** 用户发送本轮消息时正在查看的业务对象；它只是当前焦点，不绑定会话。 */
        @Valid BusinessContext businessContext) {

    public AiChatRequest {
        inputType = inputType == null ? InputType.TEXT : inputType;
        answers = answers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }

    public enum InputType {
        TEXT,
        CLARIFICATION_REPLY,
        CONFIRMATION_REPLY,
        REJECTION_REPLY
    }

    /**
     * 用户发送消息时的当前业务焦点。
     *
     * <p>这些值可以随页面选择变化。Agent 只能把它们当作本轮指代消解的候选信息；
     * 用户原话没有明确业务对象时，创建训练或预测任务前仍应向用户确认。</p>
     */
    public record BusinessContext(
            /** 当前地区编码；可以表示省、市等层级。 */
            @Size(max = 64, message = "地区编码长度不能超过64个字符") String regionCode,
            /** 当前地区名称，仅用于展示和实体确认，不能代替服务端主数据校验。 */
            @Size(max = 128, message = "地区名称长度不能超过128个字符") String regionName,
            /** 当前行业编码。 */
            @Size(max = 64, message = "行业编码长度不能超过64个字符") String industryCode,
            /** 当前行业名称，仅用于展示和实体确认。 */
            @Size(max = 128, message = "行业名称长度不能超过128个字符") String industryName,
            /** 当前客户编码。 */
            @Size(max = 64, message = "客户编码长度不能超过64个字符") String customerCode,
            /** 当前客户名称，仅用于展示和实体确认。 */
            @Size(max = 128, message = "客户名称长度不能超过128个字符") String customerName) {
    }
}
