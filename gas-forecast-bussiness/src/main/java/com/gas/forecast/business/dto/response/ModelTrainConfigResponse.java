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

}
