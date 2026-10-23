package com.gas.forecast.business.service.impl;

import com.gas.forecast.business.enums.ExecutionStepCode;
import com.gas.forecast.business.service.ExecutionStepRecorder;
import com.gas.forecast.business.service.ModelTrainStepRecorder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 将训练引擎的细粒度事件归并写入统一的 execution_step_tb 六步流程。 */
@Service
@RequiredArgsConstructor
public class ModelTrainStepRecorderImpl implements ModelTrainStepRecorder {
    private final ExecutionStepRecorder executionStepRecorder;
    private final AtomicLong handles = new AtomicLong();
    private final ConcurrentHashMap<Long, Handle> activeHandles = new ConcurrentHashMap<>();

    @Override
    public Long start(String batchNo, int attemptNo, Long parentStepId, int stepLevel, String stepCode, String stepName, int stepOrder, String inputSummary) {
        ExecutionStepCode target = target(stepCode);
        if (target == null) return null;
        executionStepRecorder.startByTrainBatch(batchNo, target, inputSummary);
        long id = handles.incrementAndGet();
        activeHandles.put(id, new Handle(batchNo, target));
        return id;
    }

    @Override
    public void success(Long stepId, String outputSummary, Integer producedCount) {
        Handle handle = remove(stepId);
        if (handle != null) executionStepRecorder.successByTrainBatch(handle.batchNo(), handle.stepCode(), outputSummary, null);
    }

    @Override
    public void fail(Long stepId, Throwable error) {
        Handle handle = remove(stepId);
        if (handle != null) executionStepRecorder.failByTrainBatch(handle.batchNo(), handle.stepCode(), "TRAINING_FAILED", error);
    }

    private Handle remove(Long id) { return id == null ? null : activeHandles.remove(id); }

    private ExecutionStepCode target(String stepCode) {
        if (stepCode == null) return null;
        if (stepCode.startsWith("DYNAMIC_")) return ExecutionStepCode.DYNAMIC_INDICATOR;
        return switch (stepCode) {
            case "LOAD_DATA" -> ExecutionStepCode.SALES_DATA_PREPARE;
            case "FEATURE_PREPARE" -> ExecutionStepCode.FACTOR_DATA_PREPARE;
            case "MODEL_TRAIN" -> ExecutionStepCode.MODEL_SELECT;
            default -> null;
        };
    }

    private record Handle(String batchNo, ExecutionStepCode stepCode) {}
}
