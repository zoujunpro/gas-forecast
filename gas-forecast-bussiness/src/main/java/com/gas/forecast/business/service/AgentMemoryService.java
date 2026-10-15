package com.gas.forecast.business.service;

import java.util.Map;

/** 管理AI会话的工作记忆，不包含大模型推理逻辑。 */
public interface AgentMemoryService {
    Map<String, Object> get(String conversationNo);

    Map<String, Object> update(String conversationNo, String operation, Map<String, Object> values);
}
