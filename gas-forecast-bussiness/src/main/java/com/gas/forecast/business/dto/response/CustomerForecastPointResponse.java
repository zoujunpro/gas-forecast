package com.gas.forecast.business.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerForecastPointResponse {
    private String customerCode;

    private String customerName;

    private String customerType;

    private LocalDate date;

    private String tendayLabel;

    private BigDecimal prediction;

    private BigDecimal lower;

    private BigDecimal upper;

    public String customerCode() {
        return customerCode;
    }

    public String customerName() {
        return customerName;
    }

    public String customerType() {
        return customerType;
    }

    public LocalDate date() {
        return date;
    }

    public String tendayLabel() {
        return tendayLabel;
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
