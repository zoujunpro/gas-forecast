package com.gas.forecast.common.core.dto;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class ExcelSelectedResolve {

    private String[] source;

    private int firstRow;

    private int lastRow;

    private String columnName;

    public String[] resolveSelectedSource(ExcelSelected excelSelected) {
        if (excelSelected == null) {
            return null;
        }

        String[] source = excelSelected.source();
        if (source.length > 0) {
            return source;
        }

        Class<? extends ExcelDynamicSelect>[] classes = excelSelected.sourceClass();
        if (classes.length > 0) {
            try {
                ExcelDynamicSelect excelDynamicSelect = classes[0].getDeclaredConstructor().newInstance();
                String[] dynamicSelectSource = excelDynamicSelect.getSource();
                if (dynamicSelectSource != null && dynamicSelectSource.length > 0) {
                    return dynamicSelectSource;
                }
            } catch (ReflectiveOperationException e) {
                log.error("解析动态下拉框数据异常", e);
            }
        }
        return null;
    }
}
