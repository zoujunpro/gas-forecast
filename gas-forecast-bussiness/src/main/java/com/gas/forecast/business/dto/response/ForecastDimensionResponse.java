package com.gas.forecast.business.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测维度集合应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastDimensionResponse {
    /**
     * 区域列表。
     */
    private List<DimensionItemResponse> areas;

    /**
     * 省份列表。
     */
    private List<DimensionItemResponse> provinces;

    /**
     * 客户列表。
     */
    private List<DimensionItemResponse> customers;
}
