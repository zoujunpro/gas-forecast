package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特征定义分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeatureDefinitionPageRequest {
    @Min(value = 1, message = "页码不能小于1")
    private Integer page;

    @Min(value = 1, message = "每页条数不能小于1")
    private Integer size;

    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;

    @Size(max = 32, message = "时间跨度长度不能超过32个字符")
    private String timeGranularity;

    @Size(max = 20, message = "数据来源长度不能超过20个字符")
    private String sourceType;

    @Size(max = 20, message = "特征状态长度不能超过20个字符")
    private String status;

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

    public String sourceType() {
        return sourceType;
    }

    public String status() {
        return status;
    }
}
