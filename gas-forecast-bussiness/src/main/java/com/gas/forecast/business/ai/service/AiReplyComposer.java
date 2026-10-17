package com.gas.forecast.business.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.ai.model.AiIntent;
import com.gas.forecast.business.component.ForecastAnalysisLlmClient;
import com.gas.forecast.common.util.TextUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 仅负责把已经确定的业务事实改写成自然、友好的用户回复。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiReplyComposer {
    private static final String SYSTEM_PROMPT = """
            你是天然气预测平台的回复助手。请根据输入的结构化业务结果生成自然、友好、简洁的中文回答。
            你只负责表达，不负责重新判断业务结果。不得修改或编造实体、编码、数量、状态、候选项和错误原因。
            NEED_INFORMATION时说明缺少什么、用户如何补充，并说明已保留原请求；NEED_CONFIRMATION或NEED_SELECTION时完整列出所有候选项并请用户回复序号、名称或编码；
            FAILED时准确说明原因并给出一条可操作的下一步；COMPLETED时直接说明结果。
            不得暴露SQL、异常堆栈、接口地址或内部实现。不要声称已经执行输入中没有明确成功的操作。控制在180字以内。
            只输出给用户看的最终回答，不要输出JSON、标题或解释过程。
            """;

    private final ForecastAnalysisLlmClient llmClient;
    private final ObjectMapper objectMapper;

    public String compose(String status, String factualMessage, AiIntent intent) {
        if (!TextUtils.hasText(factualMessage))
            return factualMessage;
        try {
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("status", status);
            facts.put("factualMessage", factualMessage);
            facts.put("operation", intent == null || intent.getIntent() == null ? null : intent.getIntent().name());
            facts.put("regionName", intent == null ? null : intent.getRegionName());
            facts.put("industryName", intent == null ? null : intent.getIndustryName());
            facts.put("customerName", intent == null ? null : intent.getCustomerName());
            facts.put("candidates", intent == null ? null : intent.getCandidates());
            facts.put("pendingContext", status != null && (status.startsWith("NEED_") || "FAILED".equals(status)));
            String answer = llmClient.complete(SYSTEM_PROMPT, objectMapper.writeValueAsString(facts), 0.2D);
            return TextUtils.hasText(answer) ? answer.trim() : factualMessage;
        } catch (Exception exception) {
            log.warn("友好回复生成失败，使用业务原文 status={}, reason={}", status, exception.getMessage());
            return factualMessage;
        }
    }
}
