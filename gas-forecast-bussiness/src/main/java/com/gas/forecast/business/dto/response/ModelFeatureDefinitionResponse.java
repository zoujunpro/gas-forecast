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
    private Long id;

    private String featureCode;

    private String featureName;

    private String featureColumn;

    private String timeGranularity;

    private Integer enabled;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    private Long createdBy;

    private String updatedByName;

    public Long id() {
        return id;
    }

    public String featureCode() {
        return featureCode;
    }

    public String featureName() {
        return featureName;
    }

    public String featureColumn() {
        return featureColumn;
    }

    public String timeGranularity() {
        return timeGranularity;
    }

    public Integer enabled() {
        return enabled;
    }

    public String description() {
        return description;
    }

    public Date createdAt() {
        return createdAt;
    }

    public Long createdBy() {
        return createdBy;
    }

    public String updatedByName() {
        return updatedByName;
    }
}
