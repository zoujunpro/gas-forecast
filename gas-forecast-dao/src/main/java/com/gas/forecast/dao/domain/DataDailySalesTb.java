package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 天然气销量原始数据表
 * @TableName data_daily_sales_tb
 */
@TableName(value = "data_daily_sales_tb")
@Data
public class DataDailySalesTb {
    /**
     *
     */
    @TableId
    private Long id;

    /**
     * 统计日期
     */
    private String statDate;

    /**
     * 地区名称
     */
    private String regionName;

    /**
     * 行业名称
     */
    private String industryName;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户编号
     */
    private String customerCode;

    /**
     * 销量值
     */
    private String gasSales;

    /**
     * 来源文件
     */
    private String fileId;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 地区编号
     */
    private String regionCode;

    /**
     * 行业编号
     */
    private String industryCode;
}
