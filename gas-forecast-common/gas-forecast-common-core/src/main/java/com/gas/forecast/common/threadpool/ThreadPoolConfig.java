package com.gas.forecast.common.threadpool;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 线程池显式配置。
 *
 * <p>遵循《阿里巴巴 Java 开发手册》：业务线程池必须通过 {@code ThreadPoolExecutor}
 * 明确声明核心参数，禁止直接使用 {@code Executors} 创建线程池。</p>
 */
public record ThreadPoolConfig(
        String poolName,
        int corePoolSize,
        int maximumPoolSize,
        int queueCapacity,
        long keepAliveTime,
        TimeUnit keepAliveUnit) {

    public ThreadPoolConfig {
        if (poolName == null || poolName.isBlank()) {
            throw new IllegalArgumentException("线程池名称不能为空");
        }
        if (corePoolSize < 0 || maximumPoolSize <= 0 || corePoolSize > maximumPoolSize) {
            throw new IllegalArgumentException("线程池大小非法：corePoolSize 必须大于等于 0 且不大于 maximumPoolSize");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("线程池队列容量必须大于 0");
        }
        if (keepAliveTime < 0) {
            throw new IllegalArgumentException("线程空闲存活时间不能小于 0");
        }
        Objects.requireNonNull(keepAliveUnit, "keepAliveUnit 不能为空");
    }

    public static ThreadPoolConfig of(String poolName, int corePoolSize, int maximumPoolSize, int queueCapacity) {
        return new ThreadPoolConfig(poolName, corePoolSize, maximumPoolSize, queueCapacity, 60, TimeUnit.SECONDS);
    }
}
