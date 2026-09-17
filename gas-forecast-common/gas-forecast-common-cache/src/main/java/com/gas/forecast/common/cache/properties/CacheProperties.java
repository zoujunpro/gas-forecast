package com.gas.forecast.common.cache.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "gas.cache")
public record CacheProperties(
        /**
         * Redis 操作失败后的重试间隔；在这个时间窗口内，缓存读写会优先使用本地缓存降级。
         */
        Duration redisRetryInterval,

        /**
         * 本地降级缓存最多保留的键值数量，超过后会先清理过期数据，再移除部分旧数据。
         */
        int localMaxSize
) {
    public CacheProperties {
        if (redisRetryInterval == null || redisRetryInterval.isZero() || redisRetryInterval.isNegative()) {
            redisRetryInterval = Duration.ofSeconds(30);
        }
        if (localMaxSize <= 0) {
            localMaxSize = 10_000;
        }
    }
}
