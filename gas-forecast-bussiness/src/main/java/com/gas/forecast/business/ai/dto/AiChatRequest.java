package com.gas.forecast.business.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

@Data
public class AiChatRequest {
    @Size(max = 64)
    private String conversationNo;
    /** 预测详情页传入；后端据此查询或创建该预测唯一绑定的会话。 */
    @Size(max = 64)
    private String forecastBatchNo;
    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息长度不能超过4000个字符")
    private String message;
    /** 是否为页面基于当前预测结果生成的推荐追问。 */
    private Boolean contextualFollowUp;
    /** 智能体详情页已确定的预测类型，例如 short-term。 */
    @Size(max = 32)
    private String agentType;
    /** 页面当前已绑定的地区、行业、客户等结构化业务范围。 */
    private Map<String, Object> metadata;
}
