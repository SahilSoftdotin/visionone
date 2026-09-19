package com.visiondigitallab.visionone.common;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process caching only, and a deliberately short list.
 *
 * <p>Nothing here would be wrong if stale for its TTL, and no KPI or metric is cached: a number
 * Gary reads comes from the database. Cache loss must never affect correctness, and the
 * integration suite runs once with caching disabled to prove it.
 *
 * <p>Valkey belongs here when a second instance runs - shared membership cache, relay leader
 * election, idempotency keys. Behind {@link CacheManager} that is a configuration swap, and it is
 * deliberately not deployed in Phase 1.
 */
@Configuration
public class CacheConfiguration {

    public static final String ORG_CONFIG = "orgConfig";
    public static final String CHANNEL_SOURCES = "channelSources";
    public static final String MEMBERSHIP = "membership";
    public static final String INTEGRATION_STATUS = "integrationStatus";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(ORG_CONFIG, CHANNEL_SOURCES, MEMBERSHIP, INTEGRATION_STATUS));
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(500)
                .recordStats());
        manager.registerCustomCache(ORG_CONFIG, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(50).recordStats().build());
        manager.registerCustomCache(CHANNEL_SOURCES, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(50).recordStats().build());
        manager.registerCustomCache(INTEGRATION_STATUS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1)).maximumSize(50).recordStats().build());
        return manager;
    }
}
