package com.scriptles.cabinet.status;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CaffeineCacheMetrics {
    private final CacheManager cacheManager;
    private final MeterRegistry meters;

    public CaffeineCacheMetrics(CacheManager cacheManager, MeterRegistry meters) {
        this.cacheManager = cacheManager;
        this.meters = meters;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bind() {
        cacheManager.getCacheNames().forEach(name -> {
            org.springframework.cache.Cache springCache = cacheManager.getCache(name);
            if (!(springCache instanceof CaffeineCache caffeineCache)) return;
            Cache<?, ?> cache = caffeineCache.getNativeCache();
            Gauge.builder("cabinet.cache.hit", cache, value -> value.stats().hitCount())
                    .tag("cache", name).register(meters);
            Gauge.builder("cabinet.cache.miss", cache, value -> value.stats().missCount())
                    .tag("cache", name).register(meters);
            Gauge.builder("cabinet.cache.eviction", cache, value -> value.stats().evictionCount())
                    .tag("cache", name).register(meters);
            Gauge.builder("cabinet.cache.size", cache, Cache::estimatedSize)
                    .tag("cache", name).register(meters);
        });
    }
}
