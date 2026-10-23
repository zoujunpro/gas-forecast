package com.gas.forecast.business.ai.service;

import com.gas.forecast.business.ai.dto.AiChatRequest;
import com.gas.forecast.business.ai.dto.AiChatResponse;
import com.gas.forecast.business.ai.dto.PythonAgentChatRequest;
import com.gas.forecast.business.ai.dto.PythonAgentChatResponse;
import com.gas.forecast.business.component.PythonAgentChatClient;
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
