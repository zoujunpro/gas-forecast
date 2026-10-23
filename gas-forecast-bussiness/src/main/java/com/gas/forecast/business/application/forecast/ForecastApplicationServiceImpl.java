package com.gas.forecast.business.application.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.dto.request.ExecutionTarget;
import com.gas.forecast.business.application.task.ExecutionTaskLifecycleService;
import com.gas.forecast.business.application.forecast.ForecastApplicationService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 预测应用入口：确认后只创建统一任务和任务明细，实际执行由异步执行器处理。 */
@Service
@RequiredArgsConstructor
public class ForecastApplicationServiceImpl implements ForecastApplicationService {
    private final ExecutionTaskLifecycleService taskLifecycleService;
    private final ObjectMapper objectMapper;

    @Override
    public Object start(ForecastCommand command) {
        validate(command);
        int targetCount = taskLifecycleService.previewTargetCount(
                command.agentCode(), command.dimensionTypeCode(), command.targets());
        if (targetCount == 0) throw new BusinessException("当前范围内没有可执行目标");
        if (!command.confirmed()) return confirmation(targetCount);

        var task = taskLifecycleService.create(command.conversationNo(), command.agentCode(),
                command.dimensionTypeCode(), command.targets(), "模型回测与预测", requestJson(command));
        List<ExecutionTaskDetailTb> details = taskLifecycleService.relations(task.getTaskNo());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskNo", task.getTaskNo());
        result.put("total", details.size());
        result.put("status", "PENDING");
        result.put("message", "预测任务已创建，正在等待异步执行。");
        result.put("tasks", details.stream().map(this::detailResult).toList());
        return result;
    }

    private Map<String, Object> confirmation(int targetCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "NEED_CONFIRMATION");
        result.put("affectedCount", targetCount);
        result.put("message", "本次预测将执行" + targetCount + "个目标，确认后开始。");
        result.put("proposedParameters", Map.of("confirmed", true));
        return result;
    }

    private Map<String, Object> detailResult(ExecutionTaskDetailTb detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detailNo", detail.getDetailNo());
        result.put("status", detail.getStatus());
        result.put("dimensionType", detail.getDimensionType());
        result.put("regionCode", detail.getRegionCode());
        result.put("industryCode", detail.getIndustryCode());
        result.put("customerCode", detail.getCustomerCode());
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
