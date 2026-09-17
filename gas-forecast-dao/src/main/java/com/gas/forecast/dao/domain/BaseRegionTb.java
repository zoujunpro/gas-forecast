package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 区域基础信息表
 * @TableName base_region_tb
 */
@TableName(value ="base_region_tb")
@Data
public class BaseRegionTb {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String regionCode;

    /**
     * 区域名称
     */
    private String regionName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 最后修改人
     */
    private String updatedBy;

    /**
     * 最后修改时间
     */
    private Date updatedAt;

    /**
     * 更新人名字
     */
    private String updatedByName;

    /**
     * 创建人名字
     */
    private String createdByName;
}