package com.gas.forecast.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelForecastExecuteRequest;
import com.gas.forecast.business.service.ModelForecastManagementService;
import com.gas.forecast.dao.domain.ModelForecastConfigTb;
import com.gas.forecast.dao.domain.ModelForecastRecordTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.ModelForecastConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelForecastRecordTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import com.gas.forecast.common.util.TextUtils;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** AI发起的训练成功后，由后端继续预测；不依赖浏览器轮询是否还在线。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiTrainForecastContinuationScheduler {
    private final ModelTrainRecordTbMapper trainRecordMapper;
    private final ModelTrainConfigTbMapper trainConfigMapper;
    private final ModelForecastConfigTbMapper forecastConfigMapper;
    private final ModelForecastRecordTbMapper forecastRecordMapper;
    private final ModelForecastManagementService forecastService;
    private final Set<String> submitting = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${forecast.ai.train-continuation-interval-ms:5000}")
    public void continueReadyTraining() {
        List<ModelTrainRecordTb> records = trainRecordMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTriggerSource, "AI")
                .eq(ModelTrainRecordTb::getStatus, "SUCCESS").orderByDesc(ModelTrainRecordTb::getId).last("limit 200"));
        for (ModelTrainRecordTb train : records) {
            if (!submitting.add(train.getBatchNo()))
                continue;
            try {
                continueOne(train);
            } catch (RuntimeException exception) {
                log.error("AI训练成功后自动预测提交失败, trainBatchNo={}", train.getBatchNo(), exception);
            } finally {
                submitting.remove(train.getBatchNo());
            }
        }
    }

    private void continueOne(ModelTrainRecordTb train) {
        ModelTrainConfigTb trainConfig = trainConfigMapper.selectById(train.getTrainConfigId());
        if (trainConfig == null)
            return;
        if (!TextUtils.hasText(trainConfig.getRegionCode()) || !TextUtils.hasText(trainConfig.getIndustryCode())
                || !TextUtils.hasText(trainConfig.getCustomerCode()) || "ALL".equalsIgnoreCase(trainConfig.getCustomerCode())) {
            log.warn("跳过非客户级AI训练的自动预测: trainBatchNo={}, trainConfigId={}", train.getBatchNo(), trainConfig.getId());
            return;
        }
        List<ModelForecastConfigTb> configs = forecastConfigMapper.selectList(Wrappers.<ModelForecastConfigTb>lambdaQuery().eq(ModelForecastConfigTb::getTrainConfigId, trainConfig.getId())
                .eq(ModelForecastConfigTb::getRegionCode, trainConfig.getRegionCode()).eq(ModelForecastConfigTb::getIndustryCode, trainConfig.getIndustryCode())
                .eq(ModelForecastConfigTb::getCustomerCode, trainConfig.getCustomerCode()).eq(ModelForecastConfigTb::getEnabled, 1).orderByAsc(ModelForecastConfigTb::getId));
        for (ModelForecastConfigTb config : configs) {
            Long count = forecastRecordMapper.selectCount(Wrappers.<ModelForecastRecordTb>lambdaQuery().eq(ModelForecastRecordTb::getForecastId, config.getId()).ge(train.getFinishedAt() != null,
                    ModelForecastRecordTb::getCreatedAt, train.getFinishedAt()));
            if (count != null && count > 0)
                continue;
            ModelForecastExecuteRequest execute = new ModelForecastExecuteRequest();
            execute.setForecastId(config.getId());
            forecastService.submit(execute);
            log.info("AI客户训练成功后已自动提交客户预测: trainBatchNo={}, customer={}, forecastId={}", train.getBatchNo(), config.getCustomerName(), config.getId());
        }
    }
}
