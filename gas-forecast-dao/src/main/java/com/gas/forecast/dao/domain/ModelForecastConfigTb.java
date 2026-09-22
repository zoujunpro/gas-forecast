package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 模型预测配置表
 * @TableName model_forecast_config_tb
 */
@TableName(value = "model_forecast_config_tb")
@Data
public class ModelForecastConfigTb {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预测名称 */
    private String forecastName;

    /**
     * 智能体编码
     */
    private String agentCode;

    /**
     * 作用范围：REGION/CUSTOMER/INDUSTRY/ALL
     */
    private String scopeType;

    /**
     * 区域编号
     */
    private String regionCode;

    /**
     * 区域名称
     */
    private String regionName;

    /**
     * 行业编号
     */
    private String industryCode;

    /**
     * 行业名称
     */
    private String industryName;

    /**
     * 客户编号
     */
    private String customerCode;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 预测开始日期
     */
    private String forecastStartDate;

    /**
     * 预测步长
     */
    private Integer forecastHorizon;

    /**
     * 时间颗粒度：DAILY/TENDAY/MONTHLY
     */
    private String forecastFrequency;

    /**
     * 是否自动预测
     */
    private Integer autoForecast;

    /**
     * 关联训练配置
     */
    private String trainConfigCode;

    /**
     * 是否启用
     */
    private Integer enabled;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建人名称
     */
    private String createdByName;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
