package com.gas.forecast.business.application.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.application.task.ExecutionTaskLifecycleService;
import com.gas.forecast.business.application.forecast.ForecastApplicationService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 预测应用入口：确认后只创建统一任务和任务明细，实际执行由异步执行器处理。 */
@Service
@RequiredArgsConstructor
public class ForecastApplicationServiceImpl implements ForecastApplicationService {
    private static final DateTimeFormatter FORECAST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final ExecutionTaskLifecycleService taskLifecycleService;
    private final ObjectMapper objectMapper;
    private final BaseCustomerTbMapper baseCustomerTbMapper;

    @Override
    public Object start(ForecastCommand command) {
        validate(command);
        int targetCount = taskLifecycleService.previewTargetCount(
                command.agentCode(), command.dimensionTypeCode(), command.targets());
        if (targetCount == 0) throw new BusinessException("当前范围内没有可执行目标");
        if (!command.confirmed()) return confirmation(command, targetCount);

        var task = taskLifecycleService.create(command.conversationNo(), command.agentCode(),
                command.dimensionTypeCode(), command.targets(), "模型回测与预测", requestJson(command));
        // forecastNo 是会话分析的稳定锚点，必须在任务创建时就绑定，而不能等异步
        // 执行器启动后才生成。这样助手消息可以立即持久化 FORECAST 引用。
        List<ExecutionTaskDetailTb> details = taskLifecycleService.relations(task.getTaskNo());
        for (ExecutionTaskDetailTb detail : details) {
            if (!TextUtils.hasText(detail.getForecastNo())) {
                String forecastNo = "FORECAST-" + LocalDateTime.now().format(FORECAST_TIME)
                        + "-" + String.format("%06d", detail.getId());
                taskLifecycleService.bindForecastNo(detail.getId(), forecastNo);
            }
        }
        details = taskLifecycleService.relations(task.getTaskNo());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo());
        result.put("total", details.size());
        result.put("status", "PENDING");
        result.put("message", "预测任务已创建，正在等待异步执行。");
        result.put("tasks", details.stream().map(this::detailResult).toList());
        return result;
    }

    /** 返回确认前的完整任务摘要，避免“执行几个目标”这类无法理解的技术表述。 */
    private Map<String, Object> confirmation(ForecastCommand command, int targetCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "NEED_CONFIRMATION");
        result.put("affectedCount", targetCount);
        result.put("message", "本次将创建" + targetCount + "个" + dimensionLabel(command)
                + "燃气预测任务，预测周期为" + forecastPeriod(command)
                + targetDescription(command)
                + "。确认后系统将自动准备数据、执行模型计算并生成预测结果。");
        result.put("proposedParameters", Map.of("confirmed", true));
        return result;
    }

    /** 确认前明确告诉用户系统将对哪些客户执行，避免只有数量而无法核对对象。 */
    private String targetDescription(ForecastCommand command) {
        if (command.dimensionType() != com.gas.forecast.business.enums.ExecutionDimensionTypeEnum.CUSTOMER) return "";
        Set<String> customerCodes = command.targets().stream()
                .filter(target -> target.customerCodes() != null)
                .flatMap(target -> target.customerCodes().stream())
                .filter(TextUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (customerCodes.isEmpty()) return "，预测对象为当前选择范围内的客户";
        List<BaseCustomerTb> customers = baseCustomerTbMapper.selectList(
                Wrappers.<BaseCustomerTb>lambdaQuery().in(BaseCustomerTb::getCustomerCode, customerCodes));
        List<String> labels = customers.stream().limit(10)
                .map(item -> item.getRegionName() + " / " + item.getIndustryName() + " / " + item.getCustomerName())
                .toList();
        if (labels.isEmpty()) return "";
        String suffix = customers.size() > labels.size() ? " 等" + customers.size() + "个客户" : "";
        return "，预测对象：" + String.join("；", labels) + suffix;
    }

    private String dimensionLabel(ForecastCommand command) {
        if (command.dimensionType() == null) return "";
        return switch (command.dimensionType()) {
            case CUSTOMER -> "客户";
            case REGION -> "地区";
            case INDUSTRY -> "行业";
        };
    }

    private String forecastPeriod(ForecastCommand command) {
        ForecastApplicationService.ForecastOptions forecast = command.forecast();
        if (forecast == null || forecast.forecastHorizon() == null) return "未指定";
        String unit = switch (String.valueOf(forecast.forecastUnit()).toUpperCase()) {
            case "MONTH" -> "个月";
            case "TENDAY" -> "旬";
            default -> "天";
        };
        return forecast.forecastHorizon() + unit;
    }

    private Map<String, Object> detailResult(ExecutionTaskDetailTb detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detailNo", detail.getDetailNo());
        result.put("status", detail.getStatus());
        result.put("dimensionType", detail.getDimensionType());
        result.put("regionCode", detail.getRegionCode());
        result.put("industryCode", detail.getIndustryCode());
        result.put("customerCode", detail.getCustomerCode());
        result.put("forecastNo", detail.getForecastNo());
        return result;
    }

    private String requestJson(ForecastCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception exception) {
            throw new BusinessException("预测任务参数无法序列化");
        }
    }

    private void validate(ForecastCommand command) {
        if (command == null || !TextUtils.hasText(command.agentCode())) throw new BusinessException("agentCode不能为空");
        if (command.dimensionType() == null) throw new BusinessException("dimensionType不能为空");
        if (command.targets() == null || command.targets().isEmpty()) throw new BusinessException("targets不能为空");
        for (ExecutionTarget target : command.targets()) {
            if (target == null) throw new BusinessException("targets不能包含空对象");
        }
    }
}
