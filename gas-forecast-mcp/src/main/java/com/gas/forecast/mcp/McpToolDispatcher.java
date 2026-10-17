package com.gas.forecast.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.service.AgentMemoryService;
import com.gas.forecast.business.service.AgentBusinessQueryService;
import com.gas.forecast.business.service.AgentExecutionTaskService;
import com.gas.forecast.business.service.AgentExecutionTaskService.StepCommand;
import com.gas.forecast.business.service.BusinessEntityResolutionService;
import com.gas.forecast.business.service.BusinessEntityResolutionService.*;
import com.gas.forecast.business.service.ForecastApplicationService;
import com.gas.forecast.business.service.ForecastApplicationService.ForecastCommand;
import com.gas.forecast.business.service.TrainingApplicationService;
import com.gas.forecast.business.service.TrainingApplicationService.TrainingCommand;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** MCP协议适配器：只转换参数并调用Business Service，不依赖AI模块。 */
@Component
@RequiredArgsConstructor
public class McpToolDispatcher {
    private final AgentMemoryService memoryService;
    private final AgentBusinessQueryService queryService;
    private final AgentExecutionTaskService taskService;
    private final BusinessEntityResolutionService entityService;
    private final TrainingApplicationService trainingService;
    private final ForecastApplicationService forecastService;
    private final ObjectMapper objectMapper;

    /**
     * MCP工具统一调用入口。
     * <p>
     * 根据工具名称把Map参数转换为Business Service所需命令，并将业务异常转换成稳定的工具错误结构。
     * 业务参数错误不可重试；未预期的运行时异常会标记为可重试。
     * </p>
     *
     * @param toolName tools/list公布的工具名称
     * @param arguments MCP客户端传入的工具参数，可为空
     * @return 统一的MCP工具执行结果
     */
    public McpToolResult invoke(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        try {
            return switch (toolName) {
                case "memory_get" -> success(toolName, "记忆查询成功", memoryService.get(requiredText(args, "conversationNo")));
                case "memory_update" -> memoryUpdate(args);
                case "customer_search" -> resolve(args, EntityType.CUSTOMER);
                case "region_search" -> resolve(args, EntityType.REGION);
                case "industry_search" -> resolve(args, EntityType.INDUSTRY);
                case "region_list" -> success(toolName, "地区列表查询成功", queryService.regions(text(args, "keyword"), integer(args, "pageNo"), integer(args, "pageSize"), bool(args, "fetchAll", false)));
                case "industry_list" -> success(toolName, "行业列表查询成功", queryService.industries(text(args, "keyword"), integer(args, "pageNo"), integer(args, "pageSize"), bool(args, "fetchAll", false)));
                case "customer_list" -> success(toolName, "客户列表查询成功",
                        queryService.customers(text(args, "keyword"), text(args, "regionCode"), text(args, "industryCode"), integer(args, "pageNo"), integer(args, "pageSize"), bool(args, "fetchAll", false)));
                case "business_reference_resolve" -> success(toolName, "业务编号校验完成",
                        queryService.resolveBusinessReference(requiredText(args, "reference")));
                case "training_start" -> success(toolName, "训练任务已受理", trainingService.start(trainingCommand(args)));
                case "training_status_get" -> success(toolName, "训练进度查询成功", trainingService.status(batch(args, "trainBatchNo", "lastTrainBatchNo")));
                case "training_result_get" -> success(toolName, "训练结果查询成功", trainingService.result(batch(args, "trainBatchNo", "lastTrainBatchNo")));
                case "training_latest_get" -> success(toolName, "最新训练查询成功", queryService.latestTraining(requiredText(args, "customerCode"), text(args, "agentCode")));
                case "training_history_get" -> success(toolName, "训练历史查询成功", queryService.trainingHistory(requiredText(args, "customerCode"), text(args, "agentCode"), text(args, "status"),
                        integer(args, "pageNo"), integer(args, "pageSize")));
                case "forecast_start" -> success(toolName, "预测任务已受理", forecastService.start(forecastCommand(args)));
                case "forecast_status_get" -> success(toolName, "预测进度查询成功", forecastService.status(batch(args, "forecastBatchNo", "lastForecastBatchNo")));
                case "forecast_result_get" -> success(toolName, "预测结果查询成功", forecastService.result(batch(args, "forecastBatchNo", "lastForecastBatchNo"), bool(args, "includePoints", true)));
                case "forecast_latest_get" -> success(toolName, "最新预测查询成功",
                        queryService.latestForecast(requiredText(args, "customerCode"), text(args, "agentCode"), bool(args, "includePoints", true)));
                case "forecast_history_get" -> success(toolName, "预测历史查询成功", queryService.forecastHistory(requiredText(args, "customerCode"), text(args, "agentCode"),
                        integer(args, "status"), integer(args, "pageNo"), integer(args, "pageSize")));
                case "task_status_get" -> success(toolName, "任务状态查询成功", taskService.status(requiredText(args, "taskNo"), bool(args, "includeRecovery", true)));
                case "task_steps_get" -> success(toolName, "任务步骤查询成功",
                        taskService.steps(requiredText(args, "taskNo"), bool(args, "includeBusinessSteps", true), bool(args, "includeAttempts", false)));
                case "task_step_record" -> success(toolName, "智能体步骤记录成功", taskService.recordStep(requiredText(args, "taskNo"), stepCommand(args)));
                case "task_resume" -> success(toolName, "任务恢复指令已生成",
                        taskService.resume(requiredText(args, "taskNo"), text(args, "resumeFromStep"), bool(args, "confirmed", false)));
                case "task_retry" -> success(toolName, "任务重试批次已创建",
                        taskService.retry(requiredText(args, "taskNo"), text(args, "retryStage"), bool(args, "confirmed", false)));
                default -> failure(toolName, "TOOL_NOT_FOUND", "未知工具：" + toolName, false);
            };
        } catch (BusinessException | IllegalArgumentException exception) {
            String reason = safeMessage(exception);
            return failure(toolName, classify(reason), reason, false);
        } catch (RuntimeException exception) {
            String reason = rootCauseMessage(exception);
            return failure(toolName, "INTERNAL_ERROR", "工具执行失败：" + reason, true);
        }
    }

    /**
     * 更新指定会话的工作记忆。
     *
     * @param args 包含conversationNo、operation和values的工具参数
     * @return 更新后的完整记忆
     */
    @SuppressWarnings("unchecked")
    private McpToolResult memoryUpdate(Map<String, Object> args) {
        Object raw = args.get("values");
        Map<String, Object> values = raw instanceof Map<?, ?> ? objectMapper.convertValue(raw, Map.class) : Map.of();
        return success("memory_update", "记忆更新成功", memoryService.update(requiredText(args, "conversationNo"), text(args, "operation"), values));
    }

    /**
     * 根据实体类型查询客户、地区或行业；多条匹配时返回候选项，由调用方继续确认。
     *
     * @param args 实体编码、名称以及可选查询条数
     * @param type 要解析的实体类型
     * @return 唯一匹配或待确认候选结果
     */
    private McpToolResult resolve(Map<String, Object> args, EntityType type) {
        EntityQuery query = query(args);
        ResolutionResult result = switch (type) {
            case CUSTOMER -> entityService.resolveCustomer(query);
            case REGION -> entityService.resolveRegion(query);
            case INDUSTRY -> entityService.resolveIndustry(query);
        };
        String status = "UNIQUE".equals(result.status()) ? "COMPLETED" : "MULTIPLE".equals(result.status()) ? "NEED_SELECTION" : "NOT_FOUND";
        return McpToolResult.success(status, "ENTITY", result.message(), result);
    }

    /** 将MCP训练参数转换成Business Service训练命令。 */
    private TrainingCommand trainingCommand(Map<String, Object> args) {
        Scope scope = scope(args);
        return new TrainingCommand(defaultText(text(args, "agentCode"), "short-term"), scope.customerCodes(), scope.regionCode(), scope.industryCode(), bool(args, "bulk", false),
                text(args, "trainMode"), integer(args, "trainingPeriods"), text(args, "trainingStartDate"), text(args, "trainingEndDate"), bool(args, "forceRetrain", false),
                bool(args, "confirmed", false), text(args, "idempotencyKey"));
    }

    /** 将MCP预测参数转换成Business Service预测命令。 */
    private ForecastCommand forecastCommand(Map<String, Object> args) {
        Scope scope = scope(args);
        return new ForecastCommand(text(args, "conversationNo"), defaultText(text(args, "agentCode"), "short-term"), scope.customerCodes(), scope.regionCode(), scope.industryCode(), bool(args, "bulk", false),
                text(args, "forecastStartDate"), text(args, "forecastEndDate"), integer(args, "forecastHorizon"), text(args, "forecastFrequency"), text(args, "trainMode"), integer(args, "trainingPeriods"),
                text(args, "trainingStartDate"), text(args, "trainingEndDate"), bool(args, "forceRetrain", false), bool(args, "autoTrain", false),
                defaultText(text(args, "trainingPreference"), "UNSPECIFIED"), bool(args, "confirmed", false),
                text(args, "idempotencyKey"));
    }

    /**
     * 解析训练或预测的业务范围。
     * <p>优先使用编码；只传名称时调用实体服务换取唯一编码，并由客户档案补充地区和行业。</p>
     */
    private Scope scope(Map<String, Object> args) {
        String customerCode = text(args, "customerCode");
        String regionCode = text(args, "regionCode");
        String industryCode = text(args, "industryCode");
        if (!TextUtils.hasText(customerCode) && TextUtils.hasText(text(args, "customerName"))) {
            EntityCandidate candidate = unique(entityService.resolveCustomer(query(args)));
            customerCode = candidate.code();
            regionCode = candidate.regionCode();
            industryCode = candidate.industryCode();
        }
        if (!TextUtils.hasText(regionCode) && TextUtils.hasText(text(args, "regionName"))) regionCode = unique(entityService.resolveRegion(query(args))).code();
        if (!TextUtils.hasText(industryCode) && TextUtils.hasText(text(args, "industryName"))) industryCode = unique(entityService.resolveIndustry(query(args))).code();
        return new Scope(TextUtils.hasText(customerCode) ? List.of(customerCode) : List.of(), regionCode, industryCode);
    }

    /**
     * 取得唯一实体候选；没有匹配或存在歧义时中止业务执行，避免智能体自行猜测。
     */
    private EntityCandidate unique(ResolutionResult result) {
        if (!"UNIQUE".equals(result.status())) throw new BusinessException(result.message() + "，请先调用对应实体查询工具确认编码");
        return result.candidates().get(0);
    }

    /** 从通用Map参数构造实体查询对象。 */
    private EntityQuery query(Map<String, Object> args) {
        return new EntityQuery(text(args, "customerCode"), text(args, "customerName"), text(args, "regionCode"), text(args, "regionName"), text(args, "industryCode"),
                text(args, "industryName"), integer(args, "limit"));
    }

    /** 将MCP参数转换为受限的智能体步骤记录命令。 */
    @SuppressWarnings("unchecked")
    private StepCommand stepCommand(Map<String, Object> args) {
        Object raw = args.get("checkpoint");
        Map<String, Object> checkpoint = raw instanceof Map<?, ?> ? objectMapper.convertValue(raw, Map.class) : Map.of();
        return new StepCommand(requiredText(args, "stepCode"), text(args, "stepName"), requiredText(args, "status"), integer(args, "stepOrder"), text(args, "inputSummary"),
                text(args, "outputSummary"), checkpoint, text(args, "errorCode"), text(args, "errorMessage"));
    }

    /**
     * 获取训练或预测批次号。
     * <p>请求中没有显式批次号时，尝试从当前会话记忆中的最近批次号回退获取。</p>
     */
    private String batch(Map<String, Object> args, String key, String memoryKey) {
        String value = text(args, key);
        if (TextUtils.hasText(value)) return value;
        String conversationNo = text(args, "conversationNo");
        if (TextUtils.hasText(conversationNo)) {
            Object remembered = memoryService.get(conversationNo).get(memoryKey);
            if (remembered != null && TextUtils.hasText(String.valueOf(remembered))) return String.valueOf(remembered);
        }
        throw new BusinessException("缺少必填参数" + key);
    }

    /** 创建状态为COMPLETED的标准成功结果。 */
    private McpToolResult success(String toolName, String message, Object data) {
        String status = "COMPLETED";
        if (data instanceof Map<?, ?> map && map.get("status") != null && TextUtils.hasText(String.valueOf(map.get("status"))))
            status = String.valueOf(map.get("status"));
        return McpToolResult.success(status, resultType(toolName), message, data);
    }

    private String resultType(String toolName) {
        if (toolName.equals("memory_get") || toolName.equals("memory_update")) return "MEMORY";
        if (toolName.endsWith("_list") || toolName.endsWith("_history_get") || toolName.equals("task_steps_get")) return "PAGE";
        if (toolName.endsWith("_search")) return "ENTITY";
        if (toolName.equals("business_reference_resolve")) return "OBJECT";
        if (toolName.endsWith("_start")) return "TASK";
        if (toolName.endsWith("_status_get")) return "PROGRESS";
        if (toolName.endsWith("_result_get") || toolName.endsWith("_latest_get")) return "RESULT";
        if (toolName.equals("task_resume") || toolName.equals("task_retry") || toolName.equals("task_step_record")) return "COMMAND";
        return "OBJECT";
    }

    /** 读取必填文本参数，缺失或空白时抛出业务参数异常。 */
    private String requiredText(Map<String, Object> args, String key) {
        String value = text(args, key);
        if (!TextUtils.hasText(value))
            throw new BusinessException("缺少必填参数" + key);
        return value;
    }

    /** 读取可选文本参数并去除首尾空白。 */
    private String text(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    /** 读取可选整数参数；格式错误时返回明确的业务参数异常。 */
    private Integer integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null)
            return null;
        if (value instanceof Number number)
            return number.intValue();
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(key + "必须是整数");
        }
    }

    /** 读取布尔参数；未提供时使用调用方指定的默认值。 */
    private boolean bool(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    /** 文本为空时返回默认值。 */
    private String defaultText(String value, String defaultValue) {
        return TextUtils.hasText(value) ? value : defaultValue;
    }

    /** 根据业务异常文本映射为供智能体判断的稳定错误码。 */
    private String classify(String message) {
        if (message != null && message.contains("确认")) return "CONFIRMATION_REQUIRED";
        if (message != null && (message.contains("缺少") || message.contains("不能为空") || message.contains("必须"))) return "INVALID_ARGUMENT";
        if (message != null && (message.contains("不存在") || message.contains("没有找到"))) return "NOT_FOUND";
        return "BUSINESS_ERROR";
    }

    /**
     * 创建包含具体原因和建议动作的结构化失败结果。
     * data字段用于让大模型可靠判断是补参数、让用户确认、改查实体，还是稍后重试。
     */
    private McpToolResult failure(String toolName, String errorCode, String reason, boolean retryable) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("toolName", toolName);
        detail.put("reason", reason);
        String action = nextAction(errorCode, toolName);
        detail.put("nextAction", action);
        detail.put("allowedActions", allowedActions(action, retryable));
        detail.put("retryable", retryable);
        return McpToolResult.failure("FAILED", errorCode, reason, retryable, detail);
    }

    /** 根据稳定错误码给大模型返回明确的后续处理建议。 */
    private String nextAction(String errorCode, String toolName) {
        return switch (errorCode) {
            case "INVALID_ARGUMENT", "CONFIRMATION_REQUIRED" -> "ASK_USER";
            case "NOT_FOUND" -> toolName.endsWith("_search") || toolName.endsWith("_start")
                    ? "ASK_USER" : "QUERY_STATUS";
            case "TOOL_NOT_FOUND" -> "FAIL";
            case "INTERNAL_ERROR" -> "QUERY_STATUS";
            default -> "MODEL_DECISION";
        };
    }

    /** 限制智能体和大模型只能从服务端允许的动作中选择。 */
    private List<String> allowedActions(String action, boolean retryable) {
        List<String> actions = new java.util.ArrayList<>();
        actions.add(action);
        if (!actions.contains("QUERY_STATUS")) actions.add("QUERY_STATUS");
        if (!actions.contains("ASK_USER")) actions.add("ASK_USER");
        if (retryable) actions.add("RETRY");
        if (!actions.contains("FAIL")) actions.add("FAIL");
        return actions;
    }

    /** 提取异常链最底层的具体原因，避免只返回笼统的包装异常。 */
    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current)
            current = current.getCause();
        String message = current.getMessage();
        if (!TextUtils.hasText(message))
            message = throwable.getMessage();
        return TextUtils.hasText(message) ? message : current.getClass().getSimpleName();
    }

    /** 避免异常消息为空，保证工具调用始终返回可理解的错误信息。 */
    private String safeMessage(Exception exception) {
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "业务执行失败";
    }

    /** 分发器内部使用的实体分类。 */
    private enum EntityType { CUSTOMER, REGION, INDUSTRY }

    /** 已完成名称到编码转换的业务范围。 */
    private record Scope(List<String> customerCodes, String regionCode, String industryCode) {}
}
