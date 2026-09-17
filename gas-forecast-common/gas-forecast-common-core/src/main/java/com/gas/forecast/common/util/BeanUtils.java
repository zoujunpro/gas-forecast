package com.gas.forecast.common.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.gas.forecast.common.core.BusinessException;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BeanUtils extends BeanUtil {

    public static <T, V> PageInfo<V> copyPageInfoProperties(PageInfo<T> srcPageInfo, Class<V> clazz) {
        PageInfo<V> targetPageInfo = new PageInfo<>();
        BeanUtil.copyProperties(srcPageInfo, targetPageInfo);
        if (CollectionUtil.isNotEmpty(srcPageInfo.getList())) {
            List<V> list = copyListProperties(srcPageInfo.getList(), clazz);
            targetPageInfo.setList(list);
        }

        return targetPageInfo;
    }

    public static <T, V> List<V> copyListProperties(List<T> srcList, Class<V> clazz) {
        List<V> list = new ArrayList<>();
        try {
            if (CollectionUtil.isNotEmpty(srcList)) {
                for (T t : srcList) {
                    V v = clazz.getDeclaredConstructor().newInstance();
                    BeanUtil.copyProperties(t, v);
                    list.add(v);
                }
            }
        } catch (Exception e) {
            log.error("数据转换异常", e);
        }
        return list;
    }

    public static void trimFiledSpace(Object object) {
        Class<?> clazz = object.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Class<?> type = field.getType();
                if (type.equals(String.class)) {
                    String value = (String) field.get(object);
                    if (value != null) {
                        value = value.trim();
                        field.set(object, value);
                    }
                }
            } catch (IllegalAccessException e) {
                log.error("空格去除异常{}", e);
                throw new BusinessException("空格去除异常");
            }
        }
    }
}
