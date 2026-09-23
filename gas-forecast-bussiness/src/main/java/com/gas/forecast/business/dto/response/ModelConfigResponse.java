package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigResponse {
    /**
     * ID。
     */
    private Long id;

    /**
     * 配置编码。
     */
    private String configCode;

    /**
     * 配置名称。
     */
    private String configName;

    /**
     * 模型版本。
     */
    private String modelVersion;

    /**
     * 智能体编码。
     */
    private String agentCode;

    /**
     * 智能体名称。
     */
    private String agentName;

    /**
     * 场景编码。
     */
    private String sceneCode;

    /**
     * 策略类型。
     */
    private String strategyType;

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
     * 区域编码列表。
     */
    private List<String> regionCodes;

    /**
     * 区域名称列表。
     */
    private List<String> regionNames;

    /**
     * 行业编码列表。
     */
    private List<String> industryCodes;

    /**
     * 行业名称列表。
     */
    private List<String> industryNames;

    /**
     * 客户编码列表。
     */
    private List<String> customerCodes;

    /**
     * 客户名称列表。
     */
    private List<String> customerNames;

    /**
     * 模型特征关联列表。
     */
    private List<ModelFeatureRefResponse> featureRefs;

    /**
     * 描述。
     */
    private String description;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
