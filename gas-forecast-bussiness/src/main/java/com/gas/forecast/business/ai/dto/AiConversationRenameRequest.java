package com.gas.forecast.business.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiConversationRenameRequest {
    @NotBlank(message = "会话编号不能为空")
    @Size(max = 64, message = "会话编号长度不能超过64个字符")
    private String conversationNo;

    @NotBlank(message = "会话名称不能为空")
    @Size(max = 128, message = "会话名称长度不能超过128个字符")
    private String title;
}
