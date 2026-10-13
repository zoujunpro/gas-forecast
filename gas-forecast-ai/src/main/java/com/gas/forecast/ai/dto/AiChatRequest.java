package com.gas.forecast.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatRequest {
    @Size(max = 64)
    private String conversationNo;
    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息长度不能超过4000个字符")
    private String message;
    /** 是否为页面基于当前预测结果生成的推荐追问。 */
    private Boolean contextualFollowUp;
}
