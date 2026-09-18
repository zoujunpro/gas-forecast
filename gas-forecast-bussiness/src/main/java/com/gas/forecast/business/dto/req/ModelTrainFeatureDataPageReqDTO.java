package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 训练特征数据分页查询请求参数。
 */
public record ModelTrainFeatureDataPageReqDTO(
        @Min(value = 1, message = "页码不能小于1")
        Integer page,

        @Min(value = 1, message = "每页条数不能小于1")
        Integer size,

        @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
        String keyword,

        @Size(max = 32, message = "时间跨度长度不能超过32个字符")
        String timeGranularity,

        @Size(max = 32, message = "统计日期长度不能超过32个字符")
        String statDate,

        @Size(max = 64, message = "区域编码长度不能超过64个字符")
        String regionCode,

        @Size(max = 64, message = "行业编码长度不能超过64个字符")
        String industryCode,

        @Size(max = 64, message = "客户编码长度不能超过64个字符")
        String customerCode,

        @Size(max = 32, message = "开始统计日期长度不能超过32个字符")
        String statDateStart,

        @Size(max = 32, message = "结束统计日期长度不能超过32个字符")
        String statDateEnd,

        @Size(max = 32, message = "排序字段长度不能超过32个字符")
        String sortField,

        @Size(max = 16, message = "排序方向长度不能超过16个字符")
        String sortOrder
) {
}
