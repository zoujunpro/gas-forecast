package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.request.ModelForecastConfigSaveRequest;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.enums.ModelForecastStatus;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.business.service.ExecutionBatchProgressService;
import com.gas.forecast.business.service.ForecastApplicationService;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.ExecutionBatchTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelForecastResultTb;
import com.gas.forecast.dao.domain.ModelConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.domain.ModelTrainBacktestTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.ExecutionBatchTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastResultTbMapper;
import com.gas.forecast.dao.mapper.ModelConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainBacktestTbMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.UUID;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 预测应用编排：确保可用训练模型、维护预测配置并提交异步预测任务。 */
@Service
@RequiredArgsConstructor
public class ForecastApplicationServiceImpl implements ForecastApplicationService {
    private final AutoTrainService autoTrainService;
    private final ModelForecastManagementService forecastService;
    private final BaseCustomerTbMapper customerMapper;
    private final DataDailySalesTbMapper dailySalesMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelConfigTbMapper modelConfigMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelTrainBacktestTbMapper trainBacktestMapper;
    private final ModelForecastConfigTbMapper forecastConfigMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastResultTbMapper forecastResultMapper;
    private final ExecutionTaskTbMapper executionTaskMapper;
    private final ExecutionBatchTbMapper executionBatchMapper;
    private final ExecutionBatchProgressService batchProgressService;
    private final ObjectMapper objectMapper;

    @Override
    public Object start(ForecastCommand command) {
        validate(command);
        ExecutionBatchTb existing = existingBatch(command);
        if (existing != null) return existingResult(existing);
        List<BaseCustomerTb> customers = customers(command);
        if (customers.isEmpty())
            throw new BusinessException("当前业务范围内没有找到可预测客户");
        if (customers.size() > 1 && !command.confirmed())
            return batchConfirmation(command, customers);
        Map<String, Object> blocked = validateAvailableModels(command, customers);
        if (blocked != null) return blocked;
        ExecutionBatchTb batch = createExecutionBatch(command, customers);
        List<Map<String, Object>> tasks = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        for (BaseCustomerTb customer : customers) {
            try {
                tasks.add(startOne(command, customer, batch.getBatchNo()));
            } catch (RuntimeException exception) {
                String message = safeMessage(exception);
                ExecutionTaskTb failedTask = createFailedTask(command, customer, batch.getBatchNo(), message);
                failures.add(Map.of("taskNo", failedTask.getTaskNo(), "customerCode", customer.getCustomerCode(),
                        "customerName", customer.getCustomerName(), "message", message));
            }
        }
        ExecutionBatchTb currentBatch = batchProgressService.refresh(batch.getBatchNo());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionBatchNo", batch.getBatchNo());
        if (tasks.size() == 1) result.put("taskNo", tasks.get(0).get("taskNo"));
        result.put("total", customers.size());
        result.put("submitted", tasks.size());
        result.put("failed", failures.size());
        result.put("tasks", tasks);
        result.put("failures", failures);
        String status = aggregateSubmissionStatus(tasks);
        result.put("status", status);
        result.put("batchStatus", currentBatch == null ? null : currentBatch.getStatus());
        result.put("message", submissionMessage("预测", status, tasks.size(), failures));
        return result;
    }

    private String submissionMessage(String action, String status, int submitted, List<Map<String, String>> failures) {
        if ("FAILED".equals(status)) {
            String reason = failures.stream().map(item -> item.get("message")).filter(TextUtils::hasText).distinct().limit(3).reduce((left, right) -> left + "；" + right).orElse("没有任务通过业务校验");
            return action + "任务未创建：" + reason;
        }
        if (!failures.isEmpty()) return action + "任务部分受理：成功" + submitted + "个，失败" + failures.size() + "个";
        return action + "任务已受理";
    }

    /** 预测范围由真实客户命中数决定，命中多个客户时才要求用户确认。 */
    private Map<String, Object> batchConfirmation(ForecastCommand command, List<BaseCustomerTb> customers) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "NEED_CONFIRMATION");
        result.put("message", "本次预测范围命中" + customers.size() + "个客户，确认后将批量发起预测。");
        result.put("affectedCount", customers.size());
        result.put("customers", customers.stream().limit(50).map(customer -> Map.of(
                "customerCode", customer.getCustomerCode(), "customerName", customer.getCustomerName())).toList());
        result.put("truncated", customers.size() > 50);
        result.put("proposedParameters", Map.of("confirmed", true));
        result.put("nextAction", "ASK_USER");
        result.put("allowedActions", List.of("ASK_USER", "CONFIRM", "CANCEL"));
        return result;
    }

    /** 创建任务前校验真实模型状态；没有用户授权时只返回原因，不创建失败任务或擅自训练。 */
    private Map<String, Object> validateAvailableModels(ForecastCommand command, List<BaseCustomerTb> customers) {
        if (command.forceRetrain() || command.autoTrain()) return null;
        List<Map<String, String>> unavailable = new ArrayList<>();
        List<Map<String, String>> incompatible = new ArrayList<>();
        for (BaseCustomerTb customer : customers) {
            ModelTrainConfigTb config = trainConfigMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getAgentCode, command.agentCode())
                    .eq(ModelTrainConfigTb::getCustomerCode, customer.getCustomerCode()).eq(ModelTrainConfigTb::getEnabled, 1).orderByDesc(ModelTrainConfigTb::getUpdatedAt)
                    .orderByDesc(ModelTrainConfigTb::getId).last("limit 1"));
            ModelTrainRecordTb trained = config == null ? null
                    : trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, config.getId())
                            .eq(ModelTrainRecordTb::getStatus, "SUCCESS").orderByDesc(ModelTrainRecordTb::getUpdatedAt).last("limit 1"));
            if (trained == null) {
                unavailable.add(Map.of("customerCode", customer.getCustomerCode(), "customerName", customer.getCustomerName(), "reason", "未找到该客户状态为SUCCESS的训练记录"));
                continue;
            }
            try {
                forecastFrequency(command.forecastFrequency(), config.getTimeGranularity(), command.agentCode());
            } catch (BusinessException exception) {
                incompatible.add(Map.of("customerCode", customer.getCustomerCode(), "customerName", customer.getCustomerName(), "reason", safeMessage(exception)));
            }
        }
        if (unavailable.isEmpty() && incompatible.isEmpty()) return null;
        boolean existingOnly = "USE_EXISTING_ONLY".equalsIgnoreCase(command.trainingPreference());
        Map<String, Object> result = new LinkedHashMap<>();
        boolean hasMismatch = !incompatible.isEmpty();
        result.put("status", hasMismatch ? "NEED_CLARIFICATION" : "NEED_CONFIRMATION");
        result.put("errorCode", hasMismatch ? "MODEL_GRANULARITY_MISMATCH" : existingOnly ? "NO_COMPATIBLE_TRAINED_MODEL" : "TRAINING_REQUIRED");
        result.put("reason", hasMismatch ? "现有模型的时间粒度与用户要求不匹配，需要用户调整预测要求或模型配置"
                : existingOnly ? "用户要求只使用现有模型，但部分业务对象没有匹配的成功模型" : "部分业务对象没有匹配的成功模型，需要先训练");
        result.put("message", hasMismatch ? "现有模型与本次预测要求不匹配，请调整预测参数或模型配置。"
                : existingOnly ? "没有找到可直接使用的成功模型，且当前要求仅使用已有模型。"
                        : "当前客户没有可直接用于预测的成功训练模型，是否先校验数据并训练，训练成功后自动继续预测？");
        result.put("unavailable", unavailable);
        result.put("incompatible", incompatible);
        result.put("affectedCount", unavailable.size() + incompatible.size());
        result.put("nextAction", "ASK_USER");
        result.put("allowedActions", List.of("ASK_USER", "FAIL"));
        if (!hasMismatch) result.put("proposedParameters", Map.of("trainingPreference", "AUTO", "autoTrain", true));
        return result;
    }

    private String aggregateSubmissionStatus(List<Map<String, Object>> tasks) {
        if (tasks.isEmpty()) return "FAILED";
        boolean allWaitingTraining = tasks.stream().allMatch(item -> "WAITING_TRAINING".equals(String.valueOf(item.get("status"))));
        return allWaitingTraining ? "WAITING_TRAINING" : "EXECUTING";
    }

    @Override
    public Object status(String forecastBatchNo) {
        ModelForecastRecordTb record = record(forecastBatchNo);
        long points = forecastResultMapper.selectCount(Wrappers.<ModelForecastResultTb>lambdaQuery().eq(ModelForecastResultTb::getForecastBatchNo, forecastBatchNo));
        String status = statusName(record.getStatus());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("forecastBatchNo", forecastBatchNo);
        result.put("status", "RUNNING".equals(status) ? "EXECUTING" : status);
        result.put("currentStage", "FORECASTING");
        result.put("forecastStartDate", record.getForecastStartDate());
        result.put("forecastHorizon", record.getForecastHorizon());
        result.put("completedPoints", points);
        result.put("updatedAt", record.getUpdatedAt());
        result.put("nextAction", "SUCCESS".equals(status) ? "QUERY_RESULT" : "FAILED".equals(status) ? "MODEL_DECISION" : "WAIT");
        result.put("allowedActions", "SUCCESS".equals(status) ? List.of("QUERY_RESULT")
                : "FAILED".equals(status) ? List.of("QUERY_STATUS", "RETRY", "ASK_USER", "FAIL") : List.of("WAIT", "QUERY_STATUS"));
        result.put("retryable", "FAILED".equals(status));
        return result;
    }

    @Override
    public Object result(String forecastBatchNo, boolean includePoints) {
        ModelForecastRecordTb record = record(forecastBatchNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        ModelTrainRecordTb training = resolveTraining(record);
        if (training != null) {
            result.put("training", trainingSummary(training));
            result.put("backtestPoints", trainBacktestMapper.selectList(Wrappers.<ModelTrainBacktestTb>lambdaQuery()
                    .eq(ModelTrainBacktestTb::getTrainBatchNo, training.getBatchNo()).orderByAsc(ModelTrainBacktestTb::getTrainDate)).stream()
                    .map(this::backtestPoint).toList());
        }
        if (includePoints) {
            List<ModelForecastResultTb> points = forecastResultMapper.selectList(Wrappers.<ModelForecastResultTb>lambdaQuery()
                    .eq(ModelForecastResultTb::getForecastBatchNo, forecastBatchNo).orderByAsc(ModelForecastResultTb::getForecastDate));
            result.put("points", points);
            result.put("lastYearPoints", lastYearPoints(record, points));
        }
        return result;
    }

    private List<Map<String, Object>> lastYearPoints(ModelForecastRecordTb record, List<ModelForecastResultTb> points) {
        Map<String, String> previousToForecastDate = new LinkedHashMap<>();
        for (ModelForecastResultTb point : points) {
            if (!TextUtils.hasText(point.getForecastDate())) continue;
            LocalDate forecastDate = LocalDate.parse(point.getForecastDate());
            previousToForecastDate.put(forecastDate.minusYears(1).toString(), forecastDate.toString());
        }
        if (previousToForecastDate.isEmpty()) return List.of();
        var query = Wrappers.<DataDailySalesTb>query().select("stat_date", "SUM(CAST(gas_sales AS DECIMAL(18,4))) AS gas_sales")
                .in("stat_date", previousToForecastDate.keySet()).isNotNull("gas_sales");
        if (TextUtils.hasText(record.getRegionCode())) query.eq("region_code", record.getRegionCode());
        if (TextUtils.hasText(record.getIndustryCode())) query.eq("industry_code", record.getIndustryCode());
        if (TextUtils.hasText(record.getCustomerCode())) query.eq("customer_code", record.getCustomerCode());
        query.groupBy("stat_date");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : dailySalesMapper.selectMaps(query)) {
            Object sourceDate = mapValue(row, "stat_date");
            Object value = mapValue(row, "gas_sales");
            String forecastDate = sourceDate == null ? null : previousToForecastDate.get(String.valueOf(sourceDate));
            if (TextUtils.hasText(forecastDate) && value != null) result.add(Map.of("date", forecastDate, "value", value));
        }
        result.sort(java.util.Comparator.comparing(item -> String.valueOf(item.get("date"))));
        return result;
    }

    private Object mapValue(Map<String, Object> row, String key) {
        return row.entrySet().stream().filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /** 优先使用同一统一执行批次中的训练任务，再按预测配置和发生时间回退匹配。 */
    private ModelTrainRecordTb resolveTraining(ModelForecastRecordTb forecast) {
        ExecutionTaskTb forecastTask = executionTaskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBusinessType, "FORECAST")
                .eq(ExecutionTaskTb::getBusinessNo, forecast.getForecastBatchNo())
                .orderByDesc(ExecutionTaskTb::getUpdatedAt).last("limit 1"));
        if (forecastTask != null) {
            var trainQuery = Wrappers.<ExecutionTaskTb>lambdaQuery()
                    .eq(ExecutionTaskTb::getBatchNo, forecastTask.getBatchNo())
                    .eq(ExecutionTaskTb::getBusinessType, "TRAIN")
                    .eq(ExecutionTaskTb::getStatus, "SUCCESS");
            if (TextUtils.hasText(forecastTask.getCustomerCode())) {
                trainQuery.eq(ExecutionTaskTb::getCustomerCode, forecastTask.getCustomerCode());
            }
            ExecutionTaskTb trainTask = executionTaskMapper.selectOne(
                    trainQuery.orderByDesc(ExecutionTaskTb::getUpdatedAt).last("limit 1"));
            String trainBatchNo = trainTask == null ? null : trainTask.getBusinessNo();
            if (TextUtils.hasText(trainBatchNo)) {
                ModelTrainRecordTb exact = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                        .eq(ModelTrainRecordTb::getBatchNo, trainBatchNo).last("limit 1"));
                if (exact != null) return exact;
            }
        }
        ModelForecastConfigTb config = forecastConfigMapper.selectById(forecast.getForecastId());
        if (config == null || config.getTrainConfigId() == null) return null;
        var query = Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, config.getTrainConfigId())
                .eq(ModelTrainRecordTb::getStatus, "SUCCESS");
        if (forecast.getCreatedAt() != null) query.le(ModelTrainRecordTb::getCreatedAt, forecast.getCreatedAt());
        return trainRecordMapper.selectOne(query.orderByDesc(ModelTrainRecordTb::getUpdatedAt).orderByDesc(ModelTrainRecordTb::getId).last("limit 1"));
    }

    private Map<String, Object> trainingSummary(ModelTrainRecordTb training) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", training.getBatchNo());
        summary.put("status", training.getStatus());
        summary.put("trainStartDate", training.getTrainStartDate()); summary.put("trainEndDate", training.getTrainEndDate());
        summary.put("bestModel", training.getBestModel()); summary.put("mape", training.getMape()); summary.put("wmape", training.getWmape());
        summary.put("smape", training.getSmape()); summary.put("mae", training.getMae()); summary.put("rmse", training.getRmse()); summary.put("r2", training.getR2());
        return summary;
    }

    private Map<String, Object> backtestPoint(ModelTrainBacktestTb item) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("date", item.getTrainDate()); point.put("actualValue", item.getActualValue()); point.put("predictedValue", item.getPredictedValue());
        return point;
    }

    /** 为一个客户提交训练或预测，并建立对应的统一执行任务。 */
    private Map<String, Object> startOne(ForecastCommand command, BaseCustomerTb customer, String executionBatchNo) {
        ModelTrainConfigTb config = trainConfigMapper.selectOne(Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getAgentCode, command.agentCode())
                .eq(ModelTrainConfigTb::getCustomerCode, customer.getCustomerCode()).eq(ModelTrainConfigTb::getEnabled, 1).orderByDesc(ModelTrainConfigTb::getUpdatedAt)
                .orderByDesc(ModelTrainConfigTb::getId).last("limit 1"));
        ModelTrainRecordTb trained = config == null ? null
                : trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, config.getId()).eq(ModelTrainRecordTb::getStatus, "SUCCESS")
                        .orderByDesc(ModelTrainRecordTb::getUpdatedAt).last("limit 1"));
        if (command.forceRetrain() || trained == null) {
            if (!command.autoTrain())
                throw new BusinessException("客户没有可用训练结果，请先训练或设置autoTrain=true");
            AutoTrainRequest train = new AutoTrainRequest();
            train.setAgentCode(command.agentCode());
            train.setCustomerCode(customer.getCustomerCode());
            train.setTrainMode(command.trainMode());
            train.setRecentPeriods(command.trainingPeriods());
            train.setTrainStartDate(command.trainingStartDate());
            train.setTrainEndDate(command.trainingEndDate());
            train.setForceTrain(command.forceRetrain());
            train.setTriggerSource("MCP");
            var response = autoTrainService.execute(train);
            ModelTrainConfigTb pendingConfig = trainConfigMapper.selectById(response.getTrainConfigId());
            if (pendingConfig != null)
                saveForecastConfig(command, customer, pendingConfig, "用户已发起预测任务；训练成功后继续执行同一任务的预测阶段");
            ExecutionTaskTb task = createTask(command, customer, executionBatchNo, "TRAIN",
                    response.getTrainBatchNo(), "TRAINING", "WAIT_TRAINING_RESULT", 20);
            Map<String, Object> result = taskResult(task);
            result.put("status", "WAITING_TRAINING");
            result.put("trainBatchNo", response.getTrainBatchNo());
            result.put("autoContinue", true);
            result.put("nextAction", "WAIT");
            return result;
        }
        ModelForecastConfigTb forecastConfig = saveForecastConfig(command, customer, config, "MCP发起");
        ModelForecastExecuteRequest execute = new ModelForecastExecuteRequest();
        execute.setForecastId(forecastConfig.getId());
        execute.setForecastStartDate(forecastConfig.getForecastStartDate());
        execute.setForecastHorizon(forecastConfig.getForecastHorizon());
        execute.setForecastFrequency(forecastConfig.getForecastFrequency());
        var response = forecastService.submit(execute);
        ExecutionTaskTb task = createTask(command, customer, executionBatchNo, "FORECAST",
                response.getForecastBatchNo(), "FORECASTING", "WAIT_FORECAST_RESULT", 70);
        Map<String, Object> result = taskResult(task);
        result.put("status", "EXECUTING");
        result.put("trainBatchNo", trained.getBatchNo());
        result.put("forecastBatchNo", response.getForecastBatchNo());
        return result;
    }

    /** 查找同一用户以相同幂等键发起的统一执行批次。 */
    private ExecutionBatchTb existingBatch(ForecastCommand command) {
        if (!TextUtils.hasText(command.idempotencyKey())) return null;
        return executionBatchMapper.selectOne(Wrappers.<ExecutionBatchTb>lambdaQuery()
                .eq(ExecutionBatchTb::getCreatedBy, currentUserId())
                .eq(ExecutionBatchTb::getTriggerSource, "AGENT")
                .eq(ExecutionBatchTb::getIdempotencyKey, command.idempotencyKey()).last("limit 1"));
    }

    private Map<String, Object> existingResult(ExecutionBatchTb batch) {
        ExecutionBatchTb current = batchProgressService.refresh(batch.getBatchNo());
        List<ExecutionTaskTb> tasks = batchProgressService.tasks(batch.getBatchNo());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionBatchNo", batch.getBatchNo());
        if (tasks.size() == 1) result.put("taskNo", tasks.get(0).getTaskNo());
        result.put("status", current == null ? batch.getStatus() : current.getStatus());
        result.put("currentStage", current == null ? batch.getCurrentStage() : current.getCurrentStage());
        result.put("progressPercent", current == null ? batch.getProgressPercent() : current.getProgressPercent());
        result.put("tasks", tasks.stream().map(this::taskResult).toList());
        result.put("idempotentReplay", true);
        return result;
    }

    /** 创建一次用户操作对应的统一执行批次。 */
    private ExecutionBatchTb createExecutionBatch(ForecastCommand command, List<BaseCustomerTb> customers) {
        BaseCustomerTb only = customers.size() == 1 ? customers.get(0) : null;
        Date now = new Date();
        ExecutionBatchTb batch = new ExecutionBatchTb();
        batch.setBatchNo("EXEC-" + compactUuid());
        batch.setBatchName(only == null ? "批量客户预测" : only.getCustomerName() + "预测");
        batch.setBatchType(command.forceRetrain() || command.autoTrain() ? "WORKFLOW" : "FORECAST");
        batch.setTriggerSource("AGENT");
        batch.setConversationNo(command.conversationNo());
        batch.setAgentCode(command.agentCode());
        batch.setScopeType(only == null ? "MULTI_CUSTOMER" : "CUSTOMER");
        batch.setRegionCode(only == null
                ? singleScope(command.regionCodes(), command.regionCode())
                : only.getRegionCode());
        batch.setIndustryCode(only == null
                ? singleScope(command.industryCodes(), command.industryCode())
                : only.getIndustryCode());
        batch.setCustomerCode(only == null ? null : only.getCustomerCode());
        batch.setStatus("PENDING");
        batch.setProgressPercent(BigDecimal.ZERO);
        batch.setTotalCount(0);
        batch.setPendingCount(0);
        batch.setRunningCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setCancelledCount(0);
        batch.setRequestJson(requestJson(command));
        batch.setIdempotencyKey(command.idempotencyKey());
        batch.setCreatedBy(currentUserId());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        executionBatchMapper.insert(batch);
        return batch;
    }

    /** 创建一个具体训练或预测任务；真实业务批次号保存在businessNo。 */
    private ExecutionTaskTb createTask(ForecastCommand command, BaseCustomerTb customer,
            String executionBatchNo, String businessType, String businessNo,
            String stage, String stepCode, int progress) {
        Date now = new Date();
        ExecutionTaskTb task = new ExecutionTaskTb();
        task.setTaskNo("TASK-" + compactUuid());
        task.setBatchNo(executionBatchNo);
        task.setBusinessType(businessType);
        task.setBusinessNo(businessNo);
        task.setAgentCode(command.agentCode());
        task.setScopeType("CUSTOMER");
        task.setRegionCode(customer.getRegionCode());
        task.setRegionName(customer.getRegionName());
        task.setIndustryCode(customer.getIndustryCode());
        task.setIndustryName(customer.getIndustryName());
        task.setCustomerCode(customer.getCustomerCode());
        task.setCustomerName(customer.getCustomerName());
        task.setStatus("RUNNING");
        task.setCurrentStage(stage);
        task.setCurrentStepCode(stepCode);
        task.setProgressPercent(BigDecimal.valueOf(progress));
        task.setAttemptNo(1);
        task.setMaxAttempts(3);
        task.setRequestJson(requestJson(command));
        task.setCreatedBy(currentUserId());
        task.setCreatedAt(now);
        task.setStartedAt(now);
        task.setUpdatedAt(now);
        executionTaskMapper.insert(task);
        return task;
    }

    private ExecutionTaskTb createFailedTask(ForecastCommand command, BaseCustomerTb customer,
            String executionBatchNo, String message) {
        ExecutionTaskTb task = createTask(command, customer, executionBatchNo, "FORECAST", null,
                "FORECASTING", "START_FORECAST", 0);
        Date now = new Date();
        task.setStatus("FAILED");
        task.setErrorCode("FORECAST_START_FAILED");
        task.setErrorMessage(message);
        task.setFinishedAt(now);
        task.setUpdatedAt(now);
        executionTaskMapper.updateById(task);
        return task;
    }

    private Map<String, Object> taskResult(ExecutionTaskTb task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo());
        result.put("executionBatchNo", task.getBatchNo());
        result.put("businessType", task.getBusinessType());
        result.put("customerCode", task.getCustomerCode());
        result.put("customerName", task.getCustomerName());
        // 预测任务的展示范围必须以任务实际客户主数据为准。前端只允许在
        // forecast_start 创建成功后据此切换左侧范围，不能猜测或沿用旧页面选择。
        BaseCustomerTb customer = TextUtils.hasText(task.getCustomerCode())
                ? customerMapper.selectOne(Wrappers.<BaseCustomerTb>lambdaQuery()
                        .eq(BaseCustomerTb::getCustomerCode, task.getCustomerCode()).last("limit 1")) : null;
        if (customer != null) {
            result.put("regionCode", customer.getRegionCode());
            result.put("regionName", customer.getRegionName());
            result.put("industryCode", customer.getIndustryCode());
            result.put("industryName", customer.getIndustryName());
        }
        result.put("status", task.getStatus());
        result.put("businessNo", task.getBusinessNo());
        return result;
    }

    private String currentUserId() { return String.valueOf(SecurityContextHolder.getUserId()); }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private String requestJson(ForecastCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception exception) {
            throw new BusinessException("预测任务参数无法序列化");
        }
    }

    private ModelForecastConfigTb saveForecastConfig(ForecastCommand command, BaseCustomerTb customer, ModelTrainConfigTb trainConfig, String remark) {
        ModelConfigTb model = trainConfig.getModelId() == null ? null : modelConfigMapper.selectById(trainConfig.getModelId());
        String frequency = forecastFrequency(command.forecastFrequency(), trainConfig.getTimeGranularity(), command.agentCode());
        int maximumHorizon = maximumHorizon(model, command.agentCode());
        String startDate = forecastStartDate(command.forecastStartDate(), customer.getCustomerCode());
        int horizon = forecastHorizon(command.forecastHorizon(), command.forecastEndDate(), startDate, frequency, maximumHorizon, command.agentCode());
        ModelForecastConfigTb existing = forecastConfigMapper.selectOne(Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getTrainConfigId, trainConfig.getId())
                .eq(ModelForecastConfigTb::getCustomerCode, customer.getCustomerCode()).eq(ModelForecastConfigTb::getEnabled, 1).orderByDesc(ModelForecastConfigTb::getUpdatedAt).last("limit 1"));
        ModelForecastConfigSaveRequest request = new ModelForecastConfigSaveRequest();
        request.setId(existing == null ? null : existing.getId());
        request.setForecastName(customer.getCustomerName() + "未来" + horizon + "天预测");
        request.setForecastStartDate(startDate);
        request.setForecastHorizon(horizon);
        request.setForecastFrequency(frequency);
        request.setTrainConfigCode(trainConfig.getTrainCode());
        request.setCustomerCode(customer.getCustomerCode());
        request.setEnabled(1);
        request.setRemark(remark);
        return forecastService.saveConfig(request);
    }

    /** 根据模型训练粒度确定预测粒度；用户传值必须与当前智能体模型一致。 */
    private String forecastFrequency(String requested, String modelGranularity, String agentCode) {
        String expected = switch (TextUtils.hasText(modelGranularity) ? modelGranularity.trim().toUpperCase() : "") {
            case "MONTH", "MONTHLY" -> "MONTHLY";
            case "TENDAY" -> "TENDAY";
            case "DAY", "DAILY" -> "DAILY";
            default -> switch (agentCode) {
                case "monthly-sales" -> "MONTHLY";
                case "winter-supply" -> "TENDAY";
                default -> "DAILY";
            };
        };
        if (TextUtils.hasText(requested) && !expected.equalsIgnoreCase(requested))
            throw new BusinessException("当前智能体只支持" + expected + "预测粒度，不能使用" + requested);
        return expected;
    }

    /** 优先使用模型配置的最大预测期数，配置缺失时按智能体采用安全默认上限。 */
    private int maximumHorizon(ModelConfigTb model, String agentCode) {
        if (model != null && model.getMaxForecastHorizon() != null && model.getMaxForecastHorizon() > 0)
            return model.getMaxForecastHorizon();
        return switch (agentCode) {
            case "monthly-sales" -> 24;
            case "winter-supply" -> 18;
            default -> 90;
        };
    }

    /** 用户指定结束日期时由日期区间计算预测期数；否则使用用户周期或默认30期。 */
    private int forecastHorizon(Integer requestedHorizon, String requestedEndDate, String startDate, String frequency, int maximumHorizon, String agentCode) {
        if (!TextUtils.hasText(requestedEndDate)) {
            int horizon = requestedHorizon == null ? defaultHorizon(agentCode) : requestedHorizon;
            if (horizon <= 0 || horizon > maximumHorizon)
                throw new BusinessException("当前模型预测周期必须在1到" + maximumHorizon + "期之间");
            return horizon;
        }
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(requestedEndDate);
            if (end.isBefore(start))
                throw new BusinessException("预测结束日期不能早于预测开始日期");
            long periods = switch (frequency.toUpperCase()) {
                case "MONTHLY" -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)) + 1;
                case "TENDAY" -> (ChronoUnit.DAYS.between(start, end) + 10) / 10;
                default -> ChronoUnit.DAYS.between(start, end) + 1;
            };
            if (periods > maximumHorizon)
                throw new BusinessException("预测时间范围换算后不能超过当前模型允许的" + maximumHorizon + "期");
            return (int) periods;
        } catch (DateTimeParseException exception) {
            throw new BusinessException("预测日期格式必须为 yyyy-MM-dd");
        }
    }

    private int defaultHorizon(String agentCode) {
        return switch (agentCode) {
            case "monthly-sales" -> 12;
            case "winter-supply" -> 18;
            default -> 30;
        };
    }

    private List<BaseCustomerTb> customers(ForecastCommand command) {
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (command.customerCodes() != null && !command.customerCodes().isEmpty())
            query.in(BaseCustomerTb::getCustomerCode, command.customerCodes());
        else {
            boolean hasRegion = command.regionCodes() != null && !command.regionCodes().isEmpty()
                    || TextUtils.hasText(command.regionCode());
            boolean hasIndustry = command.industryCodes() != null && !command.industryCodes().isEmpty()
                    || TextUtils.hasText(command.industryCode());
            if (!hasRegion && !hasIndustry)
                throw new BusinessException("预测范围至少需要客户、地区或行业之一");
            if (command.regionCodes() != null && !command.regionCodes().isEmpty())
                query.in(BaseCustomerTb::getRegionCode, command.regionCodes());
            else if (TextUtils.hasText(command.regionCode()))
                query.eq(BaseCustomerTb::getRegionCode, command.regionCode());
            if (command.industryCodes() != null && !command.industryCodes().isEmpty())
                query.in(BaseCustomerTb::getIndustryCode, command.industryCodes());
            else if (TextUtils.hasText(command.industryCode()))
                query.eq(BaseCustomerTb::getIndustryCode, command.industryCode());
        }
        return customerMapper.selectList(query.orderByAsc(BaseCustomerTb::getCustomerName));
    }

    private String singleScope(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) return fallback;
        return values.size() == 1 ? values.get(0) : null;
    }

    private String forecastStartDate(String requested, String customerCode) {
        DataDailySalesTb latest = dailySalesMapper.selectOne(Wrappers.<DataDailySalesTb>lambdaQuery().select(DataDailySalesTb::getStatDate).eq(DataDailySalesTb::getCustomerCode, customerCode)
                .isNotNull(DataDailySalesTb::getGasSales).orderByDesc(DataDailySalesTb::getStatDate).last("limit 1"));
        String minimum = latest == null || !TextUtils.hasText(latest.getStatDate()) ? LocalDate.now().toString() : LocalDate.parse(latest.getStatDate()).plusDays(1).toString();
        if (!TextUtils.hasText(requested))
            return minimum;
        try {
            LocalDate.parse(requested);
        } catch (DateTimeParseException exception) {
            throw new BusinessException("预测日期格式必须为 yyyy-MM-dd");
        }
        if (requested.compareTo(minimum) < 0)
            throw new BusinessException("预测开始日期不能早于客户最新销量数据的下一天：" + minimum);
        return requested;
    }

    private ModelForecastRecordTb record(String batchNo) {
        if (!TextUtils.hasText(batchNo)) throw new BusinessException("预测批次号不能为空");
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastBatchNo, batchNo).last("limit 1"));
        if (record == null) throw new BusinessException("没有找到预测批次" + batchNo);
        return record;
    }

    private void validate(ForecastCommand command) {
        if (command == null || !TextUtils.hasText(command.agentCode())) throw new BusinessException("agentCode不能为空");
        if ((command.autoTrain() || command.forceRetrain()) && !command.confirmed())
            throw new BusinessException("自动训练或重新训练必须先获得用户确认");
    }

    private String statusName(Integer status) {
        if (status == null) return "UNKNOWN";
        for (ModelForecastStatus value : ModelForecastStatus.values()) if (value.getCode() == status) return value.name();
        return "UNKNOWN";
    }

    private String safeMessage(RuntimeException exception) {
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "创建预测任务失败";
    }
}
