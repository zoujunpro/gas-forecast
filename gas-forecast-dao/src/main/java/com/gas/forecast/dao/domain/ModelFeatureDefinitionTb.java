package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 特征定义表
 *
 * @TableName model_feature_definition_tb
 */
@TableName(value = "model_feature_definition_tb")
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
     * 数据来源 DATABASE/API/REPORT_LLM
     */
    private String sourceType;

    private String valueSource;

    private String sourceField;

    private String calculationType;

    private String calculationParams;

    private String aggregationMethod;

    private String missingValueStrategy;

    /**
     * 特征状态 CANDIDATE/ACTIVE/DISABLED
     */
    private String status;

    /**
     * 是否启用 1 0
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
