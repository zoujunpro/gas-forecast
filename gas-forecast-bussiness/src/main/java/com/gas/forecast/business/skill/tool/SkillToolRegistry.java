package com.gas.forecast.business.skill.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.gas.forecast.common.core.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Skill 工具注册中心，只允许执行应用显式注册的业务工具。 */
@Component
public class SkillToolRegistry {

    private final Map<String, SkillTool> tools;

    public SkillToolRegistry(List<SkillTool> toolList) {
        Map<String, SkillTool> registered = new LinkedHashMap<>();
        for (SkillTool tool : toolList) {
            SkillTool previous = registered.putIfAbsent(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Skill工具名称重复：" + tool.name());
            }
        }
        this.tools = Map.copyOf(registered);
    }

    public JsonNode execute(String name, JsonNode arguments) {
        SkillTool tool = tools.get(name);
        if (tool == null) {
            throw new BusinessException("不支持的Skill工具：" + name);
        }
        return tool.execute(arguments);
    }

    public boolean supports(String name) {
        return tools.containsKey(name);
    }
}
