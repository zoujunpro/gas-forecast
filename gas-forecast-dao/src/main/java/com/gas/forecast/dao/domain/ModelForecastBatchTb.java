package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 预测任务批次
 * @TableName model_forecast_batch_tb
 */
@TableName(value ="model_forecast_batch_tb")
@Data
public class ModelForecastBatchTb {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 预测批次号
     */
    private String batchNo;

    /**
     * 使用的训练批次
     */
    private String trainBatchNo;

    /**
     * 智能体编码
     */
    private String agentCode;

    /**
     * 
     */
    private String regionCode;

    /**
     * 区域名称
     */
    private String regionName;

    /**
     * 客户编码
     */
    private String customerCode;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 所属行业编号
     */
    private String industryCode;

    /**
     * 所属行业名字
     */
    private String industryName;

    /**
     * 预测跨度数量
     */
    private Integer forecastHorizon;

    /**
     * 预测起始日期
     */
    private Integer forecastStartDate;

    /**
     * 预测结束日期
     */
    private String forecastEndDate;

    /**
     * PENDING/RUNNING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 预测调用参数
     */
    private Object requestJson;

    /**
     * 错误信息
     */
    private String errorMessage;

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
