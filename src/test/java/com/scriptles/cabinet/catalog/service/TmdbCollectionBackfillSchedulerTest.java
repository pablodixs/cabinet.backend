package com.scriptles.cabinet.catalog.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TmdbCollectionBackfillSchedulerTest {
    @Test
    void queuesOnlyAvailableCapacity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CatalogJobOrchestrationService orchestration = mock(CatalogJobOrchestrationService.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(98L);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(2)))
                .thenReturn(List.of("123", "456"));

        new TmdbCollectionBackfillScheduler(jdbc, orchestration, true, 10, 100, "pt-BR").dispatch();

        verify(orchestration).backfillCollection("123", "pt-BR");
        verify(orchestration).backfillCollection("456", "pt-BR");
    }

    @Test
    void pausesWhenQueueIsFull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CatalogJobOrchestrationService orchestration = mock(CatalogJobOrchestrationService.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(100L);

        new TmdbCollectionBackfillScheduler(jdbc, orchestration, true, 10, 100, "pt-BR").dispatch();

        verify(jdbc, never()).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt());
        verifyNoInteractions(orchestration);
    }

    @Test
    void disabledBackfillDoesNotQueryDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CatalogJobOrchestrationService orchestration = mock(CatalogJobOrchestrationService.class);

        new TmdbCollectionBackfillScheduler(jdbc, orchestration, false, 10, 100, "pt-BR").dispatch();

        verifyNoInteractions(jdbc, orchestration);
    }
}
