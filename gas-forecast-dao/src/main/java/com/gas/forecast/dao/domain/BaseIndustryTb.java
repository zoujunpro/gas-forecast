package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 天然气客户行业基础信息表
 *
 * @TableName base_industry_tb
 */
@TableName(value = "base_industry_tb")
@Data
public class BaseIndustryTb {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 行业编码
     */
    private String industryCode;

    /**
     * 行业名称
     */
    private String industryName;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 创建人名字
     */
    private String createdByName;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 更新人名字
     */
    private String updatedByName;
}
