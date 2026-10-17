package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.ModelTrainQueueService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentStrategyConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AgentStrategyConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 以训练批次作为唯一调度任务，避免任务表和批次表一对一重复。 */
@Service
@RequiredArgsConstructor
public class ModelTrainQueueServiceImpl implements ModelTrainQueueService {
    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ModelTrainRecordTbMapper recordMapper;
    private final AgentStrategyConfigTbMapper strategyMapper;
    private final BaseCodeGenerateService codeGenerateService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ModelTrainRecordTb enqueue(ModelTrainConfigTb config, String triggerSource, boolean forceTrain, String retryBatchNo) {
        Date now = new Date();
        if (TextUtils.hasText(retryBatchNo)) {
            ModelTrainRecordTb record = recordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getBatchNo, retryBatchNo.trim()).last("limit 1"));
            if (record == null)
                throw new BusinessException("训练批次不存在：" + retryBatchNo);
            if (isActive(record.getStatus()))
                throw new BusinessException("该训练批次正在执行，请勿重复发起");
            recordMapper.update(null,
                    Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, record.getId()).set(ModelTrainRecordTb::getStatus, "WAITING")
                            .set(ModelTrainRecordTb::getTriggerSource, triggerSource).set(ModelTrainRecordTb::getPriority, priority(triggerSource)).set(ModelTrainRecordTb::getNextRetryTime, now)
                            .set(ModelTrainRecordTb::getClaimedBy, null).set(ModelTrainRecordTb::getClaimedAt, null).set(ModelTrainRecordTb::getFinishedAt, null)
                            .set(ModelTrainRecordTb::getErrorMessage, null).set(ModelTrainRecordTb::getUpdatedAt, now));
            return recordMapper.selectById(record.getId());
        }

        String dedupKey = dedupKey(config);
        if (!forceTrain) {
            ModelTrainRecordTb active = recordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getActiveDedupKey, dedupKey).last("limit 1"));
            if (active != null)
                return active;
        }

        AgentStrategyConfigTb strategy = strategyMapper.selectOne(Wrappers.<AgentStrategyConfigTb>lambdaQuery().eq(AgentStrategyConfigTb::getAgentCode, config.getAgentCode()).last("limit 1"));
        ModelTrainRecordTb record = new ModelTrainRecordTb();
        record.setTrainConfigId(config.getId());
        record.setBatchNo(generateBatchNo());
        record.setAgentCode(value(config.getAgentCode(), "unknown"));
        record.setRegionCode(value(config.getRegionCode(), "ALL"));
        record.setRegionName(value(config.getRegionName(), "全部区域"));
        record.setIndustryCode(value(config.getIndustryCode(), "ALL"));
        record.setIndustryName(value(config.getIndustryName(), "全部行业"));
        record.setCustomerCode(value(config.getCustomerCode(), "ALL"));
        record.setCustomerName(value(config.getCustomerName(), "全部客户"));
        record.setTrainStartDate(value(config.getTrainStartDate(), ""));
        record.setTrainEndDate(value(config.getTrainEndDate(), ""));
        record.setStatus("WAITING");
        record.setActiveDedupKey(forceTrain ? null : dedupKey);
        record.setTriggerSource(triggerSource);
        record.setPriority(priority(triggerSource));
        record.setRetryCount(0);
        record.setMaxRetryCount(strategy == null || strategy.getMaxRetryCount() == null ? 3 : strategy.getMaxRetryCount());
        record.setNextRetryTime(now);
        record.setResultJson(objectMapper.createObjectNode().put("message", "训练批次已创建，等待调度执行。").toString());
        record.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        record.setCreatedByName(SecurityContextHolder.getUserName());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        recordMapper.insert(record);
        return record;
    }

    private boolean isActive(String status) {
        return "WAITING".equals(status) || "CLAIMED".equals(status) || "PREPARING".equals(status) || "RUNNING".equals(status);
    }

    private String dedupKey(ModelTrainConfigTb config) {
        return String.join("|", value(config.getAgentCode(), ""), value(config.getModelId(), ""), value(config.getRegionCode(), ""), value(config.getIndustryCode(), ""),
                value(config.getCustomerCode(), ""), value(config.getTrainMode(), ""), value(config.getTrainStartDate(), ""), value(config.getTrainEndDate(), ""),
                value(config.getRecentPeriods(), ""));
    }

    private String generateBatchNo() {
        String sequence = codeGenerateService.nextCode(BaseCodeType.TRAIN_BATCH);
        String number = sequence.substring(BaseCodeType.TRAIN_BATCH.prefix().length());
        return BaseCodeType.TRAIN_BATCH.prefix() + LocalDate.now().format(BATCH_DATE) + "-" + number;
    }

    private int priority(String source) {
        return switch (source) {
            case "MANUAL" -> 100;
            case "AI" -> 90;
            case "MCP" -> 90;
            case "DATA_EVENT" -> 70;
            default -> 50;
        };
    }

    private String value(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
