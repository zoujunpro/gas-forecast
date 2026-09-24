package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 天然气客户基础信息表
 *
 * @TableName base_customer_tb
 */
@TableName(value = "base_customer_tb")
@Data
public class BaseCustomerTb {
    /**
     *
     */
    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 所属地区编号
     */
    private String regionCode;

    /**
     * 所属地区名字
     */
    private String regionName;

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

    /**
     * 原始地区名称
     */
    private String rawRegionName;

    /**
     * 原始行业名称
     */
    private String rawIndustryName;
}
