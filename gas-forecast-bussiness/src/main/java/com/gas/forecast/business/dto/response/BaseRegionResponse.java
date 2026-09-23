package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 区域基础信息应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseRegionResponse {
    /**
     * ID。
     */
    private Long id;

    /**
     * 区域编码。
     */
    private String regionCode;

    /**
     * 区域名称。
     */
    private String regionName;

    /**
     * 备注。
     */
    private String remark;

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
