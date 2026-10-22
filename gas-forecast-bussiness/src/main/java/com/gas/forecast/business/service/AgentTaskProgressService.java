package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.ai.dto.AiTaskProgressResponse;
import com.gas.forecast.business.ai.dto.AiTrainingProgressResponse;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AiConversationTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.domain.ExecutionBatchTb;
import com.gas.forecast.dao.domain.ExecutionTaskTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AiConversationTbMapper;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.gas.forecast.dao.mapper.ExecutionBatchTbMapper;
import com.gas.forecast.dao.mapper.ExecutionTaskTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 查询已创建任务的进度，并推进训练完成后的确定性预测续跑。 */
@Service
@RequiredArgsConstructor
public class AgentTaskProgressService {
    private final AiConversationTbMapper conversationMapper;
    private final BaseCustomerTbMapper customerMapper;
    private final ExecutionTaskTbMapper taskMapper;
    private final ExecutionBatchTbMapper batchMapper;
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final TaskForecastContinuationService continuationService;
    private final AgentExecutionTaskService taskService;
    private final ForecastApplicationService forecastApplicationService;
    private final ObjectMapper objectMapper;

    public AiTrainingProgressResponse trainingProgress(String batchNo) {
        ModelTrainRecordTb record = trainRecordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery()
                .select(ModelTrainRecordTb::getBatchNo, ModelTrainRecordTb::getStatus, ModelTrainRecordTb::getErrorMessage,
                        ModelTrainRecordTb::getCustomerCode, ModelTrainRecordTb::getCustomerName,
                        ModelTrainRecordTb::getTrainStartDate, ModelTrainRecordTb::getTrainEndDate)
                .eq(ModelTrainRecordTb::getBatchNo, batchNo).last("limit 1"));
        if (record == null) throw new BusinessException("训练批次不存在：" + batchNo);
        ExecutionTaskTb task = taskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBusinessType, "TRAIN")
                .eq(ExecutionTaskTb::getBusinessNo, batchNo)
                .orderByDesc(ExecutionTaskTb::getUpdatedAt).last("limit 1"));
        // 训练批次才是真实状态来源。查询进度时同步任务表，避免批次已结束而任务仍永久停在 EXECUTING。
        if (task != null) taskService.status(task.getTaskNo(), false);
        boolean failed = isFailed(record.getStatus());
        String message = failed ? trainingFailureMessage(record) : null;
        // 对前端输出统一终态，内部可继续保留 FAILED_FINAL 等更细状态。
        String responseStatus = failed ? "FAILED" : record.getStatus();
        ExecutionTaskTb forecastTask = task == null ? null : forecastTask(task.getBatchNo(), task.getCustomerCode());
        ExecutionBatchTb batch = task == null ? null : batchMapper.selectOne(Wrappers.<ExecutionBatchTb>lambdaQuery()
                .eq(ExecutionBatchTb::getBatchNo, task.getBatchNo()).last("limit 1"));
        boolean continuationExpected = batch != null && "WORKFLOW".equals(batch.getBatchType());
        return new AiTrainingProgressResponse(batchNo, responseStatus, message, task == null ? null : task.getTaskNo(),
                continuationExpected, forecastTask == null ? null : forecastTask.getBusinessNo());
    }

    public AiTaskProgressResponse continueForecast(String conversationNo, String trainBatchNo) {
        findOwned(conversationNo);
        ExecutionTaskTb trainTask = taskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBusinessType, "TRAIN")
                .eq(ExecutionTaskTb::getBusinessNo, trainBatchNo)
                .orderByDesc(ExecutionTaskTb::getUpdatedAt).last("limit 1"));
        if (trainTask == null || !belongsToConversation(trainTask.getBatchNo(), conversationNo))
            throw new BusinessException("未找到当前会话对应的训练任务");
        continuationService.continueAfterTraining(trainBatchNo);
        ExecutionTaskTb forecastTask = forecastTask(trainTask.getBatchNo(), trainTask.getCustomerCode());
        if (forecastTask != null && TextUtils.hasText(forecastTask.getBusinessNo()))
            return forecastProgress(conversationNo, forecastTask.getBusinessNo());
        String status = forecastTask != null && "FAILED".equals(forecastTask.getStatus()) ? "FAILED" : "WAITING_TRAINING";
        String answer = "FAILED".equals(status) ? forecastTask.getErrorMessage() : "训练完成后的预测续跑任务已接收。";
        return response(conversationNo, status, answer, trainBatchNo, null);
    }

    public AiTaskProgressResponse forecastProgress(String conversationNo, String batchNo) {
        findOwned(conversationNo);
        ModelForecastRecordTb record = forecastRecordMapper.selectOne(Wrappers.<ModelForecastRecordTb>lambdaQuery()
                .eq(ModelForecastRecordTb::getForecastBatchNo, batchNo).last("limit 1"));
        if (record == null) throw new BusinessException("预测批次不存在：" + batchNo);
        ExecutionTaskTb task = taskMapper.selectOne(Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBusinessType, "FORECAST")
                .eq(ExecutionTaskTb::getBusinessNo, batchNo)
                .orderByDesc(ExecutionTaskTb::getUpdatedAt)
                .last("limit 1"));
        if (task == null || !belongsToConversation(task.getBatchNo(), conversationNo))
            throw new BusinessException("该预测批次不属于当前会话");

        String trainBatchNo = trainingBatchNo(task.getBatchNo(), task.getCustomerCode());
        String status = Integer.valueOf(1).equals(record.getStatus()) ? "EXECUTING"
                : Integer.valueOf(3).equals(record.getStatus()) ? "FAILED" : "COMPLETED";
        BaseCustomerTb customer = TextUtils.hasText(task.getCustomerCode())
                ? customerMapper.selectOne(Wrappers.<BaseCustomerTb>lambdaQuery()
                        .eq(BaseCustomerTb::getCustomerCode, task.getCustomerCode()).last("limit 1")) : null;
        List<String> scopeParts = new ArrayList<>();
        if (customer != null) {
            if (TextUtils.hasText(customer.getRegionName())) scopeParts.add(customer.getRegionName());
            if (TextUtils.hasText(customer.getIndustryName())) scopeParts.add(customer.getIndustryName());
            if (TextUtils.hasText(customer.getCustomerName())) scopeParts.add(customer.getCustomerName());
        }
        String scope = customer == null ? task.getCustomerName() : String.join(" / ", scopeParts);
        String answer = "EXECUTING".equals(status) ? "预测正在后台执行"
                : "FAILED".equals(status) ? "预测未能完成：" + failureMessage(record.getResponseParam())
                : "预测已完成：" + scope + "，预测批次：" + batchNo;
        AiTaskProgressResponse.ForecastResult forecastResult = "COMPLETED".equals(status) ? forecastResult(batchNo, customer) : null;
        JsonNode presentation = forecastResult == null ? null : forecastPresentation();
        return new AiTaskProgressResponse(conversationNo, status, answer, trainBatchNo, batchNo,
                null, forecastResult, null, List.of(), presentation);
    }

    private AiTaskProgressResponse response(String conversationNo, String status, String answer, String trainBatchNo, String forecastBatchNo) {
        return new AiTaskProgressResponse(conversationNo, status, answer, trainBatchNo, forecastBatchNo, null, null, null, List.of(), null);
    }

    private AiTaskProgressResponse.ForecastResult forecastResult(String batchNo, BaseCustomerTb customer) {
        JsonNode data = objectMapper.valueToTree(forecastApplicationService.result(batchNo, true));
        JsonNode record = data.path("record");
        List<AiTaskProgressResponse.ForecastPoint> points = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal minimum = null;
        BigDecimal maximum = null;
        for (JsonNode item : data.path("points")) {
            String date = item.path("forecastDate").asText(null);
            BigDecimal value = decimal(item.get("forecastValue"));
            if (!TextUtils.hasText(date) || value == null) continue;
            points.add(new AiTaskProgressResponse.ForecastPoint(date, value));
            total = total.add(value);
            minimum = minimum == null || value.compareTo(minimum) < 0 ? value : minimum;
            maximum = maximum == null || value.compareTo(maximum) > 0 ? value : maximum;
        }
        if (points.isEmpty()) return null;
        JsonNode training = data.path("training");
        AiTaskProgressResponse.TrainingResult trainingResult = training.isObject()
                ? new AiTaskProgressResponse.TrainingResult(text(training, "batchNo"), text(training, "trainStartDate"), text(training, "trainEndDate"), text(training, "bestModel"),
                        decimal(training.get("mape")), decimal(training.get("wmape")), decimal(training.get("smape")), decimal(training.get("mae")),
                        decimal(training.get("rmse")), decimal(training.get("r2")))
                : null;
        List<AiTaskProgressResponse.BacktestPoint> backtests = new ArrayList<>();
        for (JsonNode item : data.path("backtestPoints")) {
            String date = item.path("date").asText(null);
            if (TextUtils.hasText(date)) backtests.add(new AiTaskProgressResponse.BacktestPoint(date, decimal(item.get("actualValue")), decimal(item.get("predictedValue"))));
        }
        List<AiTaskProgressResponse.ForecastPoint> lastYearPoints = new ArrayList<>();
        for (JsonNode item : data.path("lastYearPoints")) {
            String date = item.path("date").asText(null);
            BigDecimal value = decimal(item.get("value"));
            if (TextUtils.hasText(date) && value != null) lastYearPoints.add(new AiTaskProgressResponse.ForecastPoint(date, value));
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(points.size()), 4, RoundingMode.HALF_UP);
        return new AiTaskProgressResponse.ForecastResult(
                batchNo,
                customer == null ? null : customer.getRegionName(),
                customer == null ? null : customer.getIndustryName(),
                customer == null ? text(record, "customerCode") : customer.getCustomerCode(),
                customer == null ? text(record, "customerName") : customer.getCustomerName(),
                average, minimum, maximum, trainingResult, points, List.of(), lastYearPoints, backtests);
    }

    private JsonNode forecastPresentation() {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("type", "FORECAST_CHART");
        value.put("chartType", "LINE");
        value.put("title", "用气预测趋势");
        return value;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return TextUtils.hasText(value) ? value : null;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) return null;
        return node.decimalValue();
    }

    private String failureMessage(byte[] response) {
        if (response == null || response.length == 0) return "模型平台没有返回具体原因";
        String reason = new String(response, java.nio.charset.StandardCharsets.UTF_8).trim();
        try {
            com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(reason);
            for (String field : List.of("message", "errorMessage", "detail", "reason")) {
                String value = json.path(field).asText(null);
                if (TextUtils.hasText(value)) return value;
            }
        } catch (Exception ignored) {
            // 非 JSON 响应直接按原始文本展示。
        }
        return reason.length() > 300 ? reason.substring(0, 300) + "…" : reason;
    }

    private boolean isFailed(String status) {
        return status != null && ("FAILED".equalsIgnoreCase(status) || "FAILED_FINAL".equalsIgnoreCase(status)
                || "PREPARE_FAILED".equalsIgnoreCase(status));
    }

    private String trainingFailureMessage(ModelTrainRecordTb record) {
        String error = record.getErrorMessage();
        if (!TextUtils.hasText(error)) return "训练未完成，但服务端没有记录具体原因，请提供批次号 " + record.getBatchNo() + " 排查。";
        Matcher days = Pattern.compile("至少需要\\s*(\\d+)\\s*天").matcher(error);
        if (days.find()) {
            String customer = TextUtils.hasText(record.getCustomerName()) ? record.getCustomerName()
                    : TextUtils.hasText(record.getCustomerCode()) ? record.getCustomerCode() : "当前客户";
            Integer actualDays = trainingDays(record.getTrainStartDate(), record.getTrainEndDate());
            String actual = actualDays == null ? "现有连续数据天数不足" : "现有训练范围共" + actualDays + "天";
            return customer + "的训练数据不满足要求：" + actual + "，模型至少需要" + days.group(1)
                    + "天连续日数据。请补充历史销量并通过数据质量校验后重新训练。";
        }
        if (error.contains("全零")) return "训练数据全部为0，无法学习有效规律。请检查销量数据来源和单位后重新训练。";
        if (error.contains("数据为空") || error.contains("无有效数据")) return "没有可用于训练的有效销量数据。请先导入并校验历史数据。";
        if (error.contains("开始日期不能晚于结束日期")) return "训练日期范围无效：开始日期不能晚于结束日期。请调整日期后重试。";
        return error;
    }

    private Integer trainingDays(String start, String end) {
        if (!TextUtils.hasText(start) || !TextUtils.hasText(end)) return null;
        try {
            return Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(
                    java.time.LocalDate.parse(start), java.time.LocalDate.parse(end)) + 1);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ExecutionTaskTb forecastTask(String executionBatchNo, String customerCode) {
        var query = Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBatchNo, executionBatchNo)
                .eq(ExecutionTaskTb::getBusinessType, "FORECAST");
        if (TextUtils.hasText(customerCode)) query.eq(ExecutionTaskTb::getCustomerCode, customerCode);
        return taskMapper.selectOne(query.orderByDesc(ExecutionTaskTb::getUpdatedAt).last("limit 1"));
    }

    /** 从同一统一执行批次中查找当前预测对应的训练业务批次。 */
    private String trainingBatchNo(String executionBatchNo, String customerCode) {
        var query = Wrappers.<ExecutionTaskTb>lambdaQuery()
                .eq(ExecutionTaskTb::getBatchNo, executionBatchNo)
                .eq(ExecutionTaskTb::getBusinessType, "TRAIN");
        if (TextUtils.hasText(customerCode)) query.eq(ExecutionTaskTb::getCustomerCode, customerCode);
        ExecutionTaskTb task = taskMapper.selectOne(
                query.orderByDesc(ExecutionTaskTb::getUpdatedAt).last("limit 1"));
        return task == null ? null : task.getBusinessNo();
    }

    private boolean belongsToConversation(String executionBatchNo, String conversationNo) {
        return batchMapper.selectCount(Wrappers.<ExecutionBatchTb>lambdaQuery()
                .eq(ExecutionBatchTb::getBatchNo, executionBatchNo)
                .eq(ExecutionBatchTb::getConversationNo, conversationNo)
                .eq(ExecutionBatchTb::getCreatedBy, String.valueOf(SecurityContextHolder.getUserId()))) > 0;
    }

    private AiConversationTb findOwned(String conversationNo) {
        AiConversationTb value = conversationMapper.selectOne(Wrappers.<AiConversationTb>lambdaQuery()
                .eq(AiConversationTb::getConversationNo, conversationNo)
                .eq(AiConversationTb::getCreatedBy, String.valueOf(SecurityContextHolder.getUserId())).last("limit 1"));
        if (value == null) throw new BusinessException("会话不存在或无权访问"); return value;
    }
}
