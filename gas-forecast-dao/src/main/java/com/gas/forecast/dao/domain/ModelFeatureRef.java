package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 模型特征关联表
 * @TableName model_feature_ref
 */
@TableName(value ="model_feature_ref")
@Data
public class ModelFeatureRef {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型ID
     */
    private Long modelId;

    /**
     * 特征ID
     */
    private Long featureId;

    /**
     * 是否必选特征
     */
    private Integer requiredFlag;

    /**
     * 特征顺序
     */
    private Integer featureOrder;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 创建人ID
     */
    private Long createdBy;
}