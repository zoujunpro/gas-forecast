package com.gas.forecast.business.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gas.forecast.common.core.PageInfoDTO;
import java.util.List;

public final class PageUtils {

    private PageUtils() {}

    public static <T> Page<T> pageRequest(int page, int size) {
        return Page.of(Math.max(page, 1), Math.max(size, 1));
    }

    public static <T> PageInfoDTO<T> toPage(IPage<?> page, List<T> records) {
        return PageInfoDTO.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
