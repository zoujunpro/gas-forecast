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
    /**
     * ID。
     */
    private Long id;

    /**
     * 训练配置编码。
     */
    private String trainCode;

    /**
     * 训练配置名称。
     */
    private String trainName;

    /**
     * 智能体编码。
     */
    private String agentCode;

    /**
     * 模型ID。
     */
    private Long modelId;

    /**
     * 模型编码。
     */
    private String modelCode;

    /**
     * 模型名称。
     */
    private String modelName;

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
     * 训练开始日期。
     */
    private String trainStartDate;

    /**
     * 训练结束日期。
     */
    private String trainEndDate;

    /**
     * 训练模式。
     */
    private String trainMode;

    /**
     * 时间粒度。
     */
    private String timeGranularity;

    /**
     * 最近期数。
     */
    private Integer recentPeriods;

    /**
     * 启用状态。
     */
    private Integer enabled;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建人。
     */
    private String createdBy;

    /**
     * 创建人名称。
     */
    private String createdByName;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
