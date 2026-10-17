package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.service.AgentExecutionTaskService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.business.service.ModelTrainQueueService;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentExecutionStepTb;
import com.gas.forecast.dao.domain.AgentExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AgentExecutionStepTbMapper;
import com.gas.forecast.dao.mapper.AgentExecutionBatchTbMapper;
import com.gas.forecast.dao.mapper.AgentExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.domain.AgentExecutionBatchTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 基于任务、智能体步骤和真实业务批次构建统一可恢复进度视图。 */
@Service
@RequiredArgsConstructor
public class AgentExecutionTaskServiceImpl implements AgentExecutionTaskService {
    private static final Set<String> AGENT_STEP_CODES = Set.of("ENTITY_RESOLUTION", "PARAMETER_COLLECTION", "WAITING_USER_CONFIRMATION", "TOOL_CALL", "RESULT_EXPLANATION");
    private static final Set<String> STEP_STATUSES = Set.of("WAITING", "RUNNING", "SUCCESS", "FAILED", "SKIPPED", "CANCELLED");
    private static final Set<String> ACTIVE_TRAIN_STATUSES = Set.of("WAITING", "CLAIMED", "PREPARING", "RUNNING", "PENDING");
    private final AgentExecutionTaskTbMapper taskMapper;
    private final AgentExecutionStepTbMapper stepMapper;
    private final AgentExecutionBatchTbMapper batchMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelTrainExecutionService trainExecutionService;
    private final ModelTrainQueueService trainQueueService;
    private final ModelForecastManagementService forecastManagementService;
    private final ObjectMapper objectMapper;

    @Override
    public Object status(String taskNo, boolean includeRecovery) {
        AgentExecutionTaskTb task = owned(taskNo);
        List<AgentExecutionTaskTb> children = taskMapper.selectList(Wrappers.<AgentExecutionTaskTb>lambdaQuery().eq(AgentExecutionTaskTb::getParentTaskNo, taskNo));
        if (children.isEmpty()) refreshFromBusinessBatch(task); else refreshParent(task, children);
        Map<String, Object> result = taskView(task);
        result.put("summary", summary(children.isEmpty() ? List.of(task) : children));
        result.put("currentStepName", stepName(task.getCurrentStepCode()));
        result.put("children", children.stream().map(this::taskView).toList());
        if (includeRecovery) {
            Map<String, Object> recovery = recovery(task);
            result.put("recovery", recovery);
            String action = String.valueOf(recovery.get("recommendedAction"));
            result.put("nextAction", action);
            result.put("allowedActions", allowedActions(action));
            result.put("retryable", "RETRY".equals(action));
            result.put("reason", recovery.get("reason"));
            result.put("resumeFromStep", recovery.get("resumeFromStep"));
        }
        return result;
    }

    @Override
    public Object steps(String taskNo, boolean includeBusinessSteps, boolean includeAttempts) {
        AgentExecutionTaskTb task = owned(taskNo);
        var query = Wrappers.<AgentExecutionStepTb>lambdaQuery().eq(AgentExecutionStepTb::getTaskNo, taskNo);
        if (!includeAttempts)
            query.eq(AgentExecutionStepTb::getTaskAttemptNo, task.getTaskAttemptNo());
        List<AgentExecutionStepTb> agentSteps = stepMapper.selectList(query.orderByAsc(AgentExecutionStepTb::getTaskAttemptNo).orderByAsc(AgentExecutionStepTb::getStepOrder));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", taskNo);
        result.put("taskAttemptNo", task.getTaskAttemptNo());
        result.put("agentSteps", agentSteps);
        result.put("businessBatches", batchMapper.selectList(Wrappers.<AgentExecutionBatchTb>lambdaQuery().eq(AgentExecutionBatchTb::getTaskNo, taskNo)
                .orderByAsc(AgentExecutionBatchTb::getTaskAttemptNo).orderByAsc(AgentExecutionBatchTb::getId)));
        if (includeBusinessSteps && TextUtils.hasText(task.getLatestTrainBatchNo()))
            result.put("trainingSteps", trainExecutionService.getExecutionSteps(task.getLatestTrainBatchNo()));
        else
            result.put("trainingSteps", List.of());
        return result;
    }

    @Override
    @Transactional
    public Object recordStep(String taskNo, StepCommand command) {
        AgentExecutionTaskTb task = owned(taskNo);
        validateStep(command);
        AgentExecutionStepTb step = stepMapper.selectOne(Wrappers.<AgentExecutionStepTb>lambdaQuery().eq(AgentExecutionStepTb::getTaskNo, taskNo)
                .eq(AgentExecutionStepTb::getTaskAttemptNo, task.getTaskAttemptNo()).eq(AgentExecutionStepTb::getStepCode, command.stepCode()).last("limit 1"));
        Date now = new Date();
        if (step == null) {
            step = new AgentExecutionStepTb();
            step.setTaskNo(taskNo);
            step.setTaskAttemptNo(task.getTaskAttemptNo());
            step.setStepLevel(1);
            step.setStepCategory("AGENT_ACTION");
            step.setStepCode(command.stepCode());
            step.setCreatedAt(now);
        }
        step.setStepName(TextUtils.hasText(command.stepName()) ? command.stepName() : command.stepCode());
        step.setStepOrder(command.stepOrder() == null ? 0 : command.stepOrder());
        step.setStatus(command.status());
        step.setRetryable(1);
        step.setInputSummary(command.inputSummary());
        step.setOutputSummary(command.outputSummary());
        step.setCheckpointJson(json(command.checkpoint()));
        step.setErrorCode(command.errorCode());
        step.setErrorMessage(command.errorMessage());
        if ("RUNNING".equals(command.status()) && step.getStartedAt() == null) step.setStartedAt(now);
        if (Set.of("SUCCESS", "FAILED", "SKIPPED", "CANCELLED").contains(command.status())) step.setFinishedAt(now);
        step.setUpdatedAt(now);
        if (step.getId() == null) stepMapper.insert(step); else stepMapper.updateById(step);
        task.setCurrentStepCode(command.stepCode());
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        return step;
    }

    @Override
    @Transactional
    public Object resume(String taskNo, String requestedStep, boolean confirmed) {
        AgentExecutionTaskTb task = owned(taskNo);
        if (!Set.of("FAILED", "INTERRUPTED").contains(task.getStatus()))
            throw new BusinessException("只有FAILED或INTERRUPTED任务可以恢复执行");
        Map<String, Object> recovery = recovery(task);
        String action = String.valueOf(recovery.get("recommendedAction"));
        if ("RETRY".equals(action))
            throw new BusinessException(String.valueOf(recovery.get("reason")) + "；业务批次已明确失败，请使用task_retry创建新批次");
        if ("WAIT".equals(action))
            return Map.of("taskNo", taskNo, "status", "EXECUTING", "recommendedAction", "WAIT", "reason", recovery.get("reason"));
        if (!confirmed)
            throw new BusinessException("恢复任务必须先获得用户确认");
        String resumeFrom = TextUtils.hasText(requestedStep) ? requestedStep : String.valueOf(recovery.get("resumeFromStep"));
        task.setTaskAttemptNo((task.getTaskAttemptNo() == null ? 1 : task.getTaskAttemptNo()) + 1);
        task.setStatus("EXECUTING");
        task.setCurrentStepCode(resumeFrom);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        return Map.of("taskNo", taskNo, "taskAttemptNo", task.getTaskAttemptNo(), "status", "EXECUTING", "resumedFromStep", resumeFrom,
                "nextAction", "智能体从该步骤继续，创建类工具必须复用原idempotencyKey并先核实已有业务批次");
    }

    /** 为明确失败的训练或预测创建新批次，原批次只作为历史关联保留。 */
    @Override
    @Transactional
    public Object retry(String taskNo, String retryStage, boolean confirmed) {
        AgentExecutionTaskTb task = owned(taskNo);
        if (!"FAILED".equals(task.getStatus())) throw new BusinessException("只有FAILED任务可以创建重试批次");
        if (!confirmed) throw new BusinessException("重试会创建新业务批次，必须先获得用户确认");
        String stage = TextUtils.hasText(retryStage) ? retryStage.trim().toUpperCase() : recommendedRetryStage(task);
        return switch (stage) {
            case "TRAINING", "START_TRAINING" -> retryTraining(task);
            case "FORECAST", "START_FORECAST" -> retryForecast(task);
            default -> throw new BusinessException("无法确定重试阶段，请指定TRAINING或FORECAST");
        };
    }

    private Object retryTraining(AgentExecutionTaskTb task) {
        if (!TextUtils.hasText(task.getLatestTrainBatchNo())) throw new BusinessException("任务没有可重试的训练批次");
        ModelTrainRecordTb previous = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, task.getLatestTrainBatchNo()).last("limit 1"));
        if (previous == null) throw new BusinessException("原训练批次不存在：" + task.getLatestTrainBatchNo());
        if (ACTIVE_TRAIN_STATUSES.contains(previous.getStatus())) throw new BusinessException("原训练批次仍在执行，不能创建重试批次");
        ModelTrainConfigTb config = trainConfigMapper.selectById(previous.getTrainConfigId());
        if (config == null) throw new BusinessException("原训练配置不存在，无法重试");
        ModelTrainRecordTb created = trainQueueService.enqueue(config, "AI", true, null);
        int attempt = nextAttempt(task);
        recordBatch(task, attempt, "TRAINING", "TRAIN", created.getBatchNo(), previous.getBatchNo());
        task.setLatestTrainBatchNo(created.getBatchNo());
        activateRetry(task, attempt, "TRAINING", "WAIT_TRAINING_RESULT");
        return Map.of("taskNo", task.getTaskNo(), "taskAttemptNo", attempt, "retryStage", "TRAINING", "previousBatchNo", previous.getBatchNo(), "trainBatchNo",
                created.getBatchNo(), "status", "EXECUTING");
    }

    private Object retryForecast(AgentExecutionTaskTb task) {
        if (!TextUtils.hasText(task.getLatestForecastBatchNo())) throw new BusinessException("任务没有可重试的预测批次");
        ModelForecastRecordTb previous = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .eq(ModelForecastRecordTb::getForecastBatchNo, task.getLatestForecastBatchNo()).last("limit 1"));
        if (previous == null) throw new BusinessException("原预测批次不存在：" + task.getLatestForecastBatchNo());
        if (previous.getStatus() == ModelForecastStatus.RUNNING.getCode()) throw new BusinessException("原预测批次仍在执行，不能创建重试批次");
        if (previous.getStatus() != ModelForecastStatus.FAILED.getCode()) throw new BusinessException("只有失败的预测批次可以重试");
        ModelForecastExecuteRequest request = new ModelForecastExecuteRequest();
        request.setForecastId(previous.getForecastId());
        request.setForecastStartDate(previous.getForecastStartDate());
        request.setForecastHorizon(previous.getForecastHorizon());
        request.setForecastFrequency(previous.getForecastFrequency());
        var created = forecastManagementService.submit(request);
        int attempt = nextAttempt(task);
        recordBatch(task, attempt, "FORECAST", "FORECAST", created.getForecastBatchNo(), previous.getForecastBatchNo());
        task.setLatestForecastBatchNo(created.getForecastBatchNo());
        activateRetry(task, attempt, "FORECASTING", "WAIT_FORECAST_RESULT");
        return Map.of("taskNo", task.getTaskNo(), "taskAttemptNo", attempt, "retryStage", "FORECAST", "previousBatchNo", previous.getForecastBatchNo(), "forecastBatchNo",
                created.getForecastBatchNo(), "status", "EXECUTING");
    }

    private String recommendedRetryStage(AgentExecutionTaskTb task) {
        return TextUtils.hasText(task.getLatestForecastBatchNo()) ? "FORECAST" : "TRAINING";
    }

    private int nextAttempt(AgentExecutionTaskTb task) {
        return (task.getTaskAttemptNo() == null ? 1 : task.getTaskAttemptNo()) + 1;
    }

    private void activateRetry(AgentExecutionTaskTb task, int attempt, String stage, String step) {
        task.setTaskAttemptNo(attempt);
        task.setStatus("EXECUTING");
        task.setCurrentStage(stage);
        task.setCurrentStepCode(step);
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setStartedAt(new Date());
        task.setFinishedAt(null);
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
    }

    private void recordBatch(AgentExecutionTaskTb task, int attempt, String stage, String type, String batchNo, String previousBatchNo) {
        AgentExecutionBatchTb relation = new AgentExecutionBatchTb();
        relation.setTaskNo(task.getTaskNo()); relation.setTaskAttemptNo(attempt); relation.setStage(stage); relation.setBatchType(type); relation.setBatchNo(batchNo);
        relation.setBatchAttemptNo(1); relation.setPreviousBatchNo(previousBatchNo); relation.setRetryMode("NEW_BATCH"); relation.setTriggerSource("AI"); relation.setStatus("EXECUTING");
        relation.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId())); relation.setCreatedByName(SecurityContextHolder.getUserName()); relation.setCreatedAt(new Date()); relation.setUpdatedAt(new Date());
        batchMapper.insert(relation);
    }

    private Map<String, Object> recovery(AgentExecutionTaskTb task) {
        if (TextUtils.hasText(task.getLatestForecastBatchNo())) {
            ModelForecastRecordTb forecast = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                    .eq(ModelForecastRecordTb::getForecastBatchNo, task.getLatestForecastBatchNo()).last("limit 1"));
            if (forecast != null && forecast.getStatus() != null) {
                if (forecast.getStatus() == ModelForecastStatus.RUNNING.getCode()) return recovery("WAIT", "WAIT_FORECAST_RESULT", true, "预测批次仍在执行，不要重复提交");
                if (forecast.getStatus() == ModelForecastStatus.FAILED.getCode()) return recovery("RETRY", "START_FORECAST", true, "预测批次已明确失败，需要创建新预测批次");
                return recovery("RESUME", "RESULT_EXPLANATION", true, "预测已经成功，可继续读取和解释结果");
            }
        }
        if (TextUtils.hasText(task.getLatestTrainBatchNo())) {
            ModelTrainRecordTb train = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, task.getLatestTrainBatchNo()).last("limit 1"));
            if (train != null) {
                if (ACTIVE_TRAIN_STATUSES.contains(train.getStatus())) return recovery("WAIT", "WAIT_TRAINING_RESULT", true, "训练批次仍在执行，不要重复提交");
                if ("SUCCESS".equals(train.getStatus())) return recovery("RESUME", "START_FORECAST", true, "训练已经成功，可从发起预测继续");
                return recovery("RETRY", "START_TRAINING", true, "训练批次已明确失败，需要创建新训练批次");
            }
        }
        return recovery("RESUME", TextUtils.hasText(task.getCurrentStepCode()) ? task.getCurrentStepCode() : "TOOL_CALL", false, "未发现已创建的业务批次，可从失败的智能体步骤继续");
    }

    private Map<String, Object> recovery(String action, String step, boolean existing, String reason) {
        return Map.of("recoverable", !"RETRY".equals(action), "recommendedAction", action, "resumeFromStep", step, "existingBusinessResult", existing, "reason", reason);
    }

    /** 将恢复建议转换成供LangGraph和受限ReAct使用的动作白名单。 */
    private List<String> allowedActions(String action) {
        return switch (action) {
            case "WAIT" -> List.of("WAIT", "QUERY_STATUS");
            case "RESUME" -> List.of("RESUME", "QUERY_STEPS", "ASK_USER");
            case "RETRY" -> List.of("QUERY_STEPS", "RETRY", "ASK_USER", "FAIL");
            default -> List.of("QUERY_STATUS", "ASK_USER", "FAIL");
        };
    }

    private AgentExecutionTaskTb owned(String taskNo) {
        if (!TextUtils.hasText(taskNo)) throw new BusinessException("taskNo不能为空");
        AgentExecutionTaskTb task = taskMapper.selectOne(Wrappers.<AgentExecutionTaskTb>lambdaQuery().eq(AgentExecutionTaskTb::getTaskNo, taskNo)
                .eq(AgentExecutionTaskTb::getCreatedBy, String.valueOf(SecurityContextHolder.getUserId())).last("limit 1"));
        if (task == null) throw new BusinessException("任务不存在或无权访问：" + taskNo);
        return task;
    }

    private Map<String, Object> taskView(AgentExecutionTaskTb task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo()); result.put("taskType", task.getTaskType()); result.put("agentCode", task.getAgentCode()); result.put("status", task.getStatus());
        result.put("currentStage", task.getCurrentStage()); result.put("currentStepCode", task.getCurrentStepCode()); result.put("progressPercent", task.getProgressPercent());
        result.put("taskAttemptNo", task.getTaskAttemptNo()); result.put("latestTrainBatchNo", task.getLatestTrainBatchNo());
        result.put("latestForecastBatchNo", task.getLatestForecastBatchNo()); result.put("errorCode", task.getErrorCode()); result.put("errorMessage", task.getErrorMessage());
        result.put("retryable", Integer.valueOf(1).equals(task.getRetryable()));
        return result;
    }

    /**
     * 以真实训练、预测批次状态刷新任务进度。
     * 任务表提供面向用户的统一进度，业务批次表仍是训练和预测是否真正完成的事实来源。
     */
    private void refreshFromBusinessBatch(AgentExecutionTaskTb task) {
        if (TextUtils.hasText(task.getLatestForecastBatchNo())) {
            ModelForecastRecordTb forecast = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                    .eq(ModelForecastRecordTb::getForecastBatchNo, task.getLatestForecastBatchNo()).last("limit 1"));
            if (forecast == null || forecast.getStatus() == null) return;
            if (forecast.getStatus() == ModelForecastStatus.RUNNING.getCode())
                applyProgress(task, "EXECUTING", "FORECASTING", "WAIT_FORECAST_RESULT", 75, null, false);
            else if (forecast.getStatus() == ModelForecastStatus.SUCCESS.getCode())
                applyProgress(task, "SUCCESS", "COMPLETED", "RESULT_EXPLANATION", 100, null, true);
            else
                applyProgress(task, "FAILED", "FORECASTING", "WAIT_FORECAST_RESULT", value(task.getProgressPercent(), 75), "预测执行失败", true);
            syncBatch(task.getTaskNo(), "FORECAST", task.getLatestForecastBatchNo(), task.getStatus(), task.getErrorMessage());
            return;
        }
        if (!TextUtils.hasText(task.getLatestTrainBatchNo())) return;
        ModelTrainRecordTb train = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .eq(ModelTrainRecordTb::getBatchNo, task.getLatestTrainBatchNo()).last("limit 1"));
        if (train == null) return;
        String status = train.getStatus() == null ? "UNKNOWN" : train.getStatus().toUpperCase();
        if (ACTIVE_TRAIN_STATUSES.contains(status))
            applyProgress(task, "EXECUTING", "TRAINING", "WAIT_TRAINING_RESULT", 35, null, false);
        else if ("SUCCESS".equals(status))
            // 用户已经发起的是预测任务，训练成功后应由任务续跑器进入预测阶段。
            applyProgress(task, "EXECUTING", "FORECASTING", "START_FORECAST", 60, null, false);
        else
            applyProgress(task, "FAILED", "TRAINING", "WAIT_TRAINING_RESULT", value(task.getProgressPercent(), 35),
                    TextUtils.hasText(train.getErrorMessage()) ? train.getErrorMessage() : "训练执行失败", true);
        syncBatch(task.getTaskNo(), "TRAIN", task.getLatestTrainBatchNo(), "SUCCESS".equals(status) ? "SUCCESS" : task.getStatus(), task.getErrorMessage());
    }

    /** 根据所有客户子任务计算批量任务的总体进度和成功、失败状态。 */
    private void refreshParent(AgentExecutionTaskTb parent, List<AgentExecutionTaskTb> children) {
        children.forEach(this::refreshFromBusinessBatch);
        long active = children.stream().filter(item -> Set.of("WAITING", "EXECUTING", "WAITING_CONFIRMATION").contains(item.getStatus())).count();
        long success = children.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count();
        long failed = children.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        BigDecimal progress = children.stream().map(item -> item.getProgressPercent() == null ? BigDecimal.ZERO : item.getProgressPercent())
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(children.size()), 2, RoundingMode.HALF_UP);
        String status = active > 0 ? "EXECUTING" : success == children.size() ? "SUCCESS" : failed == children.size() ? "FAILED" : "PARTIAL_SUCCESS";
        boolean finished = active == 0;
        applyProgress(parent, status, finished ? "COMPLETED" : "BATCH_EXECUTION", finished ? "RESULT_EXPLANATION" : "WAIT_CHILD_TASKS",
                progress.intValue(), failed > 0 ? failed + "个客户执行失败" : null, finished);
    }

    private void applyProgress(AgentExecutionTaskTb task, String status, String stage, String step, int progress, String error, boolean finished) {
        boolean changed = !status.equals(task.getStatus()) || !stage.equals(task.getCurrentStage()) || !step.equals(task.getCurrentStepCode())
                || task.getProgressPercent() == null || task.getProgressPercent().compareTo(BigDecimal.valueOf(progress)) != 0
                || (error != null && !error.equals(task.getErrorMessage()));
        if (!changed) return;
        task.setStatus(status); task.setCurrentStage(stage); task.setCurrentStepCode(step); task.setProgressPercent(BigDecimal.valueOf(progress));
        task.setErrorMessage(error); task.setErrorCode(error == null ? null : "BUSINESS_BATCH_FAILED"); task.setUpdatedAt(new Date());
        if (finished) task.setFinishedAt(new Date());
        taskMapper.updateById(task);
    }

    private void syncBatch(String taskNo, String type, String batchNo, String taskStatus, String error) {
        AgentExecutionBatchTb relation = batchMapper.selectOne(Wrappers.<AgentExecutionBatchTb>lambdaQuery().eq(AgentExecutionBatchTb::getTaskNo, taskNo)
                .eq(AgentExecutionBatchTb::getBatchType, type).eq(AgentExecutionBatchTb::getBatchNo, batchNo).orderByDesc(AgentExecutionBatchTb::getId).last("limit 1"));
        if (relation == null) return;
        String status = "EXECUTING".equals(taskStatus) ? "RUNNING" : taskStatus;
        if (status.equals(relation.getStatus()) && (error == null || error.equals(relation.getErrorMessage()))) return;
        relation.setStatus(status); relation.setErrorMessage(error); relation.setUpdatedAt(new Date());
        if (Set.of("SUCCESS", "FAILED", "CANCELLED").contains(status)) relation.setFinishedAt(new Date());
        batchMapper.updateById(relation);
    }

    private int value(BigDecimal value, int fallback) { return value == null ? fallback : value.intValue(); }

    private String stepName(String code) {
        if (code == null) return "等待开始";
        return switch (code) {
            case "START_FORECAST" -> "准备发起预测";
            case "WAIT_TRAINING_RESULT" -> "等待模型训练完成";
            case "WAIT_FORECAST_RESULT" -> "等待预测计算完成";
            case "AWAIT_FORECAST_REQUEST" -> "训练已完成，等待用户发起预测";
            case "WAIT_CHILD_TASKS" -> "等待批量客户任务完成";
            case "RESULT_EXPLANATION" -> "读取并解释预测结果";
            default -> code;
        };
    }

    private Map<String, Long> summary(List<AgentExecutionTaskTb> tasks) {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) tasks.size());
        summary.put("waiting", tasks.stream().filter(t -> "WAITING".equals(t.getStatus())).count());
        summary.put("running", tasks.stream().filter(t -> "EXECUTING".equals(t.getStatus())).count());
        summary.put("success", tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count());
        summary.put("failed", tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count());
        return summary;
    }

    private void validateStep(StepCommand command) {
        if (command == null || !AGENT_STEP_CODES.contains(command.stepCode())) throw new BusinessException("不允许记录该智能体步骤");
        if (!STEP_STATUSES.contains(command.status())) throw new BusinessException("智能体步骤状态不合法");
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new BusinessException("步骤检查点无法序列化"); }
    }
}
