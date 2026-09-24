package com.scriptles.cabinet.common.outbox;

public enum DomainOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    COMPLETED,
    DEAD
}
