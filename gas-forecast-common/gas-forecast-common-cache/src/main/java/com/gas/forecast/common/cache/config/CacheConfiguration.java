package com.gas.forecast.common.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.common.cache.CacheClient;
import com.gas.forecast.common.cache.RedisLocalFallbackCacheClient;
import com.gas.forecast.common.cache.properties.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfiguration {

    @Bean
    public RedisTemplate<String, Object> appRedisTemplate(RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public CacheClient cacheClient(RedisTemplate<String, Object> appRedisTemplate, ObjectMapper objectMapper, CacheProperties cacheProperties) {
        return new RedisLocalFallbackCacheClient(appRedisTemplate, objectMapper, cacheProperties);
    }
}
