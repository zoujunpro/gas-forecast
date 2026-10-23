package com.gas.forecast.business.service;

import com.gas.forecast.business.dto.AiChatRequest;
import com.gas.forecast.business.dto.AiChatResponse;
import com.gas.forecast.business.dto.PythonAgentChatRequest;
import com.gas.forecast.business.dto.PythonAgentChatResponse;
import com.gas.forecast.business.common.client.agent.PythonAgentChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 编排 Vue -> Java -> Python -> Java -> Vue 的完整会话调用链。 */
@Service
@RequiredArgsConstructor
public class AiChatService {
    private final AiChatPersistenceService persistenceService;
    private final PythonAgentChatClient agentClient;

    /** 创建本轮会话记录，调用 Python Agent，并保存回复。 */
    public AiChatResponse chat(AiChatRequest request) {
        PythonAgentChatRequest pythonRequest = persistenceService.createTurn(request);
        PythonAgentChatResponse result;
        try {
            result = agentClient.chat(pythonRequest);
        } catch (RuntimeException exception) {
            return persistenceService.fail(pythonRequest, exception);
        }
        return persistenceService.complete(result);
    }
}
