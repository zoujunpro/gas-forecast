package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户基础信息应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseCustomerResponse {
    /**
     * 客户ID。
     */
    private Long id;

    /**
     * 客户编码。
     */
    private String customerCode;

    /**
     * 客户名称。
     */
    private String customerName;

    /**
     * 所属行业编码。
     */
    private String industryCode;

    /**
     * 所属行业名称。
     */
    private String industryName;

    /**
     * 所属区域编码。
     */
    private String regionCode;

    /**
     * 所属区域名称。
     */
    private String regionName;

    /**
     * 原始区域名称。
     */
    private String rawRegionName;

    /**
     * 原始行业名称。
     */
    private String rawIndustryName;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date updatedAt;

    public Long id() {
        return id;
    }

    public String customerCode() {
        return customerCode;
    }

    public String customerName() {
        return customerName;
    }

    public String industryCode() {
        return industryCode;
    }

    public String industryName() {
        return industryName;
    }

    public String regionCode() {
        return regionCode;
    }

    public String regionName() {
        return regionName;
    }

    public String rawRegionName() {
        return rawRegionName;
    }

    public String rawIndustryName() {
        return rawIndustryName;
    }

    public Date createdAt() {
        return createdAt;
    }

    public Date updatedAt() {
        return updatedAt;
    }
}
