package com.gas.forecast.business.skill.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.MysteelIndexClient;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 根据指标描述搜索真实钢联指标候选。 */
@Component
@RequiredArgsConstructor
public class MysteelIndexSearchTool implements SkillTool {

    public static final String TOOL_NAME = "mysteel_index_search";
    private final MysteelIndexClient client;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String query = arguments == null ? "" : arguments.path("query").asText("").trim();
        if (!TextUtils.hasText(query)) {
            throw new BusinessException("指标搜索参数query不能为空");
        }
        int pageSize = arguments.path("page_size").asInt(20);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("query", query);
        result.set("candidates", objectMapper.valueToTree(client.searchIndexes(query, pageSize)));
        return result;
    }
}
