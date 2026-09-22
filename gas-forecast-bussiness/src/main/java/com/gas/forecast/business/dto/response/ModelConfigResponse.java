package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigResponse {
    private Long id;

    private String configCode;

    private String configName;

    private String modelVersion;

    private String agentCode;

    private String agentName;

    private String sceneCode;

    private String strategyType;

    private String regionCode;

    private String regionName;

    private String industryCode;

    private String industryName;

    private String customerCode;

    private String customerName;

    private List<String> regionCodes;

    private List<String> regionNames;

    private List<String> industryCodes;

    private List<String> industryNames;

    private List<String> customerCodes;

    private List<String> customerNames;

    private List<ModelFeatureRefResponse> featureRefs;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long id() {
        return id;
    }

    public String configCode() {
        return configCode;
    }

    public String configName() {
        return configName;
    }

    public String modelVersion() {
        return modelVersion;
    }

    public String agentCode() {
        return agentCode;
    }

    public String agentName() {
        return agentName;
    }

    public String sceneCode() {
        return sceneCode;
    }

    public String strategyType() {
        return strategyType;
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

    public List<String> regionCodes() {
        return regionCodes;
    }

    public List<String> regionNames() {
        return regionNames;
    }

    public List<String> industryCodes() {
        return industryCodes;
    }

    public List<String> industryNames() {
        return industryNames;
    }

    public List<String> customerCodes() {
        return customerCodes;
    }

    public List<String> customerNames() {
        return customerNames;
    }

    public List<ModelFeatureRefResponse> featureRefs() {
        return featureRefs;
    }

    public String description() {
        return description;
    }

    public Date createTime() {
        return createTime;
    }

    public Date updateTime() {
        return updateTime;
    }
}
