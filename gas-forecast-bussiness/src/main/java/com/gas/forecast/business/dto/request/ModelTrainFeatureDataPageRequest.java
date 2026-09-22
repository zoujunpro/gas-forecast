package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练特征数据分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainFeatureDataPageRequest {
    @Min(value = 1, message = "页码不能小于1")
    private Integer page;

    @Min(value = 1, message = "每页条数不能小于1")
    private Integer size;

    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;

    @Size(max = 32, message = "时间跨度长度不能超过32个字符")
    private String timeGranularity;

    @Size(max = 32, message = "统计日期长度不能超过32个字符")
    private String statDate;

    @Size(max = 64, message = "区域编码长度不能超过64个字符")
    private String regionCode;

    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    @Size(max = 32, message = "开始统计日期长度不能超过32个字符")
    private String statDateStart;

    @Size(max = 32, message = "结束统计日期长度不能超过32个字符")
    private String statDateEnd;

    @Size(max = 32, message = "排序字段长度不能超过32个字符")
    private String sortField;

    @Size(max = 16, message = "排序方向长度不能超过16个字符")
    private String sortOrder;

    public Integer page() {
        return page;
    }

    public Integer size() {
        return size;
    }

    public String keyword() {
        return keyword;
    }

    public String timeGranularity() {
        return timeGranularity;
    }

    public String statDate() {
        return statDate;
    }

    public String regionCode() {
        return regionCode;
    }

    public String industryCode() {
        return industryCode;
    }

    public String customerCode() {
        return customerCode;
    }

    public String statDateStart() {
        return statDateStart;
    }

    public String statDateEnd() {
        return statDateEnd;
    }

    public String sortField() {
        return sortField;
    }

    public String sortOrder() {
        return sortOrder;
    }
}
