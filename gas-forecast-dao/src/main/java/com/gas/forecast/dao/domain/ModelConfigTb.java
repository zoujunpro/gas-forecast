package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 模型配置表
 * @TableName model_config_tb
 */
@TableName(value ="model_config_tb")
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
