package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastSummaryResponse {
    private String provinceCode;

    private String province;

    private String bestModel;

    private BigDecimal backtestMape;

    private BigDecimal backtestWmape;

    private BigDecimal backtestRmse;

    private Integer featureCount;

    private Integer forecastHorizon;

    private String weatherSource;

    private String chartPath;

    public String provinceCode() {
        return provinceCode;
    }

    public String province() {
        return province;
    }

    public String bestModel() {
        return bestModel;
    }

    public BigDecimal backtestMape() {
        return backtestMape;
    }

    public BigDecimal backtestWmape() {
        return backtestWmape;
    }

    public BigDecimal backtestRmse() {
        return backtestRmse;
    }

    public Integer featureCount() {
        return featureCount;
    }

    public Integer forecastHorizon() {
        return forecastHorizon;
    }

    public String weatherSource() {
        return weatherSource;
    }

    public String chartPath() {
        return chartPath;
    }
}
