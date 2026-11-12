package com.gas.forecast.business.dto;

import com.gas.forecast.business.enums.AiChatInputTypeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Vue调用Java会话接口的请求对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    /** 已有会话编号；首次发起会话时为空，由Java创建后通过响应返回。 */
    @Size(max = 64, message = "会话编号长度不能超过64个字符")
    private String conversationNo;

    /** 用户本轮输入的自然语言消息。 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容长度不能超过4000个字符")
    private String message;

    /** 用户输入类型；默认为普通文本。 */
    private AiChatInputTypeEnum inputType = AiChatInputTypeEnum.TEXT;

    /** 结构化回复对应的交互编号。 */
    @Size(max = 64, message = "交互编号长度不能超过64个字符")
    private String interactionId;

    /** 本次回复所对应的助手轮次编号。 */
    @Size(max = 64, message = "回复轮次编号长度不能超过64个字符")
    private String replyToTurnId;

    /** 新建会话使用的智能体编码；已有会话不能在本轮临时切换。 */
    @Size(max = 64, message = "智能体编码长度不能超过64个字符")
    private String agentCode;

    /** 用户发送本轮消息时正在查看的业务对象；它只是当前焦点，不绑定会话。 */
    @Valid
    private BusinessContext businessContext;

    /** Jackson传入null时仍按普通文本处理。 */
    public void setInputType(AiChatInputTypeEnum inputType) {
        this.inputType = inputType == null ? AiChatInputTypeEnum.TEXT : inputType;
    }

    /** 用户发送消息时的当前业务焦点。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessContext {

        /** 当前地区编码；可以表示省、市等层级。 */
        @Size(max = 64, message = "地区编码长度不能超过64个字符")
        private String regionCode;

        /** 当前地区名称，仅用于展示和实体确认。 */
        @Size(max = 128, message = "地区名称长度不能超过128个字符")
        private String regionName;

        /** 当前行业编码。 */
        @Size(max = 64, message = "行业编码长度不能超过64个字符")
        private String industryCode;

        /** 当前行业名称，仅用于展示和实体确认。 */
        @Size(max = 128, message = "行业名称长度不能超过128个字符")
        private String industryName;

        /** 当前客户编码。 */
        @Size(max = 64, message = "客户编码长度不能超过64个字符")
        private String customerCode;

        /** 当前客户名称，仅用于展示和实体确认。 */
        @Size(max = 128, message = "客户名称长度不能超过128个字符")
        private String customerName;
    }
}
