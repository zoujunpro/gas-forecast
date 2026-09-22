package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigPageRequest {
    /**
     * 当前页码，从1开始。
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page;

    /**
     * 每页条数。
     */
    @Min(value = 1, message = "每页条数不能小于1")
    private Integer size;

    /**
     * 搜索关键字，匹配配置编码、名称、智能体或场景。
     */
    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;

    public Integer page() {
        return page;
    }

    public Integer size() {
        return size;
    }

    public String keyword() {
        return keyword;
    }
}
