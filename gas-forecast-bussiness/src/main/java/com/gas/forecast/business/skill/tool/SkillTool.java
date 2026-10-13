package com.gas.forecast.business.skill.tool;

import com.fasterxml.jackson.databind.JsonNode;

/** 可由训练编排或大模型工具调用执行的受限业务工具。 */
public interface SkillTool {

    /** 工具的稳定唯一标识。 */
    String name();

    /** 根据结构化参数执行工具并返回结构化结果。 */
    JsonNode execute(JsonNode arguments);
}
