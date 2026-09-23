package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标准销量数据应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSalesResponse {
    /**
     * 数据ID。
     */
    private Long id;

    /**
     * 统计日期。
     */
    private String statDate;

    /**
     * 区域编码。
     */
    private String regionCode;

    /**
     * 区域名称。
     */
    private String regionName;

    /**
     * 行业编码。
     */
    private String industryCode;

    /**
     * 行业名称。
     */
    private String industryName;

    /**
     * 客户编码。
     */
    private String customerCode;

    /**
     * 客户名称。
     */
    private String customerName;

    /**
     * 销量值。
     */
    private String gasSales;

    /**
     * 来源文件。
     */
    private String fileId;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createdAt;
}
