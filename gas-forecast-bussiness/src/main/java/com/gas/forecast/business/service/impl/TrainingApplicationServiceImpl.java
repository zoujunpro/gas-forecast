package com.gas.forecast.business.service.impl;

import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.enums.ExecutionTargetStatus;
import com.gas.forecast.business.enums.ExecutionBusinessType;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.business.service.TrainingApplicationService;
import com.gas.forecast.business.service.ExecutionTaskLifecycleService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 训练应用编排：解析批量范围、创建任务并统一查询进度和结果。 */
@Service
@RequiredArgsConstructor
public class TrainingApplicationServiceImpl implements TrainingApplicationService {
    private final AutoTrainService autoTrainService;
    private final ModelTrainExecutionService trainExecutionService;
    private final ExecutionTaskLifecycleService taskLifecycleService;

    @Override
    public Object start(TrainingCommand command) {
        validate(command);
        var task = taskLifecycleService.create(command.conversationNo(), ExecutionBusinessType.TRAIN,
                command.agentCode(), command.dimensionType(), command.targets(), "模型训练");
        List<ExecutionTaskDetailTb> relations = taskLifecycleService.relations(task.getTaskNo());
        List<Object> tasks = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        for (ExecutionTaskDetailTb relation : relations) {
            try {
                AutoTrainRequest request = new AutoTrainRequest();
                request.setAgentCode(command.agentCode());
                request.setRegionCode(emptyToNull(relation.getRegionCode()));
                request.setIndustryCode(emptyToNull(relation.getIndustryCode()));
                request.setCustomerCode(emptyToNull(relation.getCustomerCode()));
                request.setTrainMode(command.training().dataOptions().trainMode());
                request.setRecentPeriods(command.training().dataOptions().trainingPeriods());
                request.setTrainStartDate(command.training().dataOptions().trainingStartDate());
                request.setTrainEndDate(command.training().dataOptions().trainingEndDate());
                request.setForceTrain(command.training().forceRetrain());
                request.setTriggerSource("MCP");
                var response = autoTrainService.execute(request);
                taskLifecycleService.updateBatch(relation.getId(), response.getTrainBatchNo(), null,
                        ExecutionTargetStatus.TRAINING);
                tasks.add(response);
            } catch (RuntimeException exception) {
                taskLifecycleService.updateBatch(relation.getId(), null, null, ExecutionTargetStatus.FAILED);
                failures.add(Map.of("relationId", String.valueOf(relation.getId()), "message", safeMessage(exception)));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo());
        result.put("total", relations.size());
        result.put("submitted", tasks.size());
        result.put("failed", failures.size());
        result.put("tasks", tasks);
        result.put("failures", failures);
        String status = tasks.isEmpty() ? "FAILED" : "EXECUTING";
        result.put("status", status);
        if ("FAILED".equals(status)) {
            String reason = failures.stream().map(item -> item.get("message")).filter(TextUtils::hasText).distinct().limit(3).reduce((left, right) -> left + "；" + right)
                    .orElse("没有任务通过业务校验");
            result.put("message", "训练任务未创建：" + reason);
        } else if (!failures.isEmpty()) {
            result.put("message", "训练任务部分受理：成功" + tasks.size() + "个，失败" + failures.size() + "个");
        } else {
            result.put("message", "训练任务已受理");
        }
        return result;
    }

    @Override
    public Object status(String trainBatchNo) {
        requireBatch(trainBatchNo);
        ModelTrainResultRequest request = new ModelTrainResultRequest();
        request.setBatchNo(trainBatchNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trainBatchNo", trainBatchNo);
        ModelTrainResultResponse training = trainExecutionService.getTrainResult(request);
        result.put("result", training);
        result.put("steps", trainExecutionService.getExecutionSteps(trainBatchNo));
        String status = training.getBatches() == null || training.getBatches().isEmpty() ? "UNKNOWN" : training.getBatches().get(0).getStatus();
        String normalized = normalizeStatus(status);
        result.put("status", normalized);
        result.put("currentStage", "TRAINING");
        result.put("nextAction", "SUCCESS".equals(normalized) ? "START_FORECAST" : "FAILED".equals(normalized) ? "MODEL_DECISION" : "WAIT");
        result.put("allowedActions", "SUCCESS".equals(normalized) ? List.of("START_FORECAST", "QUERY_RESULT")
                : "FAILED".equals(normalized) ? List.of("QUERY_STEPS", "RETRY", "ASK_USER", "FAIL") : List.of("WAIT", "QUERY_STATUS"));
        result.put("retryable", "FAILED".equals(normalized));
        return result;
    }

    @Override
    public Object result(String trainBatchNo) {
        requireBatch(trainBatchNo);
        ModelTrainResultRequest request = new ModelTrainResultRequest();
        request.setBatchNo(trainBatchNo);
        return trainExecutionService.getTrainResult(request);
    }

    private void validate(TrainingCommand command) {
        if (command == null || !TextUtils.hasText(command.agentCode()))
            throw new BusinessException("agentCode不能为空");
        if (command.targets() == null || command.targets().isEmpty())
            throw new BusinessException("targets不能为空");
        boolean bulk = command.targets().size() > 1 || command.targets().stream()
                .anyMatch(target -> target.customerCodes() == null || target.customerCodes().isEmpty());
        if (bulk && !command.execution().confirmed())
            throw new BusinessException("批量训练必须先获得用户确认");
    }

    private String emptyToNull(String value) { return TextUtils.hasText(value) ? value : null; }

    private void requireBatch(String batchNo) {
        if (!TextUtils.hasText(batchNo))
            throw new BusinessException("训练批次号不能为空");
    }

    private String safeMessage(RuntimeException exception) {
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "创建训练任务失败";
    }

    /** 将训练队列的细分状态收敛为智能体稳定状态。 */
    private String normalizeStatus(String status) {
        if ("SUCCESS".equalsIgnoreCase(status)) return "SUCCESS";
        if (status != null && status.toUpperCase().startsWith("FAILED")) return "FAILED";
        if (status == null || "UNKNOWN".equalsIgnoreCase(status)) return "UNKNOWN";
        return "EXECUTING";
    }
}
