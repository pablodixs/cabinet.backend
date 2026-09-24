package com.scriptles.cabinet.user.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterestProfileCacheTest {
    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void invalidatesProfileAfterCommit() {
        InterestProfileCache cache = new InterestProfileCache();
        UUID userId = UUID.randomUUID();
        InterestGraphService.InterestProfile profile =
                new InterestGraphService.InterestProfile(Map.of(), Set.of());
        cache.put(userId, profile);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        cache.invalidate(userId);

        assertThat(cache.get(userId)).isSameAs(profile);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.get(userId)).isNull();
    }
}
