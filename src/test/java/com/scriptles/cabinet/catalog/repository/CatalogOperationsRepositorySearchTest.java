package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.entity.CatalogJob;
import com.scriptles.cabinet.catalog.entity.CatalogOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class CatalogOperationsRepositorySearchTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CatalogOperationRepository operationRepository;

    @Autowired
    private CatalogJobRepository jobRepository;

    @Test
    void searchesOperationsAndJobsWithPostgresCompatibleSqlShape() {
        UUID operationId = UUID.randomUUID();
        CatalogOperation operation = new CatalogOperation();
        operation.setId(operationId);
        operation.setType("COLLECTION_IMPORT");
        operation.setProvider("TMDB");
        operation.setTrigger("MANUAL");
        operation.setStatus("QUEUED");
        operation.setRootExternalId("131292");
        operation.setCreatedAt(Instant.now());
        entityManager.persist(operation);

        CatalogJob job = new CatalogJob();
        job.setId(UUID.randomUUID());
        job.setOperationId(operationId);
        job.setJobType("COLLECTION_SNAPSHOT");
        job.setProvider("TMDB");
        job.setEntityType("COLLECTION");
        job.setExternalId("131292");
        job.setPriority(10);
        job.setTrigger("MANUAL");
        job.setStatus("PENDING");
        job.setAttempts(0);
        job.setAvailableAt(Instant.now());
        job.setCreatedAt(Instant.now());
        entityManager.persist(job);
        entityManager.flush();
        entityManager.clear();

        assertThat(operationRepository.search(null, null, null, null, null, null, PageRequest.of(0, 25)))
                .extracting(CatalogOperation::getId)
                .containsExactly(operationId);
        assertThat(operationRepository.search(null, null, null, null, "131292", null, PageRequest.of(0, 25)))
                .extracting(CatalogOperation::getId)
                .containsExactly(operationId);
        assertThat(jobRepository.search(null, null, null, null, operationId, PageRequest.of(0, 25)))
                .extracting(CatalogJob::getId)
                .containsExactly(job.getId());
    }
}
