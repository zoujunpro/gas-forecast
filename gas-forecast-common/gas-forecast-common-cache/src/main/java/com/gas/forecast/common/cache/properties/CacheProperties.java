package com.gas.forecast.common.cache.properties;

import java.time.Duration;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gas.cache")
@Data
@NoArgsConstructor
public class CacheProperties {
    /**
     * Redis 操作失败后的重试间隔；在这个时间窗口内，缓存读写会优先使用本地缓存降级。
     */
    private Duration redisRetryInterval;

    /**
     * 本地降级缓存最多保留的键值数量，超过后会先清理过期数据，再移除部分旧数据。
     */
    private int localMaxSize;

    public CacheProperties(Duration redisRetryInterval, int localMaxSize) {
        if (redisRetryInterval == null || redisRetryInterval.isZero() || redisRetryInterval.isNegative()) {
            redisRetryInterval = Duration.ofSeconds(30);
        }
        if (localMaxSize <= 0) {
            localMaxSize = 10_000;
        }
        this.redisRetryInterval = redisRetryInterval;
        this.localMaxSize = localMaxSize;
    }

    public Duration redisRetryInterval() {
        return redisRetryInterval;
    }

    public int localMaxSize() {
        return localMaxSize;
    }
}
