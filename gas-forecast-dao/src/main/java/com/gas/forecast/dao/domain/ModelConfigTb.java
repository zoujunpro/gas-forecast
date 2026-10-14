package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 模型配置表
 *
 * @TableName model_config_tb
 */
@TableName(value = "model_config_tb")
@Data
public class ModelConfigTb {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置编码
     */
    private String modelCode;

    /**
     * 配置名称
     */
    private String modelName;

    /**
     * 模型版本
     */
    private String modelVersion;

    /**
     * 智能体编码
     */
    private String agentCode;

    /** 支持的时间粒度：DAY/TENDAY/MONTH。 */
    private String timeGranularity;


    /** 最小预测周期数。 */
    private Integer minForecastHorizon;

    /** 最大预测周期数。 */
    private Integer maxForecastHorizon;

    /** 最短训练数据量，单位为当前时间粒度对应的记录数。 */
    private Integer minimumTrainingRows;

    /** 自动训练时建议使用的最近周期数。 */
    private Integer recommendedTrainingPeriods;

    /** 默认训练范围策略：RECOMMENDED/CUSTOM/ALL。 */
    private String defaultTrainingRangeMode;

    /** CUSTOM 策略下使用的训练周期数。 */
    private Integer defaultTrainingPeriods;

    /** 训练数据要求的补充说明。 */
    private String trainingDataRequirement;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人名字
     */
    private String createdByName;
}
