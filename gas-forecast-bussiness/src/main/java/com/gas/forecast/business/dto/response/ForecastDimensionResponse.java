package com.gas.forecast.business.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastDimensionResponse {
    private List<DimensionItemResponse> areas;

    private List<DimensionItemResponse> provinces;

    private List<DimensionItemResponse> customers;

    public List<DimensionItemResponse> areas() {
        return areas;
    }

    public List<DimensionItemResponse> provinces() {
        return provinces;
    }

    public List<DimensionItemResponse> customers() {
        return customers;
    }
}
