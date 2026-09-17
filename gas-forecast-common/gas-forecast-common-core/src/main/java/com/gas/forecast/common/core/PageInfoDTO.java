package com.gas.forecast.common.core;

import java.io.Serializable;
import java.util.List;

public record PageInfoDTO<T>(List<T> records, long total, long page, long size) implements Serializable {

    public static <T> PageInfoDTO<T> of(List<T> records, long total, long page, long size) {
        return new PageInfoDTO<>(records, total, page, size);
    }
}
