package com.gas.forecast.business.skill.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.MysteelIndexClient;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 根据真实钢联指标编码查询指定训练区间的历史数据。 */
@Component
@RequiredArgsConstructor
public class MysteelIndexDataTool implements SkillTool {

    public static final String TOOL_NAME = "mysteel_index_data";
    private final MysteelIndexClient client;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public JsonNode execute(JsonNode arguments) {
        String indexCode = text(arguments, "index_code");
        String beginDate = text(arguments, "begin_date");
        String endDate = text(arguments, "end_date");
        if (!TextUtils.hasText(indexCode) || !TextUtils.hasText(beginDate) || !TextUtils.hasText(endDate)) {
            throw new BusinessException("指标数据查询必须提供index_code、begin_date和end_date");
        }
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("index_code", indexCode);
            result.set("values", objectMapper.valueToTree(client.fetchIndexValues(indexCode, LocalDate.parse(beginDate), LocalDate.parse(endDate))));
            return result;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException("指标数据查询日期格式必须为yyyy-MM-dd");
        }
    }

    private String text(JsonNode arguments, String field) {
        return arguments == null ? "" : arguments.path(field).asText("").trim();
    }
}
