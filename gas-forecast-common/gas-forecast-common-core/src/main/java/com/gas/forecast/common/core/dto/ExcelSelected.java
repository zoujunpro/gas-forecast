package com.gas.forecast.common.core.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excel 下拉框注解。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelSelected {

    /**
     * 固定下拉内容。
     */
    String[] source() default {};

    /**
     * 动态下拉内容。
     */
    Class<? extends ExcelDynamicSelect>[] sourceClass() default {};

    /**
     * 下拉框起始行，默认第二行。
     */
    int firstRow() default 1;

    /**
     * 下拉框结束行，默认最后一行。
     */
    int lastRow() default 0x10000;
}
