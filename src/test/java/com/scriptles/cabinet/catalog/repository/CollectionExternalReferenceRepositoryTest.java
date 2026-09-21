package com.scriptles.cabinet.catalog.repository;

import com.scriptles.cabinet.catalog.collection.CollectionSourceMode;
import com.scriptles.cabinet.catalog.collection.CollectionType;
import com.scriptles.cabinet.catalog.domain.CatalogEntityStatus;
import com.scriptles.cabinet.catalog.entity.Collection;
import com.scriptles.cabinet.catalog.entity.CollectionExternalReference;
import com.scriptles.cabinet.media.enums.ExternalSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class CollectionExternalReferenceRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CollectionExternalReferenceRepository repository;

    @Test
    void populatesAuditTimestampsWhenCreatingReference() {
        Collection collection = new Collection();
        collection.setSlug("tmdb-collection-17255");
        collection.setTitle("Collection");
        collection.setType(CollectionType.FILM_SERIES);
        collection.setSourceMode(CollectionSourceMode.EXTERNAL);
        collection.setStatus(CatalogEntityStatus.ACTIVE);
        entityManager.persist(collection);

        CollectionExternalReference reference = new CollectionExternalReference();
        reference.setCollection(collection);
        reference.setProvider(ExternalSource.TMDB);
        reference.setExternalId("17255");
        entityManager.persist(reference);
        entityManager.flush();

        assertThat(reference.getCreatedAt()).isNotNull();
        assertThat(reference.getUpdatedAt()).isNotNull();
    }
}
