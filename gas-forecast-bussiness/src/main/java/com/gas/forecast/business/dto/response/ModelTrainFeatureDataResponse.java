package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练特征数据应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainFeatureDataResponse {
    private Long id;

    private String statDate;

    private String timeGranularity;

    private String regionCode;

    private String regionName;

    private String customerCode;

    private String customerName;

    private String industryCode;

    private String industryName;

    private BigDecimal gasSales;

    private Double feature001;

    private Double feature002;

    private Double feature003;

    private Double feature004;

    private Double feature005;

    private Double feature006;

    private Double feature007;

    private Double feature008;

    private Double feature009;

    private Double feature010;

    private Map<String, Double> featureValues;

    private List<ModelTrainFeatureValueResponse> featureDetails;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long id() {
        return id;
    }

    public String statDate() {
        return statDate;
    }

    public String timeGranularity() {
        return timeGranularity;
    }

    public String regionCode() {
        return regionCode;
    }

    public String regionName() {
        return regionName;
    }

    public String customerCode() {
        return customerCode;
    }

    public String customerName() {
        return customerName;
    }

    public String industryCode() {
        return industryCode;
    }

    public String industryName() {
        return industryName;
    }

    public BigDecimal gasSales() {
        return gasSales;
    }

    public Double feature001() {
        return feature001;
    }

    public Double feature002() {
        return feature002;
    }

    public Double feature003() {
        return feature003;
    }

    public Double feature004() {
        return feature004;
    }

    public Double feature005() {
        return feature005;
    }

    public Double feature006() {
        return feature006;
    }

    public Double feature007() {
        return feature007;
    }

    public Double feature008() {
        return feature008;
    }

    public Double feature009() {
        return feature009;
    }

    public Double feature010() {
        return feature010;
    }

    public Map<String, Double> featureValues() {
        return featureValues;
    }

    public List<ModelTrainFeatureValueResponse> featureDetails() {
        return featureDetails;
    }

    public Date createTime() {
        return createTime;
    }

    public Date updateTime() {
        return updateTime;
    }
}
