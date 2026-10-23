package com.gas.forecast.business.service.impl;

import com.gas.forecast.business.service.ExecutionTaskEventService;
import com.gas.forecast.dao.domain.ExecutionStepTb;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 进程内 SSE 分发器。断线后的恢复由 execution_step_tb 快照负责，而不是重放历史事件。 */
@Slf4j
@Service
public class ExecutionTaskEventServiceImpl implements ExecutionTaskEventService {
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;
    private final Map<String, CopyOnWriteArraySet<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String taskNo) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        CopyOnWriteArraySet<SseEmitter> taskEmitters = emitters.computeIfAbsent(taskNo, ignored -> new CopyOnWriteArraySet<>());
        taskEmitters.add(emitter);
        Runnable remove = () -> remove(taskNo, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("taskNo", taskNo)));
        } catch (IOException exception) {
            remove.run();
        }
        return emitter;
    }

    @Override
    public void publishStep(ExecutionTaskDetailTb detail, ExecutionStepTb step) {
        if (detail == null || step == null || detail.getTaskNo() == null) return;
        Map<String, Object> payload = Map.of(
                "taskNo", detail.getTaskNo(), "detailNo", detail.getDetailNo(), "step", step);
        for (SseEmitter emitter : emitters.getOrDefault(detail.getTaskNo(), new CopyOnWriteArraySet<>())) {
            try {
                emitter.send(SseEmitter.event().name("task_step_updated").data(payload));
            } catch (IOException | IllegalStateException exception) {
                remove(detail.getTaskNo(), emitter);
            }
        }
    }

    private void remove(String taskNo, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> taskEmitters = emitters.get(taskNo);
        if (taskEmitters == null) return;
        taskEmitters.remove(emitter);
        if (taskEmitters.isEmpty()) emitters.remove(taskNo, taskEmitters);
    }
}
