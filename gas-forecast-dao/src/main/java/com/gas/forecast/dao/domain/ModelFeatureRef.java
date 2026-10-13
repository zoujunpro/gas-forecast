package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 模型特征关联表
 *
 * @TableName model_feature_ref
 */
@TableName(value = "model_feature_ref")
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

    /** 模型接口使用的特征键；为空时使用标准特征编码。 */
    private String outputFeatureCode;

    /**
     * 是否必选特征
     */
    private Integer requiredFlag;

    /**
     * 特征顺序
     */
    private Integer featureOrder;

    /** 模型使用该特征时覆盖默认聚合方式。 */
    private String aggregationMethod;

    /** 模型使用该特征时覆盖默认缺失值策略。 */
    private String missingValueStrategy;

    /** 模型特有计算参数JSON。 */
    private String calculationParams;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 创建人ID
     */
    private Long createdBy;
}
