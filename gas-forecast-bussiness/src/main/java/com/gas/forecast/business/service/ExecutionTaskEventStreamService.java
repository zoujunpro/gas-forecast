package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentRunEventTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.mapper.AgentRunEventTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 按任务订阅真实运行事件。
 *
 * <p>这里不再推送固定步骤；每次快照都直接来自 {@code agent_run_event_tb}，前端按
 * 事件的主键顺序展示。任务和事件的关联以 {@code execution_task_tb.run_no} 为准。</p>
 */
@Service
@RequiredArgsConstructor
public class ExecutionTaskEventStreamService {
    private static final long TIMEOUT_MS = 180_000L;
    private final ExecutionTaskTbMapper taskMapper;
    private final AgentRunEventTbMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "execution-task-event-stream");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter subscribe(String taskNo) {
        ExecutionTaskTb task = taskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getTaskNo, taskNo).last("limit 1"));
        if (task == null) throw new BusinessException("执行任务不存在：" + taskNo);
        if (!TextUtils.hasText(task.getRunNo())) throw new BusinessException("该任务尚未关联智能体运行");

        String runNo = task.getRunNo();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<String> previousVersion = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable close = () -> {
            closed.set(true);
            ScheduledFuture<?> future = futureRef.get();
            if (future != null) future.cancel(false);
        };
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());

        Runnable publish = () -> {
            if (closed.get()) return;
            try {
                List<AgentRunEventTb> events = eventMapper.selectList(Wrappers.<AgentRunEventTb>lambdaQuery()
                        .eq(AgentRunEventTb::getRunNo, runNo).orderByAsc(AgentRunEventTb::getId));
                String version = events.stream()
                        .map(event -> event.getId() + ":" + event.getStatus() + ":" + event.getUpdatedAt())
                        .reduce("", (left, right) -> left + "|" + right);
                if (!version.equals(previousVersion.getAndSet(version))) {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().name("task_events")
                                .data(objectMapper.writeValueAsString(Map.of(
                                        "taskNo", taskNo, "runNo", runNo, "events", events))));
                    }
                }
            } catch (IOException exception) {
                close.run();
                emitter.completeWithError(exception);
            } catch (Exception ignored) {
                // 事件推送异常不应影响已创建的训练、预测任务；下一次轮询会继续尝试。
            }
        };
        futureRef.set(scheduler.scheduleWithFixedDelay(publish, 0, 350, TimeUnit.MILLISECONDS));
        return emitter;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }
}
