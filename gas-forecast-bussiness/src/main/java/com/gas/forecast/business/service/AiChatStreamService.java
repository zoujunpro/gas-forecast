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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AgentRunEventService eventService;
    private final ObjectMapper objectMapper;
    private final TraceableThreadPoolExecutor executor = ThreadPoolUtils.create(ThreadPoolConfig.of("ai-chat-stream", 2, 4, 50));
    private final ScheduledExecutorService eventEmitter = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "ai-chat-event-stream");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter chat(AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        var security = new HashMap<>(SecurityContextHolder.getLocalMap());
        var loginUser = ThreadLocalUtil.getLoginUser();
        executor.executeTracked("ai-chat-stream", request.getConversationNo(), () -> {
            try {
                SecurityContextHolder.setLocalMap(new HashMap<>(security));
                if (loginUser != null) {
                    ThreadLocalUtil.setLoginUser(loginUser.userId(), loginUser.username(), loginUser.token());
                }
                PythonAgentChatRequest pythonRequest = persistenceService.createTurn(request);
                send(emitter, "run_started", java.util.Map.of("runNo", pythonRequest.runNo(), "startedAt", java.time.Instant.now().toString()));
                ScheduledFuture<?> eventSubscription = subscribeRunEvents(emitter, pythonRequest.runNo());
                PythonAgentChatResponse result;
                RuntimeException agentException = null;
                try {
                    result = agentClient.chatStream(pythonRequest, text -> sendUnchecked(emitter, "progress", text));
                } catch (RuntimeException exception) {
                    result = null;
                    agentException = exception;
                } finally {
                    eventSubscription.cancel(false);
                    sendRunEvents(emitter, pythonRequest.runNo(), new AtomicReference<>(null));
                }
                AiChatResponse response = result == null ? persistenceService.fail(pythonRequest, agentException == null ? new IllegalStateException("Python Agent 未返回最终结果") : agentException) : persistenceService.complete(result);
                for (String segment : response.markdownText().split("(?<=[。！？；\n])")) {
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
                String message = exception instanceof BusinessException && exception.getMessage() != null ? exception.getMessage() : "本轮处理失败，请稍后重试。";
                try {
                    send(emitter, "error", message);
                } catch (IOException ignored) {
                }
                emitter.completeWithError(exception);
            } finally {
                SecurityContextHolder.remove();
                ThreadLocalUtil.clear();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        synchronized (emitter) {
            emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
        }
    }

    private void sendUnchecked(SseEmitter emitter, String event, Object data) {
        try {
            send(emitter, event, data);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ScheduledFuture<?> subscribeRunEvents(SseEmitter emitter, String runNo) {
        AtomicReference<String> lastVersion = new AtomicReference<>(null);
        return eventEmitter.scheduleWithFixedDelay(() -> sendRunEvents(emitter, runNo, lastVersion), 0, 350, TimeUnit.MILLISECONDS);
    }

    private void sendRunEvents(SseEmitter emitter, String runNo, AtomicReference<String> lastVersion) {
        try {
            List<com.gas.forecast.business.dto.AgentRunEventResponse> events = eventService.listInternal(runNo);
            String version = events.stream().map(event -> event.id() + ":" + event.status() + ":" + event.updatedAt()).reduce("", (left, right) -> left + "|" + right);
            if (!version.equals(lastVersion.getAndSet(version)))
                send(emitter, "run_events", events);
        } catch (Exception ignored) {
            // 过程推送失败不能中断主对话；最终回复和历史事件仍会保留。
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        eventEmitter.shutdown();
    }
}
