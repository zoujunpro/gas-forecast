package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimensionItemResponse {
    private String code;

    private String name;

    private String parentCode;

    private String type;

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String parentCode() {
        return parentCode;
    }

    public String type() {
        return type;
    }
}
