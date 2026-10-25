package com.gas.forecast.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.dto.AiChatRequest;
import com.gas.forecast.business.dto.AiChatResponse;
import com.gas.forecast.business.dto.PythonAgentChatRequest;
import com.gas.forecast.business.dto.PythonAgentChatResponse;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.business.common.client.agent.PythonAgentChatClient;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.threadpool.ThreadPoolConfig;
import com.gas.forecast.common.threadpool.ThreadPoolUtils;
import com.gas.forecast.common.threadpool.TraceableThreadPoolExecutor;
import com.gas.forecast.common.util.ThreadLocalUtil;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 将已完成且已落库的聊天结果按短句 SSE 推送；不保存任何半截消息。 */
@Service
@RequiredArgsConstructor
public class AiChatStreamService {
    private static final long TIMEOUT_MS = 150_000L;
    private final AiChatPersistenceService persistenceService;
    private final PythonAgentChatClient agentClient;
    private final ObjectMapper objectMapper;
    private final TraceableThreadPoolExecutor executor = ThreadPoolUtils.create(
            ThreadPoolConfig.of("ai-chat-stream", 2, 4, 50));

    public SseEmitter chat(AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        var security = new HashMap<>(SecurityContextHolder.getLocalMap());
        var loginUser = ThreadLocalUtil.getLoginUser();
        executor.executeTracked("ai-chat-stream", request.conversationNo(), () -> {
            try {
                SecurityContextHolder.setLocalMap(new HashMap<>(security));
                if (loginUser != null) {
                    ThreadLocalUtil.setLoginUser(loginUser.userId(), loginUser.username(), loginUser.token());
                }
                PythonAgentChatRequest pythonRequest = persistenceService.createTurn(request);
                PythonAgentChatResponse result;
                RuntimeException agentException = null;
                try { result = agentClient.chatStream(pythonRequest, text -> sendUnchecked(emitter, "progress", text)); }
                catch (RuntimeException exception) { result = null; agentException = exception; }
                AiChatResponse response = result == null
                        ? persistenceService.fail(pythonRequest, agentException == null
                                ? new IllegalStateException("Python Agent 未返回最终结果") : agentException)
                        : persistenceService.complete(result);
                for (String segment : response.message().split("(?<=[。！？；\n])")) {
                    if (!segment.isBlank()) {
                        send(emitter, "delta", segment);
                        // 避免浏览器把同一网络帧内的多个 delta 合并成一次渲染。
                        Thread.sleep(28L);
                    }
                }
                send(emitter, "done", response);
                emitter.complete();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception exception) {
                // 回复已过期这类输入校验错误发生在创建本轮记录之前，前端可以安全地
                // 清除本地旧交互并把原文本作为普通对话重发；不能用通用错误掩盖它。
                String message = exception instanceof BusinessException && exception.getMessage() != null
                        ? exception.getMessage() : "本轮处理失败，请稍后重试。";
                try { send(emitter, "error", message); } catch (IOException ignored) { }
                emitter.completeWithError(exception);
            } finally {
                SecurityContextHolder.remove();
                ThreadLocalUtil.clear();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
    }

    private void sendUnchecked(SseEmitter emitter, String event, Object data) {
        try { send(emitter, event, data); } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    @PreDestroy
    void shutdown() { executor.shutdown(); }
}
