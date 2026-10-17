package com.gas.forecast.business.ai.service;

import com.gas.forecast.business.ai.model.AiIntentType;
import com.gas.forecast.business.ai.model.AiToolName;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Java 侧工具白名单及安全属性；大模型不能声明或改变这些属性。 */
@Component
public class AiToolRegistry {
    private final Map<AiToolName, ToolDefinition> tools = new EnumMap<>(AiToolName.class);

    public AiToolRegistry() {
        register(AiToolName.QUERY_CUSTOMERS, AiIntentType.QUERY_DATA, false, List.of());
        register(AiToolName.QUERY_MODEL_CAPABILITY, AiIntentType.QUERY_CAPABILITY, false, List.of());
        register(AiToolName.START_TRAINING, AiIntentType.START_TRAINING, true, List.of("scope"));
        register(AiToolName.QUERY_TRAINING, AiIntentType.QUERY_TRAINING, false, List.of());
        register(AiToolName.START_FORECAST, AiIntentType.START_FORECAST, true, List.of("scope"));
        register(AiToolName.QUERY_FORECAST_RESULT, AiIntentType.QUERY_RESULT, false, List.of());
        register(AiToolName.ANALYZE_CURRENT_RESULT, AiIntentType.GENERAL_CHAT, false, List.of());
        register(AiToolName.NONE, AiIntentType.UNKNOWN, false, List.of());
    }

    public void normalize(com.gas.forecast.business.ai.model.AiIntent intent) {
        AiToolName tool = intent.getTool();
        if (tool != null && tool != AiToolName.NONE) {
            intent.setIntent(definition(tool).intentType());
            return;
        }
        // 兼容历史 pending_intent_json 与旧接口返回结构。
        intent.setTool(fromLegacyIntent(intent.getIntent()));
    }

    public boolean mutating(AiToolName tool) {
        return definition(tool == null ? AiToolName.NONE : tool).mutating();
    }

    public ToolDefinition definition(AiToolName tool) {
        return tools.getOrDefault(tool, tools.get(AiToolName.NONE));
    }

    private void register(AiToolName name, AiIntentType intentType, boolean mutating, List<String> requiredArguments) {
        tools.put(name, new ToolDefinition(name, intentType, mutating, requiredArguments));
    }

    private AiToolName fromLegacyIntent(AiIntentType type) {
        if (type == null)
            return AiToolName.NONE;
        return switch (type) {
            case QUERY_DATA -> AiToolName.QUERY_CUSTOMERS;
            case QUERY_CAPABILITY -> AiToolName.QUERY_MODEL_CAPABILITY;
            case START_TRAINING -> AiToolName.START_TRAINING;
            case QUERY_TRAINING -> AiToolName.QUERY_TRAINING;
            case START_FORECAST -> AiToolName.START_FORECAST;
            case QUERY_FORECAST, QUERY_RESULT -> AiToolName.QUERY_FORECAST_RESULT;
            case GENERAL_CHAT -> AiToolName.ANALYZE_CURRENT_RESULT;
            default -> AiToolName.NONE;
        };
    }

    public record ToolDefinition(AiToolName name, AiIntentType intentType, boolean mutating, List<String> requiredArguments) {
    }
}
