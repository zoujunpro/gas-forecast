package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 冬季保供旬度清洗建模数据集表，包含旬度销量目标值和气象等基础特征
 * @TableName data_winter_tenday_dataset_tb
 */
@TableName(value ="data_winter_tenday_dataset_tb")
@Data
public class DataWinterTendayDatasetTb {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 地区编号
     */
    private String regionCode;

    /**
     * 地区名称
     */
    private String regionName;

    /**
     * 旬起始日期，01上旬、11中旬、21下旬
     */
    private String statDate;

    /**
     * 天然气销量
     */
    private BigDecimal gasSales;

    /**
     * 旬平均温度
     */
    private BigDecimal avgTemp;

    /**
     * 旬最高温度
     */
    private BigDecimal maxTemp;

    /**
     * 旬最低温度
     */
    private BigDecimal minTemp;

    /**
     * 采暖度日HDD
     */
    private BigDecimal hdd;

    /**
     * 极端低温天数
     */
    private Integer extremeColdDays;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

}
