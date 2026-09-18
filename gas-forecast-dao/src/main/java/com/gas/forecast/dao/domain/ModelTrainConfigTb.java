package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 智能体训练配置表
 * @TableName model_train_config_tb
 */
@TableName(value ="model_train_config_tb")
@Data
public class ModelTrainConfigTb {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 训练配置编码
     */
    private String trainCode;

    /**
     * 训练配置名称
     */
    private String trainName;

    /**
     * 智能体编码
     */
    private String agentCode;

    /**
     * 所属模型编码
     */
    private String modelCode;

    /**
     * 所属模型名称
     */
    private String modelName;

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
     * 训练数据开始日期
     */
    private String trainStartDate;

    /**
     * 训练数据结束日期
     */
    private String trainEndDate;

    /**
     * 训练方式：RECENT/RANGE
     */
    private String trainMode;

    /**
     * 时间格式：DAY/TENDAY/MONTH
     */
    private String timeGranularity;

    /**
     * 最近周期数
     */
    private Integer recentPeriods;

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
