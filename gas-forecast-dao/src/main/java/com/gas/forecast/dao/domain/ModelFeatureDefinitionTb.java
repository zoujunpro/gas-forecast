package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 特征定义表
 * @TableName model_feature_definition_tb
 */
@TableName(value ="model_feature_definition_tb")
@Data
public class ModelFeatureDefinitionTb {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 特征编号
     */
    private String featureCode;

    /**
     * 特征名称
     */
    private String featureName;

    /**
     * 宽表字段
     */
    private String featureColumn;

    /**
     * 时间粒度 DAY/TENDAY/MONTH
     */
    private String timeGranularity;

    /**
     * 是否启用 1  0
     */
    private Integer enabled;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人名字
     */
    private String updatedByName;
}
