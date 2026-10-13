package com.gas.forecast.business.component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.ModelTrainExecuteRequest;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.service.ModelTrainExecutionService;
import com.gas.forecast.dao.domain.AgentStrategyConfigTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AgentStrategyConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 直接以训练批次为队列项进行领取、限流、重试与平台状态核对。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gas.agent.training-queue", name = "scheduler-enabled", havingValue = "true")
public class ModelTrainQueueDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelTrainQueueDispatcher.class);
    private static final String WAITING = "WAITING";
    private static final String CLAIMED = "CLAIMED";
    private static final String PREPARING = "PREPARING";
    private static final String RUNNING = "RUNNING";
    private static final String FAILED_FINAL = "FAILED_FINAL";

    private final ModelTrainRecordTbMapper recordMapper;
    private final AgentStrategyConfigTbMapper strategyMapper;
    private final ModelTrainExecutionService executionService;
    private final ModelPlatformClient modelPlatformClient;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    @Value("${gas.agent.training-queue.max-concurrency:4}")
    private int maxConcurrency;
    @Value("${gas.agent.training-queue.reconcile-enabled:true}")
    private boolean reconcileEnabled;

    @Scheduled(fixedDelayString = "${gas.agent.training-queue.dispatch-delay-ms:2000}")
    public void dispatch() {
        long active = recordMapper.selectCount(Wrappers.<ModelTrainRecordTb>lambdaQuery().in(ModelTrainRecordTb::getStatus, CLAIMED, PREPARING, RUNNING));
        int localCapacity = Math.max(0, maxConcurrency - (int) active);
        if (localCapacity == 0)
            return;
        int platformCapacity;
        try {
            platformCapacity = modelPlatformClient.availableTrainingSlots();
        } catch (RuntimeException exception) {
            LOGGER.warn("模型平台容量查询失败，本轮暂停领取训练批次: {}", exception.getMessage());
            return;
        }
        int capacity = Math.min(localCapacity, platformCapacity);
        if (capacity <= 0)
            return;
        Date now = new Date();
        List<ModelTrainRecordTb> candidates = recordMapper.selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getStatus, WAITING)
                .and(q -> q.isNull(ModelTrainRecordTb::getNextRetryTime).or().le(ModelTrainRecordTb::getNextRetryTime, now)).orderByDesc(ModelTrainRecordTb::getPriority)
                .orderByAsc(ModelTrainRecordTb::getId).last("limit " + capacity));
        for (ModelTrainRecordTb record : candidates) {
            if (claim(record.getId()) == 0)
                continue;
            submit(recordMapper.selectById(record.getId()));
        }
    }

    private int claim(Long id) {
        Date now = new Date();
        return recordMapper.update(null, Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, id).eq(ModelTrainRecordTb::getStatus, WAITING)
                .set(ModelTrainRecordTb::getStatus, CLAIMED).set(ModelTrainRecordTb::getClaimedBy, workerId).set(ModelTrainRecordTb::getClaimedAt, now).set(ModelTrainRecordTb::getUpdatedAt, now));
    }

    private void submit(ModelTrainRecordTb record) {
        try {
            executionService.execute(new ModelTrainExecuteRequest(record.getTrainConfigId(), record.getBatchNo(), null));
            Date now = new Date();
            recordMapper.update(null,
                    Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, record.getId()).eq(ModelTrainRecordTb::getStatus, CLAIMED).set(ModelTrainRecordTb::getStatus, PREPARING)
                            .set(ModelTrainRecordTb::getStartedAt, now).set(ModelTrainRecordTb::getErrorMessage, null).set(ModelTrainRecordTb::getUpdatedAt, now));
        } catch (RuntimeException exception) {
            LOGGER.error("提交训练批次失败 batchNo={}", record.getBatchNo(), exception);
            retryOrFail(record, exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${gas.agent.training-queue.reconcile-delay-ms:3000}")
    public void reconcile() {
        if (!reconcileEnabled)
            return;
        List<ModelTrainRecordTb> failed = recordMapper
                .selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.FAILED.getCode()).orderByAsc(ModelTrainRecordTb::getId).last("limit 100"));
        for (ModelTrainRecordTb record : failed)
            retryOrFail(record, record.getErrorMessage());

        List<ModelTrainRecordTb> running = recordMapper
                .selectList(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getStatus, RUNNING).orderByAsc(ModelTrainRecordTb::getId).last("limit 100"));
        for (ModelTrainRecordTb record : running) {
            if (!platformCheckDue(record))
                continue;
            try {
                if (modelPlatformClient.isTrainingRunning(record.getBatchNo())) {
                    recordMapper.update(null, Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, record.getId()).eq(ModelTrainRecordTb::getStatus, RUNNING)
                            .set(ModelTrainRecordTb::getUpdatedAt, new Date()));
                } else {
                    retryOrFail(record, "模型平台未找到运行中的训练批次，可能已重启或训练进程已丢失");
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("核对模型平台训练批次失败 batchNo={}, error={}", record.getBatchNo(), exception.getMessage());
            }
        }
    }

    private boolean platformCheckDue(ModelTrainRecordTb record) {
        Date reference = record.getUpdatedAt() == null ? record.getCreatedAt() : record.getUpdatedAt();
        return reference != null && System.currentTimeMillis() - reference.getTime() >= 60_000L;
    }

    private void retryOrFail(ModelTrainRecordTb record, String errorMessage) {
        int retryCount = record.getRetryCount() == null ? 0 : record.getRetryCount();
        int maxRetry = record.getMaxRetryCount() == null ? 3 : record.getMaxRetryCount();
        Date now = new Date();
        if (retryCount < maxRetry) {
            Date next = new Date(now.getTime() + retryInterval(record.getAgentCode()) * 60_000L);
            recordMapper.update(null,
                    Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, record.getId()).notIn(ModelTrainRecordTb::getStatus, "SUCCESS", FAILED_FINAL)
                            .set(ModelTrainRecordTb::getStatus, WAITING).set(ModelTrainRecordTb::getRetryCount, retryCount + 1).set(ModelTrainRecordTb::getNextRetryTime, next)
                            .set(ModelTrainRecordTb::getClaimedBy, null).set(ModelTrainRecordTb::getClaimedAt, null).set(ModelTrainRecordTb::getErrorMessage, abbreviate(errorMessage))
                            .set(ModelTrainRecordTb::getUpdatedAt, now));
        } else {
            recordMapper.update(null,
                    Wrappers.<ModelTrainRecordTb>lambdaUpdate().eq(ModelTrainRecordTb::getId, record.getId()).set(ModelTrainRecordTb::getStatus, FAILED_FINAL)
                            .set(ModelTrainRecordTb::getActiveDedupKey, null).set(ModelTrainRecordTb::getErrorMessage, abbreviate(errorMessage)).set(ModelTrainRecordTb::getFinishedAt, now)
                            .set(ModelTrainRecordTb::getUpdatedAt, now));
        }
    }

    private int retryInterval(String agentCode) {
        AgentStrategyConfigTb strategy = strategyMapper.selectOne(Wrappers.<AgentStrategyConfigTb>lambdaQuery().eq(AgentStrategyConfigTb::getAgentCode, agentCode).last("limit 1"));
        return strategy == null || strategy.getRetryIntervalMinutes() == null ? 30 : strategy.getRetryIntervalMinutes();
    }

    private String abbreviate(String message) {
        return message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
