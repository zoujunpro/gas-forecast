package com.gas.forecast.common.security.context;

import cn.hutool.core.convert.Convert;
import com.alibaba.excel.util.StringUtils;
import com.gas.forecast.common.security.contants.SecurityConstants;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 获取当前线程变量中的 用户id、用户名称、Token等信息
 * 注意： 必须在网关通过请求头的方法传入，同时在HeaderInterceptor拦截器设置值。 否则这里无法获取
 *
 * @author ruoyi
 */
public class SecurityContextHolder {
    private static final ThreadLocal<Map<String, Object>> THREAD_LOCAL = new ThreadLocal<>();

    public static void set(String key, Object value) {
        Map<String, Object> map = getLocalMap();
        map.put(key, value == null ? StringUtils.EMPTY : value);
    }

    public static String get(String key) {
        Map<String, Object> map = getLocalMap();
        return Convert.toStr(map.getOrDefault(key, StringUtils.EMPTY));
    }

    public static <T> T get(String key, Class<T> clazz) {
        Map<String, Object> map = getLocalMap();
        return Convert.convert(clazz, map.get(key));
    }

    public static Map<String, Object> getLocalMap() {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (map == null) {
            map = new ConcurrentHashMap<String, Object>();
            THREAD_LOCAL.set(map);
        }
        return map;
    }

    public static void setLocalMap(Map<String, Object> threadLocalMap) {
        THREAD_LOCAL.set(threadLocalMap);
    }

    public static Long getUserId() {
        return Convert.toLong(get(SecurityConstants.USER_ID), 0L);
    }

    public static void setUserId(String account) {
        set(SecurityConstants.USER_ID, account);
    }

    public static String getUserName() {
        return get(SecurityConstants.USERNAME);
    }

    public static String getUserKey() {
        return get(SecurityConstants.USER_KEY);
    }

    public static void setUserKey(String userKey) {
        set(SecurityConstants.USER_KEY, userKey);
    }

    public static void setJobNum(String jobNum) {
        set(SecurityConstants.JOB_NUM, jobNum);
    }

    public static String getJobNum() {
        return get(SecurityConstants.JOB_NUM);
    }

    public static void setUserName(String username) {
        set(SecurityConstants.USERNAME, username);
    }

    public static String getRequest() {
        return get(SecurityConstants.TRACE_ID_HEADER);
    }

    public static void setRequestId(String requestId) {
        set(SecurityConstants.TRACE_ID_HEADER, requestId);
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
