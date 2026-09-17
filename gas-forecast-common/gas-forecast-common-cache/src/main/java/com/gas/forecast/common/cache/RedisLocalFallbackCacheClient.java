package com.gas.forecast.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.common.cache.properties.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RedisLocalFallbackCacheClient implements CacheClient {
    private static final Logger log = LoggerFactory.getLogger(RedisLocalFallbackCacheClient.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;
    private final Map<String, LocalCacheValue> localCache = new ConcurrentHashMap<>();
    private final AtomicLong redisUnavailableUntil = new AtomicLong(0);

    public RedisLocalFallbackCacheClient(RedisTemplate<String, Object> redisTemplate,
                                         ObjectMapper objectMapper,
                                         CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        Assert.hasText(key, "Cache key must not be blank");
        Assert.notNull(type, "Cache type must not be null");
        Object value = getValue(key);
        return convertValue(value, type);
    }

    @Override
    public <T> T get(String key, TypeReference<T> typeReference) {
        Assert.hasText(key, "Cache key must not be blank");
        Assert.notNull(typeReference, "Cache type reference must not be null");
        Object value = getValue(key);
        return convertValue(value, typeReference);
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        Assert.hasText(key, "Cache key must not be blank");
        if (value == null) {
            evict(key);
            return;
        }

        Duration effectiveTtl = effectiveTtl(ttl);
        putLocal(key, value, effectiveTtl);
        if (redisUnavailable()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, effectiveTtl);
        } catch (RuntimeException exception) {
            markRedisUnavailable(exception);
        }
    }

    @Override
    public void evict(String key) {
        Assert.hasText(key, "Cache key must not be blank");
        localCache.remove(key);
        if (redisUnavailable()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            markRedisUnavailable(exception);
        }
    }

    private Object getValue(String key) {
        if (!redisUnavailable()) {
            try {
                Object value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    return value;
                }
            } catch (RuntimeException exception) {
                markRedisUnavailable(exception);
            }
        }
        return getLocal(key);
    }

    private void putLocal(String key, Object value, Duration ttl) {
        cleanupLocalCacheIfNeeded();
        localCache.put(key, new LocalCacheValue(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    private Object getLocal(String key) {
        LocalCacheValue cacheValue = localCache.get(key);
        if (cacheValue == null) {
            return null;
        }
        if (cacheValue.expiresAt() <= System.currentTimeMillis()) {
            localCache.remove(key);
            return null;
        }
        return cacheValue.value();
    }

    private void cleanupLocalCacheIfNeeded() {
        int localMaxSize = Math.max(1, properties.localMaxSize());
        if (localCache.size() < localMaxSize) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, LocalCacheValue>> iterator = localCache.entrySet().iterator();
        while (iterator.hasNext() && localCache.size() >= localMaxSize) {
            Map.Entry<String, LocalCacheValue> entry = iterator.next();
            if (entry.getValue().expiresAt() <= now) {
                iterator.remove();
            }
        }
        Iterator<String> keyIterator = localCache.keySet().iterator();
        while (keyIterator.hasNext() && localCache.size() >= localMaxSize) {
            keyIterator.next();
            keyIterator.remove();
        }
    }

    private boolean redisUnavailable() {
        return redisUnavailableUntil.get() > System.currentTimeMillis();
    }

    private void markRedisUnavailable(RuntimeException exception) {
        if (isRedisConnectionException(exception)) {
            long unavailableUntil = System.currentTimeMillis() + properties.redisRetryInterval().toMillis();
            redisUnavailableUntil.set(unavailableUntil);
            log.warn("Redis is unavailable, use local cache fallback for {} ms",
                    properties.redisRetryInterval().toMillis(), exception);
            return;
        }
        throw exception;
    }

    private boolean isRedisConnectionException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RedisConnectionFailureException || current instanceof DataAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration effectiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return Duration.ofMinutes(30);
        }
        return ttl;
    }

    private <T> T convertValue(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return objectMapper.convertValue(value, type);
    }

    private <T> T convertValue(Object value, TypeReference<T> typeReference) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, typeReference);
    }

    private record LocalCacheValue(Object value, long expiresAt) {
    }
}
