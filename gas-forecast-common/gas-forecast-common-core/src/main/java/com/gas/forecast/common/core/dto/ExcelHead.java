package com.gas.forecast.common.core.dto;

import lombok.Data;

@Data
public class ExcelHead<T> {

    private String fieldName;

    private String title;

    private T nullValue;

    private int position;

    public ExcelHead(String fieldName, String title) {
        this.fieldName = fieldName;
        this.title = title;
    }

    public ExcelHead(String fieldName, String title, T nullValue) {
        this.fieldName = fieldName;
        this.title = title;
        this.nullValue = nullValue;
    }
}
