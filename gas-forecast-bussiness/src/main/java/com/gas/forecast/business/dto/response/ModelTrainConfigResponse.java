package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练配置应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainConfigResponse {
    private Long id;

    private String trainCode;

    private String trainName;

    private String agentCode;

    private Long modelId;

    private String modelCode;

    private String modelName;

    private String regionCode;

    private String regionName;

    private String industryCode;

    private String industryName;

    private String customerCode;

    private String customerName;

    private String trainStartDate;

    private String trainEndDate;

    private String trainMode;

    private String timeGranularity;

    private Integer recentPeriods;

    private Integer enabled;

    private String remark;

    private String createdBy;

    private String createdByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    public Long id() {
        return id;
    }

    public String trainCode() {
        return trainCode;
    }

    public String trainName() {
        return trainName;
    }

    public String agentCode() {
        return agentCode;
    }

    public Long modelId() {
        return modelId;
    }

    public String modelCode() {
        return modelCode;
    }

    public String modelName() {
        return modelName;
    }

    public String regionCode() {
        return regionCode;
    }

    public String regionName() {
        return regionName;
    }

    public String industryCode() {
        return industryCode;
    }

    public String industryName() {
        return industryName;
    }

    public String customerCode() {
        return customerCode;
    }

    public String customerName() {
        return customerName;
    }

    public String trainStartDate() {
        return trainStartDate;
    }

    public String trainEndDate() {
        return trainEndDate;
    }

    public String trainMode() {
        return trainMode;
    }

    public String timeGranularity() {
        return timeGranularity;
    }

    public Integer recentPeriods() {
        return recentPeriods;
    }

    public Integer enabled() {
        return enabled;
    }

    public String remark() {
        return remark;
    }

    public String createdBy() {
        return createdBy;
    }

    public String createdByName() {
        return createdByName;
    }

    public Date createdAt() {
        return createdAt;
    }

    public Date updatedAt() {
        return updatedAt;
    }
}
