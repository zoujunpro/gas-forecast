package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型训练配置分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainConfigPageRequest {
    @Min(value = 1, message = "页码不能小于1")
    private Integer page;

    @Min(value = 1, message = "每页条数不能小于1")
    private Integer size;

    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;

    @Size(max = 64, message = "智能体编码长度不能超过64个字符")
    private String agentCode;

    @Size(max = 32, message = "时间格式长度不能超过32个字符")
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

    public String agentCode() {
        return agentCode;
    }

    public String timeGranularity() {
        return timeGranularity;
    }
}
