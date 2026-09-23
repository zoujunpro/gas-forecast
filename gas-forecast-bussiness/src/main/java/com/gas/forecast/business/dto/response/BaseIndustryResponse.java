package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行业基础信息应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseIndustryResponse {
    /**
     * ID。
     */
    private Long id;

    /**
     * 行业编码。
     */
    private String industryCode;

    /**
     * 行业名称。
     */
    private String industryName;

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
}
