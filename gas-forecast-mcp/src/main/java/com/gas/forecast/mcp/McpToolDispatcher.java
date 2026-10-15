package com.gas.forecast.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.service.AgentMemoryService;
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
                case "memory_get" -> success("记忆查询成功", memoryService.get(requiredText(args, "conversationNo")));
                case "memory_update" -> memoryUpdate(args);
                case "customer_search" -> resolve(args, EntityType.CUSTOMER);
                case "region_search" -> resolve(args, EntityType.REGION);
                case "industry_search" -> resolve(args, EntityType.INDUSTRY);
                case "training_start" -> success("训练任务已受理", trainingService.start(trainingCommand(args)));
                case "training_status_get" -> success("训练进度查询成功", trainingService.status(batch(args, "trainBatchNo", "lastTrainBatchNo")));
                case "training_result_get" -> success("训练结果查询成功", trainingService.result(batch(args, "trainBatchNo", "lastTrainBatchNo")));
                case "forecast_start" -> success("预测任务已受理", forecastService.start(forecastCommand(args)));
                case "forecast_status_get" -> success("预测进度查询成功", forecastService.status(batch(args, "forecastBatchNo", "lastForecastBatchNo")));
                case "forecast_result_get" -> success("预测结果查询成功", forecastService.result(batch(args, "forecastBatchNo", "lastForecastBatchNo"), bool(args, "includePoints", true)));
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
        return success("记忆更新成功", memoryService.update(requiredText(args, "conversationNo"), text(args, "operation"), values));
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
        return McpToolResult.success("UNIQUE".equals(result.status()) ? "COMPLETED" : "NEED_INFORMATION", result.message(), result);
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
        return new ForecastCommand(defaultText(text(args, "agentCode"), "short-term"), scope.customerCodes(), scope.regionCode(), scope.industryCode(), bool(args, "bulk", false),
                text(args, "forecastStartDate"), text(args, "forecastEndDate"), integer(args, "forecastHorizon"), text(args, "forecastFrequency"), text(args, "trainMode"), integer(args, "trainingPeriods"),
                text(args, "trainingStartDate"), text(args, "trainingEndDate"), bool(args, "forceRetrain", false), bool(args, "autoTrain", true), bool(args, "confirmed", false),
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
    private McpToolResult success(String message, Object data) {
        return McpToolResult.success("COMPLETED", message, data);
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
        detail.put("nextAction", nextAction(errorCode, toolName));
        detail.put("retryable", retryable);
        return McpToolResult.failure("FAILED", errorCode, reason, retryable, detail);
    }

    /** 根据稳定错误码给大模型返回明确的后续处理建议。 */
    private String nextAction(String errorCode, String toolName) {
        return switch (errorCode) {
            case "INVALID_ARGUMENT" -> "检查工具参数定义，补充或修正参数后重新调用";
            case "CONFIRMATION_REQUIRED" -> "向用户说明批量操作范围和影响，取得明确确认后设置confirmed=true重新调用";
            case "NOT_FOUND" -> toolName.endsWith("_search") || toolName.endsWith("_start")
                    ? "检查实体名称或缩小查询范围，调用实体查询工具确认标准业务编码和数据是否存在"
                    : "核对批次号；未提供批次号时先查询会话记忆中的最近批次";
            case "TOOL_NOT_FOUND" -> "重新调用tools/list，并使用目录中公布的工具名称";
            case "INTERNAL_ERROR" -> "不要直接断言任务失败；若已经返回过批次号，先查询任务状态，否则稍后重试一次";
            default -> "根据reason修正业务条件；如果需要改变业务范围或强制重训，应先向用户确认";
        };
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
