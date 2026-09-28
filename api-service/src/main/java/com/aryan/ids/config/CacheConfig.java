package com.aryan.ids.config;

import java.time.Duration;

import com.aryan.ids.dto.StatsResponse;
import com.aryan.ids.service.Score;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis caching.
 * <ul>
 *   <li>{@code scores}: model output per unique flow (key = model version + SHA-256 of features), 1h TTL.
 *       Repeated identical flows skip the model call entirely.</li>
 *   <li>{@code stats}: dashboard aggregates, 30s TTL, so frequent polling doesn't hit MySQL.</li>
 * </ul>
 * Each cache gets a typed JSON serializer so values come back as the right Java type.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String SCORES = "scores";
    public static final String STATS = "stats";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(ObjectMapper objectMapper) {
        return builder -> builder
                .withCacheConfiguration(SCORES, typed(objectMapper, Score.class, Duration.ofHours(1)))
                .withCacheConfiguration(STATS, typed(objectMapper, StatsResponse.class, Duration.ofSeconds(30)));
    }

    private static <T> RedisCacheConfiguration typed(ObjectMapper mapper, Class<T> type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    /** If Redis is down, log and fall through to the real call instead of failing the request. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
