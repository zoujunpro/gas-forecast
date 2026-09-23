package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;

/**
 * 模型预测记录表
 * @TableName model_forecast_record_tb
 */
@TableName(value = "model_forecast_record_tb")
@Data
public class ModelForecastRecordTb {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联预测配置ID */
    private Long forecastId;

    /** 预测批次号 */
    private String forecastBatchNo;

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
     * 发起预测时使用的特征数据快照
     */
    @TableField(select = false)
    private byte[] featureSnapshot;

    /**
     * 模型预测请求参数
     */
    @TableField(select = false)
    private byte[] requestParam;

    /**
     * 模型预测响应参数
     */
    @TableField(select = false)
    private byte[] responseParam;

    /** 预测状态：1预测中、2预测成功、3预测失败 */
    private Integer status;

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
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date updatedAt;

    /** 预测完成时间 */
    private String forecastEndTime;
}
