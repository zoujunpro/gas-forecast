package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelRankResponse {
    private String modelName;

    private String modelType;

    private BigDecimal mape;

    private BigDecimal wmape;

    private BigDecimal rmse;

    private BigDecimal mae;

    private BigDecimal r2;

    private String constituents;

    public String modelName() {
        return modelName;
    }

    public String modelType() {
        return modelType;
    }

    public BigDecimal mape() {
        return mape;
    }

    public BigDecimal wmape() {
        return wmape;
    }

    public BigDecimal rmse() {
        return rmse;
    }

    public BigDecimal mae() {
        return mae;
    }

    public BigDecimal r2() {
        return r2;
    }

    public String constituents() {
        return constituents;
    }
}
