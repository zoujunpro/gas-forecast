package com.gas.forecast.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.function.Supplier;

public interface CacheClient {

    <T> T get(String key, Class<T> type);

    <T> T get(String key, TypeReference<T> typeReference);

    void put(String key, Object value, Duration ttl);

    void evict(String key);

    default <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cachedValue = get(key, type);
        if (cachedValue != null) {
            return cachedValue;
        }
        T loadedValue = loader.get();
        if (loadedValue != null) {
            put(key, loadedValue, ttl);
        }
        return loadedValue;
    }

    default <T> T getOrLoad(String key, TypeReference<T> typeReference, Duration ttl, Supplier<T> loader) {
        T cachedValue = get(key, typeReference);
        if (cachedValue != null) {
            return cachedValue;
        }
        T loadedValue = loader.get();
        if (loadedValue != null) {
            put(key, loadedValue, ttl);
        }
        return loadedValue;
    }
}
