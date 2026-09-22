package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BacktestDetailResponse {
    private String modelName;

    private String season;

    private LocalDate date;

    private BigDecimal actual;

    private BigDecimal prediction;

    private BigDecimal absoluteError;

    private BigDecimal apePct;

    public String modelName() {
        return modelName;
    }

    public String season() {
        return season;
    }

    public LocalDate date() {
        return date;
    }

    public BigDecimal actual() {
        return actual;
    }

    public BigDecimal prediction() {
        return prediction;
    }

    public BigDecimal absoluteError() {
        return absoluteError;
    }

    public BigDecimal apePct() {
        return apePct;
    }
}
