package com.scriptles.cabinet.status;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class BackgroundJobRetention {
    private final BackgroundJobRunRepository repository;

    @Transactional
    public int deleteExpired() {
        return repository.deleteExpired(Instant.now().minus(30, ChronoUnit.DAYS));
    }
}
