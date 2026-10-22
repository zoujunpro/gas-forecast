package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
import com.gas.forecast.business.dto.response.ModelTrainResultResponse;
import com.gas.forecast.business.service.AutoTrainService;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.business.service.TrainingApplicationService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.BaseCustomerTb;
import com.gas.forecast.dao.mapper.BaseCustomerTbMapper;
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
    private final BaseCustomerTbMapper customerMapper;

    @Override
    public Object start(TrainingCommand command) {
        validate(command);
        List<String> customers = customerCodes(command);
        if (customers.isEmpty())
            throw new BusinessException("当前业务范围内没有找到可训练客户");
        List<Object> tasks = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        for (String customerCode : customers) {
            try {
                AutoTrainRequest request = new AutoTrainRequest();
                request.setAgentCode(command.agentCode());
                request.setRegionCode(singleScope(command.regionCodes(), command.regionCode()));
                request.setIndustryCode(singleScope(command.industryCodes(), command.industryCode()));
                request.setCustomerCode(customerCode);
                request.setTrainMode(command.trainMode());
                request.setRecentPeriods(command.trainingPeriods());
                request.setTrainStartDate(command.trainingStartDate());
                request.setTrainEndDate(command.trainingEndDate());
                request.setForceTrain(command.forceRetrain());
                request.setTriggerSource("MCP");
                tasks.add(autoTrainService.execute(request));
            } catch (RuntimeException exception) {
                failures.add(Map.of("customerCode", customerCode, "message", safeMessage(exception)));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", customers.size());
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
        if (command.bulk() && !command.confirmed())
            throw new BusinessException("批量训练必须先获得用户确认");
    }

    private List<String> customerCodes(TrainingCommand command) {
        if (command.customerCodes() != null && !command.customerCodes().isEmpty())
            return command.customerCodes().stream().filter(TextUtils::hasText).distinct().toList();
        var query = Wrappers.<BaseCustomerTb>lambdaQuery();
        if (command.regionCodes() != null && !command.regionCodes().isEmpty())
            query.in(BaseCustomerTb::getRegionCode, command.regionCodes());
        else if (TextUtils.hasText(command.regionCode()))
            query.eq(BaseCustomerTb::getRegionCode, command.regionCode());
        if (command.industryCodes() != null && !command.industryCodes().isEmpty())
            query.in(BaseCustomerTb::getIndustryCode, command.industryCodes());
        else if (TextUtils.hasText(command.industryCode()))
            query.eq(BaseCustomerTb::getIndustryCode, command.industryCode());
        if (!command.bulk())
            throw new BusinessException("单客户训练必须提供customerCodes");
        return customerMapper.selectList(query.orderByAsc(BaseCustomerTb::getCustomerName)).stream().map(BaseCustomerTb::getCustomerCode).distinct().toList();
    }

    private String singleScope(List<String> values, String fallback) {
        if (values == null || values.isEmpty())
            return fallback;
        return values.size() == 1 ? values.get(0) : null;
    }

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
