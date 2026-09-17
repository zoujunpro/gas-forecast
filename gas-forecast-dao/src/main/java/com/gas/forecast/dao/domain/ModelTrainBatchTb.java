package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 模型训练批次
 * @TableName model_train_batch_tb
 */
@TableName(value ="model_train_batch_tb")
@Data
public class ModelTrainBatchTb {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 训练批次号
     */
    private String batchNo;

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
     * 训练数据开始日期
     */
    private String trainStartDate;

    /**
     * 训练数据截止日期
     */
    private String trainEndDate;

    /**
     * PENDING/RUNNING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 最佳模型
     */
    private String bestModel;

    /**
     * MAPE
     */
    private BigDecimal mape;

    /**
     * WMAPE
     */
    private BigDecimal wmape;

    /**
     * RMSE
     */
    private BigDecimal rmse;

    /**
     * MAE
     */
    private BigDecimal mae;

    /**
     * R²
     */
    private BigDecimal r2;

    /**
     * 训练参数
     */
    private Object configJson;

    /**
     * 模型排名、特征、清洗记录等
     */
    private Object resultJson;

    /**
     * 模型文件地址
     */
    private String artifactUri;

    /**
     * 模型文件摘要
     */
    private String artifactSha256;

    /**
     * 模型产物版本
     */
    private String artifactVersion;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建人名字
     */
    private String createdByName;

    /**
     * 开始时间
     */
    private Date startedAt;

    /**
     * 完成时间
     */
    private Date completedAt;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

}