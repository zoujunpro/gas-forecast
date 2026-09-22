package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 模型配置作用范围表
 * @TableName model_config_scope_tb
 */
@TableName(value ="model_config_scope_tb")
@Data
public class ModelConfigScopeTb {
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
     * 模型配置编码，仅保留兼容历史数据。
     */
    private String modelCode;

    /**
     * 地区编码
     */
    private String regionCode;

    /**
     * 行业编码
     */
    private String industryCode;

    /**
     * 客户编码
     */
    private String customerCode;

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
