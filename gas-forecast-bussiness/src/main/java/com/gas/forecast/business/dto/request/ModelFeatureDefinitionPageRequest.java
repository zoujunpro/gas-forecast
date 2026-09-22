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
}
