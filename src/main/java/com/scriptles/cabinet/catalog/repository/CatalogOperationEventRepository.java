package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CatalogOperationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogOperationEventRepository extends JpaRepository<CatalogOperationEvent, UUID> {
    List<CatalogOperationEvent> findByOperationIdOrderByOccurredAtAsc(UUID operationId);
}
