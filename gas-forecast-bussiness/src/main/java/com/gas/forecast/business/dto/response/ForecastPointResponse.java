package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForecastPointResponse {
    private LocalDate date;

    private String tendayLabel;

    private BigDecimal avgTemp;

    private BigDecimal maxTemp;

    private BigDecimal minTemp;

    private BigDecimal hdd;

    private Integer extremeColdDays;

    private String weatherSource;

    private BigDecimal prediction;

    private BigDecimal lower;

    private BigDecimal upper;

    public LocalDate date() {
        return date;
    }

    public String tendayLabel() {
        return tendayLabel;
    }

    public BigDecimal avgTemp() {
        return avgTemp;
    }

    public BigDecimal maxTemp() {
        return maxTemp;
    }

    public BigDecimal minTemp() {
        return minTemp;
    }

    public BigDecimal hdd() {
        return hdd;
    }

    public Integer extremeColdDays() {
        return extremeColdDays;
    }

    public String weatherSource() {
        return weatherSource;
    }

    public BigDecimal prediction() {
        return prediction;
    }

    public BigDecimal lower() {
        return lower;
    }

    public BigDecimal upper() {
        return upper;
    }
}
