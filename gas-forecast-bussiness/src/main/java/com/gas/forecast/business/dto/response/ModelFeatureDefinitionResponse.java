package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特征定义应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeatureDefinitionResponse {
    /**
     * ID。
     */
    private Long id;

    /**
     * 特征编码。
     */
    private String featureCode;

    /**
     * 特征名称。
     */
    private String featureName;

    /**
     * 特征字段。
     */
    private String featureColumn;

    /**
     * 时间粒度。
     */
    private String timeGranularity;

    /**
     * 启用状态。
     */
    private Integer enabled;

    /**
     * 描述。
     */
    private String description;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 创建人。
     */
    private Long createdBy;

    /**
     * 更新人名称。
     */
    private String updatedByName;
}
