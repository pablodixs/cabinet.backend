package com.scriptles.cabinet.user.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.UUID;

@Component
public class InterestProfileCache {
    private final Cache<UUID, InterestGraphService.InterestProfile> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .build();

    public InterestGraphService.InterestProfile get(UUID userId) {
        return cache.getIfPresent(userId);
    }

    public void put(UUID userId, InterestGraphService.InterestProfile profile) {
        cache.put(userId, profile);
    }

    public void invalidate(UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.invalidate(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.invalidate(userId);
            }
        });
    }
}
