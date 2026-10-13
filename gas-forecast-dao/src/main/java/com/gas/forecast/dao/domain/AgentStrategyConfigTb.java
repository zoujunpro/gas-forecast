package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 智能体自动训练与预测策略。 */
@Data
@TableName("agent_strategy_config_tb")
public class AgentStrategyConfigTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentCode;
    private String agentName;
    private String trainScopeType;
    private String triggerType;
    private String checkCron;
    private Integer dataDelayPeriods;
    private Integer modelValidityPeriods;
    private Integer maxRetryCount;
    private Integer retryIntervalMinutes;
    private Integer autoTrainEnabled;
    private Integer autoForecastEnabled;
    private Integer forecastLeadPeriods;
    private Integer enabled;
    private String description;
    private Date createdAt;
    private Date updatedAt;
}
