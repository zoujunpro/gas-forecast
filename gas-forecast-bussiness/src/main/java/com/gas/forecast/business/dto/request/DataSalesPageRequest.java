package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标准销量数据分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSalesPageRequest {
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
     * 搜索关键字，匹配区域、行业、客户、客户编码或来源文件。
     */
    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;

    /**
     * 统计开始日期。
     */
    @Size(max = 32, message = "开始日期长度不能超过32个字符")
    private String startDate;

    /**
     * 统计结束日期。
     */
    @Size(max = 32, message = "结束日期长度不能超过32个字符")
    private String endDate;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    @Size(max = 64, message = "区域编码长度不能超过64个字符")
    private String regionCode;

    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;

    public Integer page() {
        return page;
    }

    public Integer size() {
        return size;
    }

    public String keyword() {
        return keyword;
    }

    public String startDate() {
        return startDate;
    }

    public String endDate() {
        return endDate;
    }

    public String customerCode() {
        return customerCode;
    }

    public String regionCode() {
        return regionCode;
    }

    public String industryCode() {
        return industryCode;
    }
}
