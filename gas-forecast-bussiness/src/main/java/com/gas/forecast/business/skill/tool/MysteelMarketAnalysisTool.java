package com.gas.forecast.business.skill.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.MysteelMarketAnalysisClient;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 获取钢联 Markdown 市场分析报告的受限 Skill 工具。 */
@Component
@RequiredArgsConstructor
public class MysteelMarketAnalysisTool implements SkillTool {

    public static final String TOOL_NAME = "mysteel_market_analysis";

    private final MysteelMarketAnalysisClient client;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String query = arguments == null ? "" : arguments.path("query").asText("").trim();
        if (!TextUtils.hasText(query)) {
            throw new BusinessException("Skill工具参数query不能为空");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("query", query);
        result.put("report", client.analyze(query));
        result.put("format", "MARKDOWN");
        return result;
    }
}
