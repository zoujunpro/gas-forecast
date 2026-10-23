package com.gas.forecast.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.enums.ExecutionTargetStatus;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.ExecutionTaskDetailTb;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 训练完成后续跑同一任务下等待预测的最小执行目标。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskForecastContinuationService {
    private final ExecutionTaskLifecycleService taskLifecycleService;
    private final ModelForecastConfigTbMapper modelForecastConfigTbMapper;
    private final ModelForecastManagementService forecastService;

    public void continueAfterTraining(String trainBatchNo) {
        if (!TextUtils.hasText(trainBatchNo)) return;
        List<ExecutionTaskDetailTb> relations = taskLifecycleService.relationsByTrainBatch(trainBatchNo);
        for (ExecutionTaskDetailTb relation : relations) {
            if (!ExecutionTargetStatus.WAITING_FORECAST.getCode().equals(relation.getStatus())
                    || TextUtils.hasText(relation.getForecastBatchNo())) continue;
            try {
                ModelForecastConfigTb config = config(relation);
                if (config == null) throw new IllegalStateException("未找到训练目标对应的预测配置");
                ModelForecastExecuteRequest request = new ModelForecastExecuteRequest();
                request.setForecastId(config.getId());
                request.setForecastStartDate(config.getForecastStartDate());
                request.setForecastHorizon(config.getForecastHorizon());
                request.setForecastFrequency(config.getForecastFrequency());
                var response = forecastService.submit(request);
                taskLifecycleService.updateBatch(relation.getId(), null, response.getForecastBatchNo(),
                        ExecutionTargetStatus.FORECASTING);
                log.info("训练成功后续跑预测 taskNo={}, trainBatchNo={}, forecastBatchNo={}",
                        relation.getTaskNo(), trainBatchNo, response.getForecastBatchNo());
            } catch (RuntimeException exception) {
                taskLifecycleService.updateBatch(relation.getId(), null, null, ExecutionTargetStatus.FAILED);
                log.error("训练成功后续跑预测失败 taskNo={}, trainBatchNo={}", relation.getTaskNo(), trainBatchNo, exception);
            }
        }
    }

    private ModelForecastConfigTb config(ExecutionTaskDetailTb relation) {
        var query = Wrappers.<ModelForecastConfigTb>lambdaQuery()
                .eq(ModelForecastConfigTb::getAgentCode, relation.getAgentCode())
                .eq(ModelForecastConfigTb::getEnabled, 1);
        scope(query, ModelForecastConfigTb::getRegionCode, relation.getRegionCode());
        scope(query, ModelForecastConfigTb::getIndustryCode, relation.getIndustryCode());
        scope(query, ModelForecastConfigTb::getCustomerCode, relation.getCustomerCode());
        return modelForecastConfigTbMapper.selectOne(query.orderByDesc(ModelForecastConfigTb::getUpdatedAt).last("limit 1"));
    }

    private <T> void scope(LambdaQueryWrapper<ModelForecastConfigTb> query, SFunction<ModelForecastConfigTb, T> column, String value) {
        if (TextUtils.hasText(value)) query.eq(column, value);
        else query.and(item -> item.isNull(column).or().eq(column, ""));
    }
}
