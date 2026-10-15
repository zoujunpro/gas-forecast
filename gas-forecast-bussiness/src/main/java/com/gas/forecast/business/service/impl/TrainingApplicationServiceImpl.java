package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.AutoTrainRequest;
import com.gas.forecast.business.dto.request.ModelTrainResultRequest;
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
                request.setRegionCode(command.regionCode());
                request.setIndustryCode(command.industryCode());
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
        result.put("status", tasks.isEmpty() ? "FAILED" : "EXECUTING");
        return result;
    }

    @Override
    public Object status(String trainBatchNo) {
        requireBatch(trainBatchNo);
        ModelTrainResultRequest request = new ModelTrainResultRequest();
        request.setBatchNo(trainBatchNo);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trainBatchNo", trainBatchNo);
        result.put("result", trainExecutionService.getTrainResult(request));
        result.put("steps", trainExecutionService.getExecutionSteps(trainBatchNo));
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
        if (TextUtils.hasText(command.regionCode()))
            query.eq(BaseCustomerTb::getRegionCode, command.regionCode());
        if (TextUtils.hasText(command.industryCode()))
            query.eq(BaseCustomerTb::getIndustryCode, command.industryCode());
        if (!command.bulk())
            throw new BusinessException("单客户训练必须提供customerCodes");
        return customerMapper.selectList(query.orderByAsc(BaseCustomerTb::getCustomerName)).stream().map(BaseCustomerTb::getCustomerCode).distinct().toList();
    }

    private void requireBatch(String batchNo) {
        if (!TextUtils.hasText(batchNo))
            throw new BusinessException("训练批次号不能为空");
    }

    private String safeMessage(RuntimeException exception) {
        return TextUtils.hasText(exception.getMessage()) ? exception.getMessage() : "创建训练任务失败";
    }
}
