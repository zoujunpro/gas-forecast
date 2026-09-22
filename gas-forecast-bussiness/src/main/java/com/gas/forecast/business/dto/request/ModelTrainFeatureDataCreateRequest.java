package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练特征数据新增请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainFeatureDataCreateRequest {
    @NotBlank(message = "统计日期不能为空")
    private @Size(max = 32, message = "统计日期长度不能超过32个字符") String statDate;

    @NotBlank(message = "时间粒度不能为空")
    private @Size(max = 16, message = "时间粒度长度不能超过16个字符") String timeGranularity;

    @Size(max = 64, message = "区域编码长度不能超过64个字符")
    private String regionCode;

    @Size(max = 128, message = "区域名称长度不能超过128个字符")
    private String regionName;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    @Size(max = 128, message = "客户名称长度不能超过128个字符")
    private String customerName;

    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;

    @Size(max = 128, message = "行业名称长度不能超过128个字符")
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
}
