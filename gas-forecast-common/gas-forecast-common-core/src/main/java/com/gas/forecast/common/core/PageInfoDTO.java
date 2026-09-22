package com.gas.forecast.common.core;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageInfoDTO<T> implements Serializable {
    private List<T> records;

    private long total;

    private long page;

    private long size;

    public List<T> records() {
        return records;
    }

    public long total() {
        return total;
    }

    public long page() {
        return page;
    }

    public long size() {
        return size;
    }

    public static <T> PageInfoDTO<T> of(List<T> records, long total, long page, long size) {
        return new PageInfoDTO<>(records, total, page, size);
    }
}
