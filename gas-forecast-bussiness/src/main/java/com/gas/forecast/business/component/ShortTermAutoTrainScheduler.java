package com.gas.forecast.business.component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.enums.ModelTrainStatus;
import com.gas.forecast.business.enums.ModelTrainMode;
import com.gas.forecast.business.service.ModelTrainQueueService;
import com.gas.forecast.common.enums.ForecastAgentEnum;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.AgentStrategyConfigTb;
import com.gas.forecast.dao.domain.ModelTrainConfigTb;
import com.gas.forecast.dao.domain.DataDailySalesTb;
import com.gas.forecast.dao.domain.ModelTrainRecordTb;
import com.gas.forecast.dao.mapper.AgentStrategyConfigTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainConfigTbMapper;
import com.gas.forecast.dao.mapper.DataDailySalesTbMapper;
import com.gas.forecast.dao.mapper.ModelTrainRecordTbMapper;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/** 按智能体策略巡检短期客户最新数据，并将需要训练的配置加入统一队列。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gas.agent.short-term-auto-train", name = "enabled", havingValue = "true")
public class ShortTermAutoTrainScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortTermAutoTrainScheduler.class);
    private static final String AGENT_CODE = ForecastAgentEnum.SHORT_CUSTOMER.getAgentCode();

    private final AgentStrategyConfigTbMapper strategyMapper;
    private final ModelTrainConfigTbMapper configMapper;
    private final DataDailySalesTbMapper dailySalesMapper;
    private final ModelTrainRecordTbMapper recordMapper;
    private final ModelTrainQueueService queueService;
    private final AtomicReference<ZonedDateTime> lastTriggeredAt = new AtomicReference<>();

    /**
     * 这里只负责轻量巡检。真正的执行时间来自 agent_strategy_config_tb.check_cron， fixedDelay
     * 不等于训练周期，也不会在每次调用时创建任务。
     */
    @Scheduled(fixedDelayString = "${gas.agent.short-term-auto-train.check-delay-ms:30000}")
    public void inspect() {
        AgentStrategyConfigTb strategy = strategyMapper.selectOne(Wrappers.<AgentStrategyConfigTb>lambdaQuery().eq(AgentStrategyConfigTb::getAgentCode, AGENT_CODE).last("limit 1"));
        if (!enabled(strategy) || !cronDue(strategy.getCheckCron()))
            return;

        List<ModelTrainConfigTb> configs = configMapper.selectList(Wrappers.<ModelTrainConfigTb>lambdaQuery().eq(ModelTrainConfigTb::getAgentCode, AGENT_CODE).eq(ModelTrainConfigTb::getEnabled, 1)
                .isNotNull(ModelTrainConfigTb::getCustomerCode).orderByAsc(ModelTrainConfigTb::getId));
        int queued = 0;
        int noData = 0;
        int noChange = 0;
        for (ModelTrainConfigTb config : configs) {
            DataDailySalesTb latest = latestData(config);
            if (latest == null) {
                noData++;
                continue;
            }
            if (alreadyTrained(config.getId(), latest.getStatDate())) {
                noChange++;
                continue;
            }
            queueService.enqueue(config, "SCHEDULE", false, null);
            queued++;
        }
        LOGGER.info("短期客户自动训练巡检完成 configs={}, queued={}, noData={}, noChange={}", configs.size(), queued, noData, noChange);
    }

    private boolean enabled(AgentStrategyConfigTb strategy) {
        return strategy != null && Integer.valueOf(1).equals(strategy.getEnabled()) && Integer.valueOf(1).equals(strategy.getAutoTrainEnabled()) && TextUtils.hasText(strategy.getCheckCron());
    }

    private boolean cronDue(String cronText) {
        try {
            CronExpression cron = CronExpression.parse(cronText);
            ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ZonedDateTime scheduled = cron.next(now.minusSeconds(70));
            if (scheduled == null || scheduled.isAfter(now))
                return false;
            ZonedDateTime previous = lastTriggeredAt.get();
            if (previous != null && !scheduled.isAfter(previous))
                return false;
            return lastTriggeredAt.compareAndSet(previous, scheduled);
        } catch (IllegalArgumentException exception) {
            LOGGER.error("短期客户自动训练 Cron 不合法 cron={}", cronText, exception);
            return false;
        }
    }

    private DataDailySalesTb latestData(ModelTrainConfigTb config) {
        var query = Wrappers.<DataDailySalesTb>lambdaQuery().eq(DataDailySalesTb::getCustomerCode, config.getCustomerCode());
        if (TextUtils.hasText(config.getRegionCode()))
            query.eq(DataDailySalesTb::getRegionCode, config.getRegionCode());
        if (TextUtils.hasText(config.getIndustryCode()))
            query.eq(DataDailySalesTb::getIndustryCode, config.getIndustryCode());
        if (ModelTrainMode.RANGE.name().equals(config.getTrainMode())) {
            if (TextUtils.hasText(config.getTrainStartDate()))
                query.ge(DataDailySalesTb::getStatDate, config.getTrainStartDate());
            if (TextUtils.hasText(config.getTrainEndDate()))
                query.le(DataDailySalesTb::getStatDate, config.getTrainEndDate());
        }
        return dailySalesMapper.selectOne(query.orderByDesc(DataDailySalesTb::getStatDate).orderByDesc(DataDailySalesTb::getId).last("limit 1"));
    }

    private boolean alreadyTrained(Long configId, String latestDataDate) {
        ModelTrainRecordTb latestSuccess = recordMapper.selectOne(Wrappers.<ModelTrainRecordTb>lambdaQuery().eq(ModelTrainRecordTb::getTrainConfigId, configId)
                .eq(ModelTrainRecordTb::getStatus, ModelTrainStatus.SUCCESS.getCode()).orderByDesc(ModelTrainRecordTb::getTrainEndDate).orderByDesc(ModelTrainRecordTb::getId).last("limit 1"));
        return latestSuccess != null && TextUtils.hasText(latestSuccess.getTrainEndDate()) && latestSuccess.getTrainEndDate().compareTo(latestDataDate) >= 0;
    }
}
