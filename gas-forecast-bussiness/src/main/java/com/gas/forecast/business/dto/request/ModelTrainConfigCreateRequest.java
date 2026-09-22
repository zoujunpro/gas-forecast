package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练配置新增请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainConfigCreateRequest {
    @Size(max = 64, message = "训练配置编码长度不能超过64个字符")
    private String trainCode;

    @NotBlank(message = "训练配置名称不能为空")
    private @Size(max = 128, message = "训练配置名称长度不能超过128个字符") String trainName;

    @NotBlank(message = "智能体编码不能为空")
    private @Size(max = 64, message = "智能体编码长度不能超过64个字符") String agentCode;

    @NotNull(message = "所属模型不能为空")
    private Long modelId;

    private String modelCode;

    @Size(max = 128, message = "所属模型名称长度不能超过128个字符")
    private String modelName;

    @Size(max = 64, message = "区域编码长度不能超过64个字符")
    private String regionCode;

    @Size(max = 64, message = "区域名称长度不能超过64个字符")
    private String regionName;

    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;

    @Size(max = 64, message = "行业名称长度不能超过64个字符")
    private String industryName;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    @Size(max = 128, message = "客户名称长度不能超过128个字符")
    private String customerName;

    @Size(max = 32, message = "训练开始日期长度不能超过32个字符")
    private String trainStartDate;

    @Size(max = 32, message = "训练结束日期长度不能超过32个字符")
    private String trainEndDate;

    @Size(max = 32, message = "训练方式长度不能超过32个字符")
    private String trainMode;

    @Size(max = 32, message = "时间格式长度不能超过32个字符")
    private String timeGranularity;

    private Integer recentPeriods;

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;

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
}
